# Repairly — сервис ремонта бытовой техники

**КР №3:** Spring Boot REST API, PostgreSQL, Flyway и веб-интерфейс для сервисного центра.
Основная сущность — заявка на ремонт; справочники — клиенты и мастера. Это развитие
базы и бизнес-правил КР №1, а не новая система на тему Student Hub.

## Ветки и структура

- `kr1` — консольное приложение.
- `kr2` — настольный JavaFX-клиент с JDBC.
- `kr3` — backend и web. Разработка КР №3 ведётся здесь; предыдущие ветки сохранены.
- `master` — опубликованное состояние КР №2 до отдельного переноса КР №3.

```text
backend/
  pom.xml                       Spring Boot 3.5.5, Java 21
  Dockerfile                    Сборка и запуск backend
  src/main/java/ru/rtumirea/repairly/backend/
    controller/ service/ repository/ domain/ dto/
    security/ filter/ error/ config/ web/
  src/main/resources/
    application.yml
    db/migration/               Flyway: база КР1 + расширение КР3
    static/                     index.html, app.js, styles.css
  scripts/verify_api.py          Приёмочные HTTP-проверки в отдельной тестовой БД
  scripts/verify_postgres.py     Создание и очистка изолированной тестовой БД
src/                            Сохранённый JavaFX-клиент КР2 и его тесты
pom.xml                         Maven-проект JavaFX
mvnw, mvnw.cmd                  Maven Wrapper 3.9.11, общий для обоих проектов
```

В Docker работают **PostgreSQL и Spring Boot**. Веб-интерфейс отдаёт Spring Boot,
JavaScript выполняется в браузере и обращается к `/api`. React не требуется:
клиент реализован на JavaScript с DOM API и CSS, без отдельной сборки Node.js.
JavaFX не запускается в Docker и не нужен для работы web.

## Быстрый запуск

Нужен запущенный Docker с Compose (поддержкой `include`). Java и Maven внутри
контейнера уже предусмотрены. Для локальной сборки/тестов и JavaFX нужен JDK 21.
Первая Docker-сборка скачивает образы и Maven-зависимости.

macOS/Linux, из корня репозитория:

