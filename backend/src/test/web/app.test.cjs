const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const path = require("node:path");
const { JSDOM } = require("jsdom");

const source = readFileSync(
    path.join(__dirname, "../../main/resources/static/app.js"),
    "utf8",
);
const client = {
    id: "c1",
    code: "C-1",
    name: "Анна",
    phone: "+70000000001",
    address: "Адрес",
    active: true,
    version: 0,
};
const technician = {
    id: "t1",
    code: "T-1",
    name: "Мастер",
    phone: "+70000000011",
    specialization: "Техника",
    active: true,
    version: 0,
};
const repair = {
    id: "r1",
    externalId: "R-1",
    applianceType: "Холодильник",
    brand: "Atlant",
    model: "XM",
    problem: "<img src=x onerror=alert(1)>",
    clientId: "c1",
    clientName: "Анна",
    clientPhone: client.phone,
    technicianId: "t1",
    technicianName: "Мастер",
    status: "NEW",
    cost: 1000,
    urgent: true,
    priorityLevel: "HIGH",
    archived: false,
    tags: ["гарантия"],
    version: 3,
    createdAt: "2026-01-01T10:00:00Z",
};
const page = (content) => ({
    content,
    totalElements: content.length,
    totalPages: 1,
    page: 0,
    first: true,
    last: true,
});

async function waitFor(predicate) {
    for (let attempt = 0; attempt < 100; attempt++) {
        if (predicate()) return;
        await new Promise((resolve) => setTimeout(resolve, 5));
    }
    assert.fail("UI did not reach the expected state");
}

async function setup(t, role = "ADMIN") {
    const dom = new JSDOM(
        '<div id="app"></div><div id="notice" hidden></div><dialog id="modal"></dialog>',
        {
            url: "http://localhost/",
            runScripts: "outside-only",
        },
    );
    t.after(() => dom.window.close());
    const w = dom.window;
    w.Headers = Headers;
    w.confirm = () => true;
    w.HTMLDialogElement.prototype.showModal = function () {
        this.open = true;
    };
    w.HTMLDialogElement.prototype.close = function () {
        this.open = false;
    };
    const user = {
        id: "u1",
        email: "test@example.org",
        displayName: "Тест",
        roles: [role],
        enabled: true,
        version: 0,
    };
    const requests = [];
    let writeError = null;
    w.fetch = async (url, options = {}) => {
        requests.push({ url, options });
        const pathname = new URL(url, w.location).pathname;
        let data = {};
        if (pathname === "/api/auth/refresh")
            data = { accessToken: "test-access", user };
        else if (pathname === "/api/clients") data = [client];
        else if (pathname === "/api/technicians") data = [technician];
        else if (pathname === "/api/analytics/summary")
            data = {
                total: 1,
                active: 1,
                archived: 0,
                urgent: 1,
                averageCost: 1000,
                byTechnician: [{ name: "Мастер", count: 1 }],
                byStatus: [{ name: "NEW", count: 1 }],
                byPriority: [{ name: "HIGH", count: 1 }],
            };
        else if (pathname === "/api/admin/users") data = page([user]);
        else if (pathname === "/api/admin/audit")
            data = page([
                {
                    occurredAt: repair.createdAt,
                    actorEmail: user.email,
                    eventType: "LOGIN_SUCCESS",
                    summary: "Вход",
                    correlationId: "test",
                },
            ]);
        else if (pathname.endsWith("/notes")) data = [];
        else if (pathname === "/api/repairs/import/preview")
            data = {
                totalRows: 2,
                validRows: 1,
                invalidRows: 1,
                issues: [{ row: 3, message: "Неверная стоимость" }],
            };
        else if (pathname === "/api/repairs/import/apply")
            data = { created: 1, updated: 0, skipped: 1, issues: [] };
        else if (pathname === "/api/repairs/r1") data = repair;
        else if (pathname === "/api/repairs") data = page([repair]);
        else throw Error("Unexpected endpoint: " + pathname);
        const failed =
            writeError &&
            ["POST", "PUT"].includes(options.method) &&
            !pathname.startsWith("/api/auth");
        return {
            ok: !failed,
            status: failed ? 409 : 200,
            json: async () => (failed ? { message: writeError } : data),
        };
    };
    w.eval(source);
    await waitFor(() => w.document.querySelector("table"));
    const button = (label) =>
        [...w.document.querySelectorAll("button")].find(
            (n) => n.textContent === label,
        );
    const click = (label) => {
        const found = button(label);
        assert.ok(found, "Missing button: " + label);
        found.click();
    };
    return {
        w,
        requests,
        click,
        button,
        failWrites: (message) => {
            writeError = message;
        },
    };
}

