# REST API Repairly КР3

Базовый путь `/api`, данные JSON, UUID в URL. DTO не раскрывают JPA-сущности или хеши
паролей. Для защищённых запросов: `Authorization: Bearer <accessToken>`.
Ответ каждой операции содержит `X-Correlation-ID`.

## Авторизация

| Метод и путь | Тело / результат |
|---|---|
| POST /auth/login | `{email,password,clientType:"WEB" или "NATIVE"}` → accessToken, сроки, user; native также refreshToken |
| POST /auth/refresh | WEB: cookie; NATIVE: `{refreshToken}` → новая пара токенов |
| POST /auth/logout | Cookie или `{refreshToken}` → 204, отзыв сессии |
| GET /auth/me | UUID, email, displayName, roles, passwordChangeRequired |
| POST /auth/change-initial-password | `{newPassword}` → 204, затем refresh для нового access JWT |
| POST /auth/change-password | `{currentPassword,newPassword}` → 204, затем повторный вход |

Пароль: минимум 12 символов, максимум 72 байта UTF-8, верхний/нижний регистр, цифра,
спецсимвол; не содержит имя учётной записи. ADMIN создаёт/сбрасывает временный пароль,
после чего пользователь обязан его заменить. Сохранён только BCrypt-хеш.

## Заявки

| Метод и путь | Доступ / назначение |
|---|---|
| GET /repairs | VIEWER/EDITOR/ADMIN, фильтр `q`, `page`, `size`, `sort` |
| GET /repairs/{id} | Карточка, включая архивную |
| POST /repairs | EDITOR/ADMIN, создание, HTTP 201 |
| PUT /repairs/{id} | EDITOR/ADMIN, обязательная актуальная `version` |
| DELETE /repairs/{id} | EDITOR/ADMIN, архивирование |
| POST /repairs/{id}/restore | EDITOR/ADMIN, восстановление |
| DELETE /repairs/{id}/permanent | ADMIN, только архив, feature flag и `X-Confirm-Delete: PERMANENTLY_DELETE` |
| POST /repairs/bulk | EDITOR/ADMIN, `{ids,action,status?,tag?}` |
| GET /repairs/export | EDITOR/ADMIN, `q`, Accept: text/csv |
| POST /repairs/import/preview | EDITOR/ADMIN, multipart `file`, query `mode` |
| POST /repairs/import/apply | EDITOR/ADMIN, тот же файл и режим, повторная валидация |

Пример создания (UUID справочников берутся из GET /clients и /technicians):

```json
{
  "externalId": "R-2026-001",
  "applianceType": "Стиральная машина",
  "brand": "Bosch",
  "model": "Serie 4",
  "problem": "Не сливает воду",
  "clientId": "UUID клиента",
  "technicianId": null,
  "status": "NEW",
  "cost": 1500.00,
  "urgent": true,
  "tags": ["гарантия"]
}
```

PUT принимает те же поля и `version`. В ответе дополнительно есть UUID, имена/контакты
связанных объектов, priorityLevel, archived, archivedAt, completedAt, createdAt,
updatedAt и актуальная version. Закрытые/архивные заявки не редактируются. Для перехода
в DIAGNOSING, WAITING_PARTS, IN_REPAIR, COMPLETED нужен мастер.

Массовые действия: ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG; от 1 до 500 UUID.
Ответ: `{requested,succeeded,failures:[{id,message}]}`. Каждый UUID — отдельная транзакция.

Фильтр — комбинация свободного текста и токенов:

```text
"не греет" technician:T-1 status:IN_REPAIR urgent:true priority:high tag:гарантия cost>=1000 archived:false
```

Допустимые статусы: NEW, DIAGNOSING, WAITING_PARTS, IN_REPAIR, COMPLETED, CANCELLED.
Приоритеты: LOW, MEDIUM, HIGH, CRITICAL, NOT_APPLICABLE.
`archived:false` — по умолчанию, `true` — только архив, `all` — все.
Стоимость поддерживает `<`, `<=`, `=`, `>=`, `>` и диапазон 0–9999999999.99.
Фильтр мастера сравнивает код точно, без учёта регистра. Тег сравнивается точно,
без регистра. Токен с пробелом можно заключить целиком в кавычки: `"tag:срочный заказ"`.
Все условия соединены AND, пользовательский текст не конкатенируется с SQL/JPQL.

