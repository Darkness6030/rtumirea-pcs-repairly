#!/usr/bin/env sh
set -eu
cd "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
if [ ! -f .env ]; then cp .env.example .env; fi
docker compose up -d --wait
exec ./mvnw javafx:run