test("All six administrator pages render from API DTOs", async (t) => {
    const { w, click } = await setup(t);
    for (const label of [
        "Клиенты",
        "Мастера",
        "Аналитика",
        "Пользователи",
        "Журнал аудита",
        "Заявки",
    ]) {
        click(label);
        await waitFor(
            () => w.document.querySelector("main h1")?.textContent === label,
        );
        assert.equal(w.document.querySelector("main .error"), null);
    }
});

test("Viewer sees records but no write or administration buttons", async (t) => {
    const { button } = await setup(t, "VIEWER");
    assert.ok(button("Открыть"));
    for (const name of [
        "+ Новая заявка",
        "Импорт CSV",
        "Пользователи",
        "Журнал аудита",
    ])
        assert.equal(button(name), undefined);
});

test("Card renders untrusted problem as text; edit sends saved version and client", async (t) => {
    const { w, click, requests } = await setup(t);
    click("Открыть");
    await waitFor(() => w.document.querySelector("#modal").open);
    assert.ok(
        w.document.querySelector("#modal").textContent.includes(repair.problem),
    );
    assert.equal(w.document.querySelector("#modal img"), null);
    click("Редактировать");
    await waitFor(() => w.document.querySelector("#modal form"));
    const form = w.document.querySelector("#modal form");
    assert.equal(form.elements.clientId.disabled, true);
    assert.equal(form.elements.problem.value, repair.problem);
    form.elements.cost.value = "2500.50";
    form.dispatchEvent(
        new w.Event("submit", { bubbles: true, cancelable: true }),
    );
    await waitFor(() => requests.some((r) => r.options.method === "PUT"));
    const body = JSON.parse(
        requests.find((r) => r.options.method === "PUT").options.body,
    );
    assert.equal(body.version, 3);
    assert.equal(body.clientId, "c1");
    assert.equal(body.cost, "2500.50");
    assert.equal(body.urgent, true);
});

test("Changing filters replaces previous tag/cost tokens and keeps free text", async (t) => {
    const { w, requests } = await setup(t);
    async function search(tag, cost) {
        const form = w.document.querySelector("form.panel");
        form.querySelector("input[type=search]").value = "Atlant";
        form.elements.tag.value = tag;
        form.elements.operator.value = ">=";
        form.elements.cost.value = cost;
        const previous = requests.length;
        form.dispatchEvent(new w.Event("submit", { cancelable: true }));
        await waitFor(
            () =>
                requests
                    .slice(previous)
                    .some((r) => r.url.startsWith("/api/repairs?")) &&
                w.document.querySelector("form.panel") !== form,
        );
        return new URL(
            requests
                .filter((r) => r.url.startsWith("/api/repairs?"))
                .at(-1).url,
            w.location,
        ).searchParams.get("q");
    }
    assert.ok(
        (await search("на гарантии", "1000")).includes('"tag:на гарантии"'),
    );
    const q = await search("согласовано", "2000");
    assert.ok(q.includes("cost>=2000"));
    assert.ok(!q.includes("1000") && !q.includes("на гарантии"));
    assert.equal(
        w.document.querySelector("input[type=search]").value,
        "Atlant",
    );
});

test("Save conflict stays in the form and shows server explanation", async (t) => {
    const { w, click, failWrites } = await setup(t);
    click("+ Новая заявка");
    await waitFor(() => w.document.querySelector("#modal form"));
    failWrites("Номер заявки уже используется");
    const form = w.document.querySelector("#modal form");
    form.elements.clientId.value = "c1";
    form.dispatchEvent(new w.Event("submit", { cancelable: true }));
    await waitFor(() => form.querySelector(".error").textContent);
    assert.equal(
        form.querySelector(".error").textContent,
        "Номер заявки уже используется",
    );
    assert.equal(w.document.querySelector("#modal").open, true);
    assert.equal(form.querySelector("[type=submit]").disabled, false);
});

test("CSV application requires preview; preview and result report row counts", async (t) => {
    const { w, click, button } = await setup(t);
    click("Импорт CSV");
    assert.equal(button("Применить импорт").disabled, true);
    const file = w.document.querySelector("input[type=file]");
    Object.defineProperty(file, "files", {
        value: [new w.File(["csv"], "repairs.csv", { type: "text/csv" })],
    });
    click("Проверить файл");
    await waitFor(() => !button("Применить импорт").disabled);
    assert.ok(
        w.document
            .querySelector(".result")
            .textContent.includes("ошибочных: 1"),
    );
    click("Применить импорт");
    await waitFor(() =>
        w.document.querySelector(".result").textContent.includes("Создано: 1"),
    );
    assert.equal(button("Применить импорт").disabled, true);
});
