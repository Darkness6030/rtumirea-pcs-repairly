#!/usr/bin/env sh
set -eu
cd "$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)"
./backend/init-env.sh
docker compose up -d --build --wait
app_port=$(sed -n 's/^APP_PORT=//p' .env | tail -n 1 | tr -d '\r')
printf 'Repairly запущен: http://localhost:%s/\n' "${app_port:-8080}"
