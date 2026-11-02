$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$path = Join-Path $root '.env'
if (!(Test-Path $path)) { [IO.File]::WriteAllText($path, '') }
function RandomHex($bytes) {
    $buffer = New-Object byte[] $bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($buffer)).Replace('-', '').ToLower()
}
$jwtBytes = New-Object byte[] 48
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
$defaults = [ordered]@{
 DB_NAME='repairly'; DB_USER='repairly'; DB_PASSWORD=(RandomHex 24); DB_PORT='55434';
 DB_URL='jdbc:postgresql://localhost:55434/repairly'; JWT_SECRET_BASE64=[Convert]::ToBase64String($jwtBytes);
 BOOTSTRAP_ADMIN_EMAIL='admin@repairly.local'; BOOTSTRAP_ADMIN_NAME='Repairly Administrator';
 BOOTSTRAP_ADMIN_PASSWORD=('Tmp1!' + (RandomHex 16)); APP_PORT='8080'; COOKIE_SECURE='false'; PERMANENT_DELETE_ENABLED='true'
}
foreach ($key in $defaults.Keys) {
 if ([IO.File]::ReadAllText($path) -notmatch "(?m)^$key=") {
   [IO.File]::AppendAllText($path, "`n$key=$($defaults[$key])`n", (New-Object Text.UTF8Encoding($false)))
 }
}
Write-Host 'Configuration ready. Read administrator credentials from local .env.'
