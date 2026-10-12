$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
    docker compose up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось запустить PostgreSQL. Проверьте Docker и порт в .env.' }
    & .\mvnw.cmd javafx:run
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось запустить Repairly. Проверьте JDK 21 и вывод Maven.' }
} finally {
    Pop-Location
}
