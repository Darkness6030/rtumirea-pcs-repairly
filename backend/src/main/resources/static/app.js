"use strict";
// Tokens live only in memory; refresh is an HttpOnly SameSite cookie.
const $ = (s) => document.querySelector(s),
    app = $("#app"),
    modal = $("#modal");
const state = {
    token: null,
    user: null,
    view: "repairs",
    page: 0,
    size: 25,
    q: "",
    sort: "created,desc",
    selected: new Set(),
    clients: [],
    technicians: [],
    refresh: null,
};
const statuses = {
    NEW: "Новая",
    DIAGNOSING: "Диагностика",
    WAITING_PARTS: "Ожидание запчастей",
    IN_REPAIR: "В ремонте",
    COMPLETED: "Завершена",
    CANCELLED: "Отменена",
};
const priorities = {
    LOW: "Низкий",
    MEDIUM: "Средний",
    HIGH: "Высокий",
    CRITICAL: "Критический",
    NOT_APPLICABLE: "Не применяется",
};
const roles = {
    VIEWER: "Наблюдатель",
    EDITOR: "Оператор",
    ADMIN: "Администратор",
};
const fieldTitles = {
    externalId: "Номер заявки",
    applianceType: "Вид техники",
    brand: "Бренд",
    model: "Модель",
    problem: "Неисправность",
    clientId: "Клиент",
    technicianId: "Мастер",
    status: "Статус",
    cost: "Стоимость",
    tags: "Теги",
    email: "Email",
    displayName: "Имя",
    name: "Имя",
    phone: "Телефон",
    address: "Адрес",
    specialization: "Специализация",
    code: "Код",
    body: "Текст заметки",
    roles: "Роли",
    password: "Пароль",
    temporaryPassword: "Временный пароль",
    newPassword: "Новый пароль",
    currentPassword: "Текущий пароль",
    version: "Версия записи",
};
const transitions = {
    NEW: ["NEW", "DIAGNOSING", "CANCELLED"],
    DIAGNOSING: ["DIAGNOSING", "WAITING_PARTS", "IN_REPAIR", "CANCELLED"],
    WAITING_PARTS: ["WAITING_PARTS", "IN_REPAIR", "CANCELLED"],
    IN_REPAIR: ["IN_REPAIR", "WAITING_PARTS", "COMPLETED", "CANCELLED"],
};
const money = (v) =>
    new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
    }).format(v ?? 0);
const date = (v) => (v ? new Date(v).toLocaleString("ru-RU") : "—");
const admin = () => state.user?.roles.includes("ADMIN"),
    editor = () => admin() || state.user?.roles.includes("EDITOR");
function el(tag, attrs = {}, ...children) {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (k.startsWith("on")) n.addEventListener(k.slice(2), v);
        else if (k === "class") n.className = v;
        else if (k === "text") n.textContent = v;
        else if (v !== undefined && v !== null) {
            if (k in n) n[k] = v;
            else n.setAttribute(k, v);
        }
    }
    for (const c of children.flat())
        if (c !== null && c !== undefined)
            n.append(
                c instanceof Node ? c : document.createTextNode(String(c)),
            );
    return n;
}
const button = (text, action, cls = "") =>
    el(
        "button",
        { type: "button", class: cls, onclick: () => run(action) },
        text,
    );
function notice(text, error = false) {
    const n = $("#notice");
    n.textContent = text;
    n.className = error ? "error" : "";
    n.hidden = false;
    clearTimeout(notice.timer);
    notice.timer = setTimeout(() => (n.hidden = true), error ? 12000 : 5000);
}
async function run(fn) {
    try {
        return await fn();
    } catch (e) {
        notice(e.message, true);
    }
}
async function refresh() {
    if (!state.refresh)
        state.refresh = (async () => {
            const r = await fetch("/api/auth/refresh", {
                method: "POST",
                credentials: "same-origin",
            });
            if (!r.ok) throw Error("Сессия завершена. Войдите снова.");
            const d = await r.json();
            state.token = d.accessToken;
            state.user = d.user;
        })().finally(() => (state.refresh = null));
    return state.refresh;
}
async function api(path, options = {}, retry = true) {
    const headers = new Headers(options.headers);
    if (state.token) headers.set("Authorization", "Bearer " + state.token);
    if (options.body && !(options.body instanceof FormData))
        headers.set("Content-Type", "application/json");
    const r = await fetch("/api" + path, {
        ...options,
        headers,
        credentials: "same-origin",
    });
    if (
        r.status === 401 &&
        retry &&
        state.token &&
        !path.startsWith("/auth/")
    ) {
        try {
            await refresh();
            return api(path, options, false);
        } catch (e) {
            state.token = null;
            state.user = null;
            modal.close();
            login();
            throw e;
        }
    }
    if (!r.ok) {
        let d;
        try {
            d = await r.json();
        } catch {
            d = { message: "Ошибка запроса: " + r.status };
        }
        const fields = Object.entries(d.validationErrors || {})
            .map(([k, v]) => (fieldTitles[k] || k) + ": " + v)
            .join("\n");
        throw Error(
            [
                d.message,
                fields,
                d.correlationId ? "Код обращения: " + d.correlationId : "",
            ]
                .filter(Boolean)
                .join("\n"),
        );
    }
    if (options.raw) return r;
    if (r.status === 204) return null;
    return r.json();
}
const send = (path, method, body) =>
    api(path, { method, body: JSON.stringify(body) });
