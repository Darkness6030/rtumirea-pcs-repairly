$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/init-env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
 docker compose up -d --build --wait
 if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed' }
 Write-Host 'Repairly: http://localhost:8080/ (or APP_PORT from .env)'
} finally { Pop-Location }
