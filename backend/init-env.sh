#!/usr/bin/env sh
set -eu
cd "$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
umask 077
touch .env
append_missing() {
  if ! grep -q "^$1=" .env; then printf '\n%s=%s\n' "$1" "$2" >> .env; fi
}
append_missing DB_NAME repairly
append_missing DB_USER repairly
append_missing DB_PASSWORD "$(openssl rand -hex 24)"
append_missing DB_PORT 55434
append_missing DB_URL jdbc:postgresql://localhost:55434/repairly
append_missing JWT_SECRET_BASE64 "$(openssl rand -base64 48 | tr -d '\n')"
append_missing BOOTSTRAP_ADMIN_EMAIL admin@repairly.local
append_missing BOOTSTRAP_ADMIN_NAME 'Администратор Repairly'
append_missing BOOTSTRAP_ADMIN_PASSWORD "Tmp1!$(openssl rand -hex 16)"
append_missing APP_PORT 8080
append_missing COOKIE_SECURE false
append_missing PERMANENT_DELETE_ENABLED true
printf 'Настройки готовы. Реквизиты администратора находятся в локальном .env.\n'