function input(
    label,
    name,
    value = "",
    type = "text",
    required = true,
    maxLength,
) {
    const n = el("input", { name, value: value ?? "", type, required });
    if (maxLength) n.maxLength = maxLength;
    return el("label", {}, label, n);
}
function select(label, name, options, value) {
    const n = el(
        "select",
        { name },
        options.map(([id, title]) => el("option", { value: id }, title)),
    );
    if (value !== undefined && value !== null) n.value = value;
    return el("label", {}, label, n);
}
function check(label, name, value = false) {
    return el(
        "label",
        { class: "check" },
        el("input", { type: "checkbox", name, checked: value }),
        label,
    );
}
function formValue(form, name) {
    return form.elements.namedItem(name)?.value ?? "";
}
function dialog(title, content) {
    modal.replaceChildren(
        el(
            "div",
            { class: "dialog-head" },
            el("h2", { id: "modal-title" }, title),
            button("Закрыть", () => modal.close()),
        ),
        content,
    );
    if (!modal.open) modal.showModal();
}
function formDialog(title, fields, onSave, saveLabel = "Сохранить") {
    const err = el("div", { class: "error", role: "alert" }),
        save = el("button", { type: "submit", class: "primary" }, saveLabel);
    const form = el(
        "form",
        {},
        el("div", { class: "form-grid" }, fields),
        err,
        el(
            "div",
            { class: "form-footer" },
            button("Отмена", () => modal.close()),
            save,
        ),
    );
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        save.disabled = true;
        err.textContent = "";
        try {
            await onSave(form);
            modal.close();
            notice("Сохранено");
            await render();
        } catch (x) {
            err.textContent = x.message;
        } finally {
            save.disabled = false;
        }
    });
    dialog(title, form);
    return form;
}
function login() {
    state.view = "repairs";
    state.page = 0;
    state.selected.clear();
    state.q = "";
    state.freeText = "";
    state.filters = {};
    app.replaceChildren(
        el(
            "div",
            { class: "login" },
            el(
                "div",
                { class: "login-card" },
                el("div", { class: "brand" }, "repairly"),
                el("h1", {}, "С возвращением"),
                el(
                    "p",
                    { class: "muted" },
                    "Управление заявками сервисного центра",
                ),
                loginForm(),
            ),
        ),
    );
}
function loginForm() {
    const err = el("div", { class: "error", role: "alert" }),
        submit = el("button", { type: "submit", class: "primary" }, "Войти");
    const f = el(
        "form",
        {},
        input("Email", "email", "", "email"),
        input("Пароль", "password", "", "password"),
        err,
        submit,
    );
    f.addEventListener("submit", async (e) => {
        e.preventDefault();
        submit.disabled = true;
        try {
            const d = await send("/auth/login", "POST", {
                email: formValue(f, "email"),
                password: formValue(f, "password"),
                clientType: "WEB",
            });
            state.token = d.accessToken;
            state.user = d.user;
            f.reset();
            await render();
        } catch (x) {
            err.textContent = x.message;
        } finally {
            submit.disabled = false;
        }
    });
    return f;
}
function password(initial = false) {
    const fields = initial
        ? []
        : [input("Текущий пароль", "currentPassword", "", "password")];
    fields.push(
        input("Новый пароль", "newPassword", "", "password"),
        input("Повторите пароль", "repeat", "", "password"),
    );
    const f = formDialog(
        initial ? "Замените временный пароль" : "Смена пароля",
        fields,
        async (form) => {
            const next = formValue(form, "newPassword");
            if (next !== formValue(form, "repeat"))
                throw Error("Пароли не совпадают");
            await send(
                initial
                    ? "/auth/change-initial-password"
                    : "/auth/change-password",
                "POST",
                initial
                    ? { newPassword: next }
                    : {
                          currentPassword: formValue(form, "currentPassword"),
                          newPassword: next,
                      },
            );
            if (initial) {
                await refresh();
            } else {
                await api("/auth/logout", { method: "POST" });
                state.token = null;
                state.user = null;
            }
        },
    );
    f.prepend(
        el(
            "p",
            { class: "muted" },
            "От 12 символов, заглавные и строчные буквы, цифра и специальный символ. Не более 72 байт UTF-8.",
        ),
    );
}
function shell() {
    const nav = [
        ["repairs", "Заявки"],
        ["clients", "Клиенты"],
        ["technicians", "Мастера"],
        ["analytics", "Аналитика"],
    ];
    if (admin())
        nav.push(["users", "Пользователи"], ["audit", "Журнал аудита"]);
    const sidebar = el(
        "aside",
        { class: "sidebar" },
        el("div", { class: "brand" }, "repairly"),
        el("div", { class: "eyebrow" }, "Сервис ремонта"),
        nav.map(([key, title]) =>
            button(
                title,
                async () => {
                    state.view = key;
                    state.page = 0;
                    state.selected.clear();
                    await render();
                },
                "nav" + (state.view === key ? " active" : ""),
            ),
        ),
    );
    sidebar.append(
        el(
            "div",
            { class: "side-bottom" },
            el("div", { class: "user" }, state.user.displayName),
            el("small", {}, state.user.roles.map((r) => roles[r]).join(", ")),
            admin()
                ? el(
                      "a",
                      {
                          href: "/swagger-ui.html",
                          target: "_blank",
                          rel: "noopener",
                      },
                      "Swagger API",
                  )
                : null,
            button("Сменить пароль", () => password()),
            button("Выйти", async () => {
                await api("/auth/logout", { method: "POST" });
                state.token = null;
                state.user = null;
                login();
            }),
        ),
    );
    const main = el("main");
    app.replaceChildren(el("div", { class: "shell" }, sidebar, main));
    return main;
}
let renderSequence = 0;
async function render() {
    if (!state.user) return login();
    const main = shell();
    if (state.user.passwordChangeRequired) {
        main.append(
            el("h1", {}, "Первый вход"),
            el(
                "p",
                { class: "muted" },
                "Для доступа к системе замените временный пароль.",
            ),
            button("Заменить пароль", () => password(true), "primary"),
        );
        password(true);
        return;
    }
    const sequence = ++renderSequence;
    main.append(el("p", { class: "muted" }, "Загружаем данные…"));
    try {
        const content =
            state.view === "repairs"
                ? await repairsPage()
                : state.view === "analytics"
                  ? await analyticsPage()
                  : state.view === "users"
                    ? await usersPage()
                    : state.view === "audit"
                      ? await auditPage()
                      : await directoryPage();
        if (sequence === renderSequence) main.replaceChildren(content);
    } catch (e) {
        if (sequence === renderSequence)
            main.replaceChildren(
                el("p", { class: "error" }, e.message),
                button("Повторить", render),
            );
    }
}
function heading(title, subtitle, actions = []) {
    return el(
        "div",
        { class: "heading" },
        el(
            "div",
            {},
            el("h1", {}, title),
            el("p", { class: "muted" }, subtitle),
        ),
        el("div", { class: "actions" }, actions),
    );
}
function table(headers, rows) {
    return el(
        "div",
        { class: "table-wrap" },
        el(
            "table",
            {},
            el(
                "thead",
                {},
                el(
                    "tr",
                    {},
                    headers.map((h) => el("th", {}, h)),
                ),
            ),
            el(
                "tbody",
                {},
                rows.length
                    ? rows
                    : [
                          el(
                              "tr",
                              {},
                              el(
                                  "td",
                                  { colSpan: headers.length, class: "empty" },
                                  "Записей не найдено",
                              ),
                          ),
                      ],
            ),
        ),
    );
}
function pager(data) {
    return el(
        "div",
        { class: "pager" },
        el(
            "span",
            { class: "muted" },
            "Всего: " +
                data.totalElements +
                " · Страница " +
                (data.page + 1) +
                " из " +
                Math.max(1, data.totalPages),
        ),
        el(
            "div",
            { class: "actions" },
            el(
                "select",
                {
                    value: state.size,
                    onchange: (e) =>
                        run(async () => {
                            state.size = Number(e.target.value);
                            state.page = 0;
                            await render();
                        }),
                },
                [10, 25, 50, 100].map((n) =>
                    el(
                        "option",
                        { value: n, selected: n === state.size },
                        n + " строк",
                    ),
                ),
            ),
            el(
                "button",
                {
                    disabled: data.first,
                    onclick: () =>
                        run(async () => {
                            state.page--;
                            state.selected.clear();
                            await render();
                        }),
                },
                "Назад",
            ),
            el(
                "button",
                {
                    disabled: data.last,
                    onclick: () =>
                        run(async () => {
                            state.page++;
                            state.selected.clear();
                            await render();
                        }),
                },
                "Далее",
            ),
        ),
    );
}
async function directories() {
    [state.clients, state.technicians] = await Promise.all([
        api("/clients"),
        api("/technicians"),
    ]);
}
async function repairsPage() {
    await directories();
    const data = await api(
        "/repairs?" +
            new URLSearchParams({
                q: state.q,
                page: state.page,
                size: state.size,
                sort: state.sort,
            }),
    );
    const box = el("div");
    box.append(
        heading(
            "Заявки",
            "Приём техники, ход ремонта и история обращений",
            editor()
                ? [
                      button("Импорт CSV", importDialog),
                      button("Экспорт CSV", exportCsv),
                      button("+ Новая заявка", () => repairForm(), "primary"),
                  ]
                : [],
        ),
    );
    const query = el("input", {
        type: "search",
        value: state.freeText || "",
        placeholder: "Поиск по технике, клиенту, неисправности или номеру",
        ariaLabel: "Поиск заявок",
        maxLength: 500,
    });
    const sort = el(
        "select",
        { ariaLabel: "Сортировка" },
        [
            ["created,desc", "Сначала новые"],
            ["created,asc", "Сначала старые"],
            ["cost,asc", "Стоимость ↑"],
            ["cost,desc", "Стоимость ↓"],
            ["status,asc", "Статус"],
            ["priority,asc", "Приоритет"],
            ["code,asc", "Номер"],
        ].map(([v, t]) =>
            el("option", { value: v, selected: v === state.sort }, t),
        ),
    );
    const filter = el(
        "form",
        { class: "panel" },
        el(
            "div",
            { class: "toolbar" },
            query,
            sort,
            el("button", { type: "submit", class: "primary" }, "Найти"),
            button("Сбросить", async () => {
                state.q = "";
                state.freeText = "";
                state.page = 0;
                state.filters = {};
                state.selected.clear();
                await render();
            }),
        ),
    );
    const f = state.filters || {};
    const filterFields = el(
        "div",
        { class: "filters" },
        select(
            "Статус",
            "status",
            [["", "Все статусы"], ...Object.entries(statuses)],
            f.status,
        ),
        select(
            "Мастер",
            "technician",
            [
                ["", "Все мастера"],
                ...state.technicians.map((t) => [t.code, t.name]),
            ],
            f.technician,
        ),
        select(
            "Срочность",
            "urgent",
            [
                ["", "Любая"],
                ["true", "Срочная"],
                ["false", "Обычная"],
            ],
            f.urgent,
        ),
        select(
            "Приоритет",
            "priority",
            [["", "Любой"], ...Object.entries(priorities)],
            f.priority,
        ),
        select(
            "Архив",
            "archived",
            [
                ["false", "Активные"],
                ["true", "Архивные"],
                ["all", "Все записи"],
            ],
            f.archived || "false",
        ),
        input("Тег", "tag", f.tag || "", "text", false, 50),
        select(
            "Стоимость",
            "operator",
            [
                ["", "Без ограничения"],
                [">=", "Не меньше"],
                ["<=", "Не больше"],
                ["=", "Равна"],
                [">", "Больше"],
                ["<", "Меньше"],
            ],
            f.operator,
        ),
        input("Сумма, ₽", "cost", f.cost || "", "number", false),
    );
    filterFields.querySelector("[name=cost]").step = "0.01";
    filterFields.querySelector("[name=cost]").min = "0";
    filter.append(filterFields);
    // Keep user text separate: changing a filter must not leave old tokens in the query.
    filter.addEventListener("submit", (e) => {
        e.preventDefault();
        run(async () => {
            const values = Object.fromEntries(new FormData(filter));
            let q = query.value.trim();
            for (const k of [
                "status",
                "technician",
                "urgent",
                "priority",
                "tag",
                "archived",
            ]) {
                if (values[k])
                    q +=
                        " " +
                        (k === "tag"
                            ? '"tag:' + values[k] + '"'
                            : k + ":" + values[k]);
            }
            if (values.operator) {
                if (values.cost === "")
                    throw Error("Введите сумму для фильтра стоимости");
                q += " cost" + values.operator + values.cost;
            }
            state.q = q.trim();
            state.freeText = query.value.trim();
            state.filters = values;
            state.sort = sort.value;
            state.page = 0;
            state.selected.clear();
            await render();
        });
    });
    box.append(filter);
    if (editor()) {
        const action = el(
            "select",
            { ariaLabel: "Массовая операция" },
            [
                ["ARCHIVE", "В архив"],
                ["RESTORE", "Восстановить"],
                ["SET_STATUS", "Изменить статус"],
                ["ADD_TAG", "Добавить тег"],
                ["REMOVE_TAG", "Удалить тег"],
            ].map(([v, t]) => el("option", { value: v }, t)),
        );
        const count = el(
            "span",
            { id: "selection-count", class: "muted" },
            "Выбрано: " + state.selected.size,
        );
        box.append(
            el(
                "div",
                { class: "toolbar" },
                count,
                action,
                button("Применить к выбранным", () => bulk(action.value)),
            ),
        );
    }
    const all = el("input", {
        type: "checkbox",
        ariaLabel: "Выбрать текущую страницу",
        onchange: (e) => {
            data.content.forEach((r) =>
                e.target.checked
                    ? state.selected.add(r.id)
                    : state.selected.delete(r.id),
            );
            box.querySelectorAll("input[data-row]").forEach(
                (n) => (n.checked = e.target.checked),
            );
            selectionCount();
        },
    });
    box.append(
        table(
            [
                editor() ? all : "№",
                "Заявка",
                "Клиент",
                "Статус",
                "Приоритет",
                "Стоимость",
                "Принята",
                "",
            ],
            data.content.map((r) =>
                el(
                    "tr",
                    {},
                    el(
                        "td",
                        {},
                        editor()
                            ? el("input", {
                                  type: "checkbox",
                                  "data-row": r.id,
                                  ariaLabel: "Выбрать " + r.externalId,
                                  checked: state.selected.has(r.id),
                                  onchange: (e) => {
                                      e.target.checked
                                          ? state.selected.add(r.id)
                                          : state.selected.delete(r.id);
                                      selectionCount();
                                  },
                              })
                            : r.externalId,
                    ),
                    el(
                        "td",
                        {},
                        el("strong", {}, r.externalId),
                        el(
                            "span",
                            { class: "secondary" },
                            [r.applianceType, r.brand, r.model].join(" "),
                        ),
                    ),
                    el(
                        "td",
                        {},
                        r.clientName,
                        el(
                            "span",
                            { class: "secondary" },
                            r.technicianName || "Мастер не назначен",
                        ),
                    ),
                    el(
                        "td",
                        {},
                        el(
                            "span",
                            { class: "badge " + r.status },
                            r.archived ? "В архиве" : statuses[r.status],
                        ),
                    ),
                    el(
                        "td",
                        {},
                        el(
                            "span",
                            { class: "badge " + r.priorityLevel },
                            priorities[r.priorityLevel],
                        ),
                    ),
                    el("td", {}, money(r.cost)),
                    el("td", {}, date(r.createdAt)),
                    el(
                        "td",
                        {},
                        button("Открыть", () => repairCard(r.id)),
                    ),
                ),
            ),
        ),
    );
    box.append(pager(data));
    return box;
}
function selectionCount() {
    if ($("#selection-count"))
        $("#selection-count").textContent = "Выбрано: " + state.selected.size;
}
async function repairForm(record) {
    await directories();
    const r = record || {};
    const fields = [
        input(
            "Номер заявки",
            "externalId",
            r.externalId || "",
            "text",
            true,
            100,
        ),
        select(
            "Клиент",
            "clientId",
            [
                ["", "Выберите клиента"],
                ...state.clients
                    .filter((c) => c.active || c.id === r.clientId)
                    .map((c) => [c.id, c.name + " · " + c.phone]),
            ],
            r.clientId,
        ),
        input(
            "Вид техники",
            "applianceType",
            r.applianceType || "",
            "text",
            true,
            100,
        ),
        input("Бренд", "brand", r.brand || "", "text", true, 100),
        input("Модель", "model", r.model || "", "text", true, 100),
        input("Стоимость, ₽", "cost", r.cost ?? 0, "number"),
        select(
            "Мастер",
            "technicianId",
            [
                ["", "Не назначен"],
                ...state.technicians
                    .filter((t) => t.active || t.id === r.technicianId)
                    .map((t) => [t.id, t.name + " · " + t.specialization]),
            ],
            r.technicianId,
        ),
        select(
            "Статус",
            "status",
            (record ? transitions[r.status] || [r.status] : ["NEW"]).map(
                (s) => [s, statuses[s]],
            ),
            r.status || "NEW",
        ),
        el(
            "label",
            { class: "full" },
            "Неисправность",
            el("textarea", {
                name: "problem",
                required: true,
                maxLength: 2000,
                value: r.problem || "",
            }),
        ),
        input(
            "Теги через запятую",
            "tags",
            (r.tags || []).join(", "),
            "text",
            false,
        ),
        check("Срочный ремонт", "urgent", r.urgent),
    ];
    const f = formDialog(
        record ? "Редактирование " + r.externalId : "Новая заявка",
        fields,
        async (form) => {
            const values = Object.fromEntries(new FormData(form));
            const payload = {
                ...values,
                cost: values.cost,
                urgent: form.elements.urgent.checked,
                clientId: r.clientId || values.clientId,
                technicianId: values.technicianId || null,
                tags: values.tags
                    .split(",")
                    .map((t) => t.trim())
                    .filter(Boolean),
                version: r.version ?? null,
            };
            await send(
                "/repairs" + (record ? "/" + r.id : ""),
                record ? "PUT" : "POST",
                payload,
            );
        },
    );
    f.elements.clientId.required = true;
    if (record) f.elements.clientId.disabled = true;
    f.elements.cost.min = "0";
    f.elements.cost.max = "9999999999.99";
    f.elements.cost.step = "0.01";
}
async function repairCard(id) {
    const r = await api("/repairs/" + id),
        notes = await api("/repairs/" + id + "/notes");
    const body = el("div");
    const facts = [
        ["Техника", [r.applianceType, r.brand, r.model].join(" ")],
        ["Клиент", r.clientName + " · " + r.clientPhone],
        ["Мастер", r.technicianName || "Не назначен"],
        ["Статус", statuses[r.status] + (r.archived ? " · Архив" : "")],
        ["Стоимость", money(r.cost)],
        ["Приоритет", priorities[r.priorityLevel]],
        ["Принята", date(r.createdAt)],
        ["Завершена", date(r.completedAt)],
        ["Неисправность", r.problem],
        ["Теги", r.tags.join(", ") || "—"],
    ];
    body.append(
        el(
            "dl",
            { class: "details" },
            facts.map(([label, value]) =>
                el("div", {}, el("dt", {}, label), el("dd", {}, value)),
            ),
        ),
    );
    if (editor()) {
        const actions = el("div", { class: "actions" });
        if (!r.archived && !["COMPLETED", "CANCELLED"].includes(r.status))
            actions.append(
                button("Редактировать", () => repairForm(r), "primary"),
            );
        actions.append(
            button(r.archived ? "Восстановить" : "В архив", async () => {
                if (
                    !confirm(
                        r.archived
                            ? "Восстановить заявку?"
                            : "Переместить заявку в архив?",
                    )
                )
                    return;
                await api("/repairs/" + id + (r.archived ? "/restore" : ""), {
                    method: r.archived ? "POST" : "DELETE",
                });
                modal.close();
                await render();
            }),
        );
        if (admin() && r.archived)
            actions.append(
                button(
                    "Удалить окончательно",
                    async () => {
                        if (
                            !confirm(
                                "Удалить заявку и все её заметки навсегда?",
                            )
                        )
                            return;
                        await api("/repairs/" + id + "/permanent", {
                            method: "DELETE",
                            headers: {
                                "X-Confirm-Delete": "PERMANENTLY_DELETE",
                            },
                        });
                        modal.close();
                        await render();
                    },
                    "danger",
                ),
            );
        body.append(actions);
    }
    const noteSection = el(
        "section",
        { class: "notes" },
        el("h3", {}, "Заметки · " + notes.length),
    );
    for (const n of notes) {
        const item = el(
            "article",
            { class: "note" },
            el("strong", {}, (n.pinned ? "Закреплено · " : "") + n.authorName),
            el("span", { class: "secondary muted" }, " · " + date(n.createdAt)),
            el("p", {}, n.body),
        );
        if (editor() && (admin() || n.authorId === state.user.id))
            item.append(
                el(
                    "div",
                    { class: "actions" },
                    button("Изменить", () => noteForm(id, n)),
                    button(
                        "Удалить",
                        async () => {
                            if (!confirm("Удалить заметку?")) return;
                            await api("/repairs/" + id + "/notes/" + n.id, {
                                method: "DELETE",
                            });
                            await repairCard(id);
                        },
                        "danger",
                    ),
                ),
            );
        noteSection.append(item);
    }
    if (editor())
        noteSection.append(button("+ Добавить заметку", () => noteForm(id)));
    body.append(noteSection);
    dialog(r.externalId, body);
}
function noteForm(id, n) {
    formDialog(
        n ? "Редактирование заметки" : "Новая заметка",
        [
            el(
                "label",
                { class: "full" },
                "Текст",
                el("textarea", {
                    name: "body",
                    value: n?.body || "",
                    required: true,
                    maxLength: 4000,
                }),
            ),
            check("Закрепить", "pinned", n?.pinned),
        ],
        async (f) => {
            await send(
                "/repairs/" + id + "/notes" + (n ? "/" + n.id : ""),
                n ? "PUT" : "POST",
                {
                    body: formValue(f, "body"),
                    pinned: f.elements.pinned.checked,
                    version: n?.version ?? null,
                },
            );
        },
    );
}
function bulk(action) {
    if (!state.selected.size) throw Error("Сначала выберите заявки");
    const fields = [];
    if (action === "SET_STATUS")
        fields.push(select("Статус", "status", Object.entries(statuses)));
    if (action.includes("TAG"))
        fields.push(input("Тег", "tag", "", "text", true, 50));
    fields.push(
        el(
            "p",
            { class: "full muted" },
            "Заявок выбрано: " + state.selected.size,
        ),
    );
    formDialog(
        "Массовая операция",
        fields,
        async (f) => {
            const result = await send("/repairs/bulk", "POST", {
                ids: [...state.selected],
                action,
                status: action === "SET_STATUS" ? formValue(f, "status") : null,
                tag: action.includes("TAG") ? formValue(f, "tag") : null,
            });
            state.selected.clear();
            setTimeout(
                () =>
                    dialog(
                        "Результат операции",
                        el(
                            "div",
                            {},
                            el(
                                "p",
                                {},
                                "Успешно: " +
                                    result.succeeded +
                                    " из " +
                                    result.requested,
                            ),
                            el(
                                "div",
                                { class: "result" },
                                result.failures.map((x) =>
                                    el("p", {}, x.id + ": " + x.message),
                                ),
                            ),
                        ),
                    ),
                50,
            );
        },
        "Выполнить",
    );
}
async function exportCsv() {
    const r = await api(
        "/repairs/export?" + new URLSearchParams({ q: state.q }),
        { raw: true },
    );
    const url = URL.createObjectURL(await r.blob());
    const a = el("a", {
        href: url,
        download: "repairly-" + new Date().toISOString().slice(0, 10) + ".csv",
    });
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function importDialog() {
    let checked = null;
    const file = el("input", {
            type: "file",
            accept: ".csv",
            ariaLabel: "CSV файл",
        }),
        mode = select("Режим импорта", "mode", [
            ["CREATE_ONLY", "Только новые"],
            ["UPSERT_BY_EXTERNAL_ID", "Обновлять по номеру заявки"],
        ]);
    const result = el("div", { class: "result", role: "status" }),
        apply = button(
            "Применить импорт",
            async () => {
                if (!checked) throw Error("Сначала проверьте файл");
                apply.disabled = true;
                try {
                    const d = await upload("/apply");
                    checked = null;
                    result.textContent =
                        "Создано: " +
                        d.created +
                        ", обновлено: " +
                        d.updated +
                        ", пропущено: " +
                        d.skipped +
                        "\n" +
                        d.issues
                            .map((x) => "Строка " + x.row + ": " + x.message)
                            .join("\n");
                    await render();
                } finally {
                    apply.disabled = !checked;
                }
            },
            "primary",
        );
    apply.disabled = true;
    const upload = async (suffix) => {
        if (!file.files[0]) throw Error("Выберите CSV-файл");
        const data = new FormData();
        data.append("file", file.files[0]);
        return api(
            "/repairs/import" +
                suffix +
                "?mode=" +
                mode.querySelector("select").value,
            { method: "POST", body: data },
        );
    };
    const reset = () => {
        checked = null;
        apply.disabled = true;
        result.textContent = "";
    };
    file.addEventListener("change", reset);
    mode.addEventListener("change", reset);
    dialog(
        "Импорт заявок",
        el(
            "div",
            { class: "upload" },
            el(
                "p",
                { class: "muted" },
                "CSV в UTF-8. Колонки: externalId, applianceType, brand, model, problem, client, technician, status, cost, urgent, tags. Клиент и мастер указываются кодами справочников; теги разделяются |. Новые заявки создаются со статусом NEW.",
            ),
            file,
            mode,
            el(
                "div",
                { class: "actions" },
                button("Проверить файл", async () => {
                    reset();
                    const d = await upload("/preview");
                    checked = true;
                    apply.disabled = d.validRows === 0;
                    result.textContent =
                        "Всего: " +
                        d.totalRows +
                        ", корректных: " +
                        d.validRows +
                        ", ошибочных: " +
                        d.invalidRows +
                        "\n" +
                        d.issues
                            .map((x) => "Строка " + x.row + ": " + x.message)
                            .join("\n");
                }),
                apply,
            ),
            result,
        ),
    );
}
async function directoryPage() {
    const kind = state.view;
    const rows = await api("/" + kind);
    const box = el("div");
    box.append(
        heading(
            kind === "clients" ? "Клиенты" : "Мастера",
            "Контакты и доступность для новых заявок",
            admin()
                ? [button("+ Добавить", () => personForm(kind), "primary")]
                : [],
        ),
    );
    const query = el("input", {
            type: "search",
            placeholder: "Поиск по имени, телефону или коду",
            ariaLabel: "Поиск по справочнику",
        }),
        holder = el("div");
    function fill() {
        const q = query.value.toLowerCase();
        holder.replaceChildren(
            table(
                [
                    "Код",
                    "Имя",
                    "Телефон",
                    kind === "clients" ? "Адрес" : "Специализация",
                    "Состояние",
                    "",
                ],
                rows
                    .filter((r) =>
                        [r.code, r.name, r.phone, r.address, r.specialization]
                            .join(" ")
                            .toLowerCase()
                            .includes(q),
                    )
                    .map((r) =>
                        el(
                            "tr",
                            {},
                            el("td", {}, r.code),
                            el("td", {}, r.name),
                            el("td", {}, r.phone),
                            el("td", {}, r.address || r.specialization),
                            el("td", {}, r.active ? "Активен" : "Отключён"),
                            el(
                                "td",
                                {},
                                admin()
                                    ? el(
                                          "div",
                                          { class: "actions" },
                                          button("Изменить", () =>
                                              personForm(kind, r),
                                          ),
                                          button(
                                              r.active
                                                  ? "Отключить"
                                                  : "Включить",
                                              async () => {
                                                  await api(
                                                      "/" +
                                                          kind +
                                                          "/" +
                                                          r.id +
                                                          "/active?" +
                                                          new URLSearchParams({
                                                              active: !r.active,
                                                              version:
                                                                  r.version,
                                                          }),
                                                      { method: "PATCH" },
                                                  );
                                                  await render();
                                              },
                                          ),
                                      )
                                    : null,
                            ),
                        ),
                    ),
            ),
        );
    }
    query.addEventListener("input", fill);
    fill();
    box.append(el("div", { class: "toolbar" }, query), holder);
    return box;
}
function personForm(kind, r = {}) {
    const client = kind === "clients";
    formDialog(
        (r.id ? "Изменить: " : "Добавить: ") + (client ? "клиент" : "мастер"),
        [
            input(
                "Код (латиница, цифры, - и _)",
                "code",
                r.code || "",
                "text",
                true,
                50,
            ),
            input("Имя", "name", r.name || "", "text", true, 150),
            input(
                "Телефон (10–15 цифр, возможен +)",
                "phone",
                r.phone || "",
                "tel",
                true,
                16,
            ),
            input(
                client ? "Адрес" : "Специализация",
                client ? "address" : "specialization",
                r.address || r.specialization || "",
                "text",
                true,
                client ? 300 : 150,
            ),
            check("Активен", "active", r.active ?? true),
        ],
        async (f) => {
            await send(
                "/" + kind + (r.id ? "/" + r.id : ""),
                r.id ? "PUT" : "POST",
                {
                    ...Object.fromEntries(new FormData(f)),
                    active: f.elements.active.checked,
                    version: r.version ?? null,
                },
            );
        },
    );
}
async function analyticsPage() {
    const s = await api("/analytics/summary");
    return el(
        "div",
        {},
        heading("Аналитика", "Сводка по заявкам сервисного центра", [
            button("Обновить", render),
        ]),
        el(
            "div",
            { class: "cards" },
            [
                ["Все заявки", s.total],
                ["Активные", s.active],
                ["В архиве", s.archived],
                ["Срочные активные", s.urgent],
                ["Средняя стоимость", money(s.averageCost)],
            ].map(([k, v]) =>
                el(
                    "div",
                    { class: "metric" },
                    el("span", { class: "muted" }, k),
                    el("strong", {}, v),
                ),
            ),
        ),
        el(
            "div",
            { class: "charts" },
            [
                ["По мастерам", s.byTechnician, {}],
                ["По статусам", s.byStatus, statuses],
                ["По приоритету", s.byPriority, priorities],
            ].map(([title, rows, labels]) =>
                el(
                    "section",
                    { class: "panel" },
                    el("h2", {}, title),
                    rows.length
                        ? rows.map((r) =>
                              el(
                                  "div",
                                  { class: "bar" },
                                  el("span", {}, labels[r.name] || r.name),
                                  el("strong", {}, r.count),
                                  el("progress", {
                                      value: r.count,
                                      max: Math.max(
                                          1,
                                          ...rows.map((x) => x.count),
                                      ),
                                      ariaLabel: labels[r.name] || r.name,
                                  }),
                              ),
                          )
                        : el("p", { class: "muted" }, "Нет активных заявок"),
                ),
            ),
        ),
    );
}
async function usersPage() {
    const data = await api(
        "/admin/users?" +
            new URLSearchParams({
                page: state.page,
                size: state.size,
                sort: "email,asc",
            }),
    );
    return el(
        "div",
        {},
        heading("Пользователи", "Доступ сотрудников к системе", [
            button("+ Создать пользователя", () => userForm(), "primary"),
        ]),
        table(
            ["Имя", "Email", "Роли", "Состояние", ""],
            data.content.map((r) =>
                el(
                    "tr",
                    {},
                    el("td", {}, r.displayName),
                    el("td", {}, r.email),
                    el("td", {}, r.roles.map((x) => roles[x]).join(", ")),
                    el(
                        "td",
                        {},
                        r.enabled ? "Активен" : "Отключён",
                        r.passwordChangeRequired
                            ? el(
                                  "span",
                                  { class: "secondary" },
                                  "Временный пароль",
                              )
                            : null,
                    ),
                    el(
                        "td",
                        {},
                        el(
                            "div",
                            { class: "actions" },
                            button("Изменить", () => userForm(r)),
                            button("Сбросить пароль", () =>
                                formDialog(
                                    "Сброс пароля",
                                    [
                                        input(
                                            "Новый временный пароль",
                                            "temporaryPassword",
                                            "",
                                            "password",
                                        ),
                                    ],
                                    async (f) => {
                                        await send(
                                            "/admin/users/" +
                                                r.id +
                                                "/reset-password",
                                            "POST",
                                            {
                                                temporaryPassword: formValue(
                                                    f,
                                                    "temporaryPassword",
                                                ),
                                            },
                                        );
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
        pager(data),
    );
}
function userForm(r = {}) {
    const fields = [
        input("Имя", "displayName", r.displayName || "", "text", true, 160),
        input("Email", "email", r.email || "", "email", true, 254),
    ];
    if (!r.id)
        fields.push(
            input("Временный пароль", "temporaryPassword", "", "password"),
        );
    fields.push(
        select(
            "Уровень доступа",
            "role",
            Object.entries(roles),
            r.roles?.includes("ADMIN")
                ? "ADMIN"
                : r.roles?.includes("EDITOR")
                  ? "EDITOR"
                  : "VIEWER",
        ),
    );
    if (r.id) fields.push(check("Активен", "enabled", r.enabled));
    formDialog(
        r.id ? "Изменить пользователя" : "Новый пользователь",
        fields,
        async (f) => {
            const values = Object.fromEntries(new FormData(f)),
                role = values.role;
            delete values.role;
            if (r.id) {
                values.enabled = f.elements.enabled.checked;
                values.version = r.version;
            }
            values.roles = [role];
            await send(
                "/admin/users" + (r.id ? "/" + r.id : ""),
                r.id ? "PUT" : "POST",
                values,
            );
        },
    );
}
async function auditPage() {
    const data = await api(
        "/admin/audit?" +
            new URLSearchParams({
                q: state.auditQuery || "",
                page: state.page,
                size: state.size,
                sort: state.auditSort || "occurred,desc",
            }),
    );
    const q = el("input", {
            type: "search",
            value: state.auditQuery || "",
            placeholder: "Событие, пользователь или код обращения",
            ariaLabel: "Поиск аудита",
        }),
        sort = select(
            "Сортировка",
            "sort",
            [
                ["occurred,desc", "Сначала новые"],
                ["occurred,asc", "Сначала старые"],
                ["actor,asc", "По пользователю"],
                ["event,asc", "По событию"],
            ],
            state.auditSort,
        );
    const form = el(
        "form",
        { class: "toolbar" },
        q,
        sort,
        el("button", { type: "submit" }, "Найти"),
    );
    form.addEventListener("submit", (e) => {
        e.preventDefault();
        state.auditQuery = q.value;
        state.auditSort = formValue(form, "sort");
        state.page = 0;
        run(render);
    });
    return el(
        "div",
        {},
        heading("Журнал аудита", "История входов и изменений"),
        form,
        table(
            ["Время", "Пользователь", "Событие", "Описание", "Код обращения"],
            data.content.map((r) =>
                el(
                    "tr",
                    {},
                    el("td", {}, date(r.occurredAt)),
                    el("td", {}, r.actorEmail || "Система"),
                    el("td", {}, r.eventType),
                    el("td", {}, r.summary),
                    el("td", {}, r.correlationId),
                ),
            ),
        ),
        pager(data),
    );
}
(async () => {
    try {
        await refresh();
        await render();
    } catch {
        login();
    }
})();
