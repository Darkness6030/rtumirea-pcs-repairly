# Проверка примера Student Hub из архива

Проверено **12 октября 2026**. Использован предоставленный `student-hub-suite.zip`.
SHA-256: `cd32c1db5828e17ffb0ce3bddeb7267b9d37f66315ec93b734d8d1598f26427c`.

Архив распакован целиком в отдельную папку `Downloads/repairly-kr2-reference`.
В ней сохранены backend, javafx-client и android-client. Эти исходники не копируются
в Repairly: у примера собственная база `student_hub` и REST API, а у Repairly —
база `repairly` из КР №1 и прямое JDBC-подключение.

## Особенности именно скачанного архива

В файлах архива обнаружены два отличия от текста задания:

1. `.env` лежит в `backend`, а не в корне. Для указанной ниже команды его копируют
   из `backend/.env` в корень распакованного проекта.
2. В `backend/docker-compose.yml` у сервиса `db` указано
   `127.0.0.1:${POSTGRES_PORT:-5433}:5433`. При этом PostgreSQL и datasource backend
   используют внутренний порт **5432**. Исправлено на
   `127.0.0.1:${POSTGRES_PORT:-55433}:5432`.

В отдельной распакованной копии внесены только эти исправления и локальные порты.
Исходный ZIP не изменён. Обычный профиль Docker Compose используется без dev-watcher.

На проверочной машине `8080` занят другим Docker-проектом, `55432` — локальным PostgreSQL.
Поэтому в корневом `.env` примера установлены:

```dotenv
APP_PORT=18080
POSTGRES_PORT=55433
CORS_ALLOWED_ORIGINS=http://localhost:18080
```

Пользователь и пароль БД, JWT и начальная учётная запись берутся из `.env` архива.
Секреты здесь не повторяются. Внутренний порт backend остаётся `8080`.

## Повторный запуск

Все команды ниже выполняются **из корня распакованного Student Hub**:

```bash
docker compose --env-file .env -f backend/docker-compose.yml \
  -p repairly-student-hub-example up -d --wait db backend

docker compose --env-file .env -f backend/docker-compose.yml \
  -p repairly-student-hub-example ps
```

При первой сборке Maven внутри контейнера скачивает Spring Boot и зависимости,
поэтому запуск может занять несколько минут. Логи:

```bash
docker compose --env-file .env -f backend/docker-compose.yml \
  -p repairly-student-hub-example logs --tail=50 backend
```

Адреса для проверки:

- Веб-система: <http://localhost:18080/>
- Healthcheck: <http://localhost:18080/actuator/health>
- REST API: `http://127.0.0.1:18080/api`
- PostgreSQL примера: `jdbc:postgresql://localhost:55433/student_hub`

На дату проверки оба контейнера **healthy**, web вернул **HTTP 200**, healthcheck —
**HTTP 200** и `{"status":"UP","groups":["liveness","readiness"]}`.
Запрос авторизации администратора в `/api/auth/login` вернул **HTTP 200** и
`passwordChangeRequired: true`. Новый пароль не устанавливался: при первом
интерактивном входе его выбирает участник команды.

После проверки backend запускается desktop-клиент примера:

```bash
cd javafx-client
STUDENTHUB_API=http://127.0.0.1:18080/api mvn javafx:run
```

В PowerShell:

```powershell
cd javafx-client
$env:STUDENTHUB_API = 'http://127.0.0.1:18080/api'
mvn javafx:run
```

Если Maven отдельно не установлен, можно вызвать `mvnw` из каталога Repairly
с `-f /путь/к/примеру/javafx-client/pom.xml`. Переменная `STUDENTHUB_API` читается
самим JavaFX-примером и наследуется дочерним процессом Maven.

JavaFX-пример успешно собран и запущен через Maven. Изучены LoginView,
MainView, StudentsPane, DashboardPane, FxTasks и организация CSS. В этом архиве
экран строится программно: для Repairly FXML добавлен самостоятельно,
как отдельно требует задание. Полный интерактивный сеанс после обязательной
смены первого пароля не выполнялся; это не требуется для JDBC-клиента Repairly.

Остановка примера с сохранением базы:

```bash
docker compose --env-file .env -f backend/docker-compose.yml \
  -p repairly-student-hub-example down
```

Не используйте `-v`, если данные должны сохраниться. PostgreSQL Repairly на `55434`
и пример Student Hub на `55433` используют разные volumes и не заменяют друг друга.
