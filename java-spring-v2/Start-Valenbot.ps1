param([string]$ConfigFile = '')
$ErrorActionPreference = 'Stop'
$appRoot = $PSScriptRoot
if (-not $ConfigFile) { $ConfigFile = Join-Path (Split-Path $appRoot -Parent) 'runtime\v2\config.json' }
if (-not (Test-Path -LiteralPath $ConfigFile)) { throw 'Config file is missing. See README.md for first-time setup.' }
$cfg = Get-Content -Raw -Encoding UTF8 -LiteralPath $ConfigFile | ConvertFrom-Json
if ($cfg.database -ne 'robot_sn_v2') { throw 'This launcher only runs the independent robot_sn_v2 database.' }
$runtimeRoot = Split-Path (Resolve-Path -LiteralPath $ConfigFile).Path -Parent
$jar = Join-Path $runtimeRoot 'robot-sn-system-2.2.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw "JAR not found: $jar" }
if (Get-NetTCPConnection -State Listen -LocalPort $cfg.port -ErrorAction SilentlyContinue) { Write-Host "Port $($cfg.port) is already in use. Check http://127.0.0.1:$($cfg.port)/"; exit 1 }
$cred = Import-Clixml -LiteralPath (Join-Path $runtimeRoot 'credentials.xml')
$env:VALENBOT_DB_URL = "jdbc:postgresql://$($cfg.dbHost):$($cfg.dbPort)/$($cfg.database)"
$env:VALENBOT_DB_USER = $cfg.dbUser
$env:VALENBOT_DB_PASSWORD = [Net.NetworkCredential]::new('', $cred.DbPassword).Password
$env:VALENBOT_PUBLIC_URL = $cfg.publicUrl
$env:VALENBOT_PORT = [string]$cfg.port
$env:VALENBOT_BOOTSTRAP_FILE = Join-Path $runtimeRoot 'bootstrap.json'
Write-Host "Valenbot v2: http://127.0.0.1:$($cfg.port)/"
Write-Host 'Keep this window open. Ctrl+C stops this v2 instance only.'
& $cfg.java '-Dfile.encoding=UTF-8' '-jar' $jar "--logging.file.name=$runtimeRoot/app.log"
exit $LASTEXITCODE