Сортировка: `code`, `name`, `cost`, `status`, `priority`, `created`, `updated`, `technician`,
направление `asc` или `desc`. Вторая сортировка по UUID обеспечивает стабильные страницы.
`page` начинается с 0, `size` от 1 до 100. Ответ: content, page, size, totalElements,
totalPages, first, last.

## Справочники и заметки

| Метод и путь | Назначение |
|---|---|
| GET /clients, GET /technicians | Списки; `activeOnly=true` исключает отключённых |
| POST /clients, POST /technicians | ADMIN, создание |
| PUT /clients/{id}, PUT /technicians/{id} | ADMIN, изменение с version |
| PATCH /clients/{id}/active, /technicians/{id}/active | ADMIN, query `active` и `version` |
| GET /repairs/{id}/notes | История заметок, закреплённые сначала |
| POST /repairs/{id}/notes | EDITOR/ADMIN, `{body,pinned}` |
| PUT /repairs/{id}/notes/{noteId} | Автор или ADMIN, `{body,pinned,version}` |
| DELETE /repairs/{id}/notes/{noteId} | Автор или ADMIN |

Общие поля справочника: code (латиница/цифры/-/_), name, phone, active, version.
Клиент: address; мастер: specialization. Телефон — 10–15 цифр, возможен начальный +.
Связь с уже отключённой записью сохраняется; выбрать её для новой заявки нельзя.

## CSV

UTF-8, разделитель запятая, первая строка — заголовки:

```csv
externalId,applianceType,brand,model,problem,client,technician,status,cost,urgent,tags
CSV-001,Чайник,Demo,K1,Не включается,C-1,T-1,NEW,1500.00,false,гарантия|диагностика
```

`client` и `technician` — коды справочников. Мастер может быть пустым для NEW.
Новые строки создаются только со статусом NEW. Обновления соблюдают те же переходы и
запреты, что PUT. CREATE_ONLY отклоняет существующий externalId;
UPSERT_BY_EXTERNAL_ID обновляет соответствующую запись с проверкой версии.
Файл до 5 MiB / 5000 строк. Preview не пишет данные; apply повторяет проверки и применяет
корректные строки независимо. При ошибке строки её изменения откатываются.
`invalidRows` — число ошибочных строк, а не число сообщений.
Экспорт учитывает весь текущий фильтр, не только страницу, нейтрализует формулы Excel,
поддерживает BOM, кавычки и переносы строк. Экспорт архивных/закрытых записей служит
отчётом: их нельзя импортировать как новые с закрытым статусом.

## Аналитика, пользователи, аудит

- GET `/analytics/summary`: total, active (неархивные), archived, urgent (неархивные
  срочные), averageCost (неархивные), byTechnician, byStatus, byPriority.
- GET `/admin/users`: ADMIN, page/size/sort (email/name/created/updated).
- POST `/admin/users`: email, displayName, temporaryPassword, roles.
- PUT `/admin/users/{id}`: email, displayName, enabled, roles, version.
- POST `/admin/users/{id}/reset-password`: temporaryPassword, ответ 204.
- GET `/admin/audit`: ADMIN, q/page/size/sort (occurred/actor/event).

## Ошибки

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 409,
  "code": "CONFLICT",
  "message": "Заявка уже изменена. Обновите карточку",
  "path": "/api/repairs/...",
  "correlationId": "...",
  "validationErrors": {}
}
```

400 — неверные данные; 401 — нет действительной аутентификации; 403 — нет прав или
нужна смена временного пароля; 404 — запись отсутствует; 409 — версия/уникальность;
413 — слишком большой файл. Stack trace доступен только в серверном журнале.