```bash
./run.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

Скрипт дополняет локальный `.env` недостающими параметрами, создаёт случайные секреты
и выполняет `docker compose up -d --build --wait`. Существующие реквизиты БД сохраняются.
Не копируйте `.env.example` поверх рабочего `.env`: это шаблон без действительных секретов.

Ручной запуск после настройки `.env`:

```bash
docker compose up -d --build --wait
# Равнозначный вход через каталог backend:
docker compose --env-file .env -f backend/docker-compose.yml up -d --build --wait
```

- Web: http://localhost:8080/
- Health: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui.html — после web-входа администратором.
- OpenAPI: http://localhost:8080/v3/api-docs — ADMIN, Bearer JWT или Swagger-cookie.
- PostgreSQL: `localhost:55434`, если `DB_PORT` не изменён.

Если порт 8080 занят, измените `APP_PORT` и `CORS_ALLOWED_ORIGINS` в `.env`.
Если меняется порт базы, синхронно измените `DB_PORT` и `DB_URL` для JavaFX.

## Вход и роли

Логин первого администратора — `BOOTSTRAP_ADMIN_EMAIL` (по умолчанию `admin@repairly.local`).
Случайный временный пароль находится в `BOOTSTRAP_ADMIN_PASSWORD` локального `.env`.
Первый вход требует смены пароля. Изменение `.env` не сбрасывает пароль существующего пользователя.

| Роль | Возможности |
|---|---|
| VIEWER | Просмотр заявок, карточек, заметок, справочников и аналитики |
| EDITOR | Дополнительно CRUD заявок, архив/восстановление, массовые действия, CSV и заметки |
| ADMIN | Дополнительно справочники, пользователи, аудит, Swagger и окончательное удаление |

Изменить/удалить заметку может её автор или администратор. Для окончательного удаления
нужны ADMIN, архивная запись, `PERMANENT_DELETE_ENABLED=true` и явное подтверждение.

Пароли — BCrypt, access JWT — 15 минут по умолчанию. Web access хранится только в памяти,
refresh — в HttpOnly SameSite=Strict cookie; в PostgreSQL хранится хеш refresh-токена.
Для Android предусмотрен `clientType: "NATIVE"`: refresh возвращается в JSON и передаётся
в JSON-запросах refresh/logout. При смене/сбросе пароля прежние access JWT отзываются.
Неудачные входы учитываются в БД; после пяти попыток применяется временная блокировка.

## База и миграция КР1 → КР3

Используется прежний volume `repairly_postgres_data` и таблицы `clients`, `technicians`,
`repair_requests`. Перед обновлением рекомендуется сохранить резервную копию:

```bash
mkdir -p backups
docker compose up -d --wait postgres
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backups/before-kr3.dump
```

Flyway запускается до Hibernate. `ddl-auto: validate` проверяет схему, не создаёт её.

- `V1__kr1_schema.sql` создаёт исходную схему на пустой базе; на базе КР1 безопасно
  пропускает существующие таблицы. Для известной базы КР1 baseline установлен в 0.
- `V2__kr3_backend.sql` добавляет UUID `public_id`, коды, активность справочников,
  архив, приоритет, срочность, временные метки, пользователей/роли, refresh-сессии,
  теги, заметки и аудит. Прежние числовые ID и внешние ключи остаются на месте.
- Старые локальные даты интерпретируются как Europe/Moscow и переводятся в TIMESTAMPTZ.
  REST возвращает ISO 8601, браузер отображает местное время.
- Старые заявки получают код `R-<id>`, клиенты `C-<id>`, мастера `T-<id>`.

Миграции не загружают демонстрационные данные поверх существующих.
Для пустой учебной базы можно отдельно выполнить прежний идемпотентный seed:

```bash
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < src/main/resources/db/migration/seed.sql
```

JavaFX сохранён как исторический JDBC-клиент. Для защиты КР3 используйте web:
прямой JDBC-доступ JavaFX не проходит роли и аудит backend. При параллельных изменениях
через desktop нажимайте обновление в web. API КР3 предназначено для будущего Android-клиента.

## Функции

- Заявки: UUID, уникальный номер, техника, неисправность, клиент, мастер, статус,
  стоимость, срочность, теги, вычисляемый приоритет, даты, версия и архив.
- Полные формы, просмотр карточки и заметок; архив и восстановление вместо обычного удаления.
- Поиск по нескольким полям, фильтры, серверная пагинация 1–100 записей, стабильная сортировка.
- Массовые архив/восстановление/статус/теги с отдельными ошибками каждого UUID.
- CSV: предварительная проверка, применение, только новые или обновление по номеру,
  экспорт текущего фильтра. Импорт повторно проверяет данные на момент применения.
- Аналитика: все/активные/архивные/срочные, средняя стоимость, распределения по мастерам,
  статусам и приоритетам. «Активные» в аналитике означает неархивные.
- Пользователи с обязательной сменой временного пароля; журнал аудита с correlation ID.

Правила КР1 сохранены: новые заявки имеют статус NEW, закрытые заявки не редактируются,
клиента зарегистрированной заявки менять нельзя, для рабочего статуса нужен мастер.
Приоритет: закрытая → NOT_APPLICABLE; срочная с ожиданием запчастей → CRITICAL;
срочная → HIGH; ожидание запчастей или стоимость ≥ 10000 → MEDIUM; иначе LOW.

## REST и фильтры

Основные пути: `/api/auth`, `/api/repairs`, `/api/clients`, `/api/technicians`,
`/api/analytics/summary`, `/api/admin/users`, `/api/admin/audit`.
Полный контракт доступен в Swagger и описан в [docs/kr3-api.md](docs/kr3-api.md).

Пример фильтра `q` (передаётся с URL-кодированием):

```text
Samsung technician:T-1 status:IN_REPAIR urgent:true priority:high tag:гарантия cost>=1000 archived:false
```

Сравнения стоимости: `<`, `<=`, `=`, `>=`, `>`. `archived:all` включает архив.
Значения фильтра передаются через Criteria API. Сортировка выбирается только из белого списка.

## Проверки

```bash
./mvnw -B -ntp test                         # регрессия КР2
./mvnw -B -ntp -f backend/pom.xml verify    # backend + context smoke
node --check backend/src/main/resources/static/app.js
# Необязательная проверка web-форм: Node.js 22+, только для разработки
npm --prefix backend ci --ignore-scripts
npm --prefix backend test
# PostgreSQL: отдельная БД, миграция КР1, HTTP-проверки, автоматическая очистка
python3 backend/scripts/verify_postgres.py
```

`verify_postgres.py` запускает `verify_api.py` в изоляции. Прямой вызов `verify_api.py`
создаёт/изменяет данные и пароль администратора: используйте его **только на отдельной
тестовой базе**, см. [docs/kr3-verification.md](docs/kr3-verification.md).

## Управление запуском

```bash
docker compose ps
docker compose logs --tail=100 backend
curl http://localhost:8080/actuator/health
docker compose stop                      # остановка
docker compose down                      # удаляет контейнеры, сохраняет данные
```

Не добавляйте `-v`, если нужно сохранить базу.

Для отдельного запуска JavaFX: `./mvnw javafx:run`. Инструкция КР2 сохранена в
[docs/kr2-readme.md](docs/kr2-readme.md); её старые команды запуска относятся к ветке `kr2`.

Материалы защиты: [docs/kr3-defense.md](docs/kr3-defense.md).
Архив преподавателя перенесён в `docs/reference/student-hub-suite.zip`, исключён из Git:
внутри есть чужие демонстрационные секреты и кэши. Проект собирается без архива.
Происхождение заимствованной инфраструктуры описано в [docs/reference/README.md](docs/reference/README.md).
