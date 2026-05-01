param(
    [string]$DbHost = "127.0.0.1",
    [string]$DbPort = "3306",
    [string]$DbName = "deuterium_app",
    [string]$DbUser = "root",
    [string]$DbPassword,
    [string]$PublicHost = "0.0.0.0",
    [int]$PublicPort = 28657,
    [string]$BridgeHost = "127.0.0.1",
    [int]$BridgePort = 28658
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    throw "DbPassword is required."
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$deliveryRoot = Join-Path $root "delivery\DeuteriumAPP-production"
$backendProject = Join-Path $root "backend-api"
$pluginProject = Join-Path $root "minecraft-plugin"
$backendRuntimeSource = Join-Path $backendProject "build\deuterium-backend-runtime"
$backendRuntimeTarget = Join-Path $deliveryRoot "backend"
$pluginTarget = Join-Path $deliveryRoot "minecraft-plugin"

function New-Secret([int]$Bytes = 32) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buffer)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($buffer).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Get-ExistingConfigValue([string]$Key) {
    $configPath = Join-Path $deliveryRoot "backend\config\application.conf"
    if (-not (Test-Path $configPath)) {
        return $null
    }
    $escaped = [Regex]::Escape($Key)
    $line = Get-Content -Path $configPath |
        Where-Object { $_ -match "^\s*$escaped\s*=" } |
        Select-Object -First 1
    if (-not $line) {
        return $null
    }
    return (($line -split "=", 2)[1]).Trim()
}

function New-OrExistingSecret([string]$Key) {
    $existing = Get-ExistingConfigValue $Key
    if ([string]::IsNullOrWhiteSpace($existing)) {
        return New-Secret
    }
    return $existing
}

function Copy-DirectoryFresh($Source, $Target) {
    if (Test-Path $Target) {
        Remove-Item -Recurse -Force $Target
    }
    New-Item -ItemType Directory -Force $Target | Out-Null
    Copy-Item -Recurse -Force (Join-Path $Source "*") $Target
}

$bridgeToken = New-OrExistingSecret "pluginBridge.token"
$sessionPepper = New-OrExistingSecret "security.sessionTokenPepper"
$verificationPepper = New-OrExistingSecret "security.verificationPepper"
$section = [char]0x00A7
$chatBroadcastFormat = "${section}x${section}b${section}1${section}f${section}7${section}f${section}f%player% ${section}7: ${section}f%message%"

$pluginLocalResources = Join-Path $pluginProject "src\main\local-resources"
New-Item -ItemType Directory -Force $pluginLocalResources | Out-Null
@"
backend:
  ws-url: "ws://127.0.0.1:$BridgePort/bridge/plugin/ws"
  token: "CHANGE_ME"
  reconnect-seconds: 5

chat:
  broadcast-format: "$chatBroadcastFormat"

wallet-monitor:
  enabled: true
  online-poll-seconds: 15
  offline-poll-seconds: 300
  max-offline-players-per-cycle: 100

events:
  death: true
  server-say: true
  join: true
  quit: true
"@ | Set-Content -Encoding UTF8 (Join-Path $pluginLocalResources "config.yml")

Push-Location $backendProject
try {
    .\gradlew.bat test prepareWindowsRuntime
} finally {
    Pop-Location
}

Push-Location $pluginProject
try {
    .\gradlew.bat clean shadowJar
} finally {
    Pop-Location
}

if (Test-Path $deliveryRoot) {
    Remove-Item -Recurse -Force $deliveryRoot
}
New-Item -ItemType Directory -Force $backendRuntimeTarget, $pluginTarget | Out-Null
Copy-DirectoryFresh $backendRuntimeSource $backendRuntimeTarget

$jdbcUrl = "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
@"
# Generated local production config. Do not commit.
public.host=$PublicHost
public.port=$PublicPort

bridge.host=$BridgeHost
bridge.port=$BridgePort

database.jdbcUrl=$jdbcUrl
database.user=deuterium_app
database.password=CHANGE_ME
database.maximumPoolSize=10

security.sessionTokenPepper=CHANGE_ME_TO_LONG_RANDOM_VALUE
security.verificationPepper=CHANGE_ME_TO_ANOTHER_LONG_RANDOM_VALUE
security.sessionDays=30

pluginBridge.token=CHANGE_ME_TO_LONG_RANDOM_VALUE
pluginBridge.requestTimeoutMillis=10000
pluginBridge.heartbeatIntervalMillis=30000
pluginBridge.staleAfterMillis=90000

chat.historyRetentionDays=30
chat.websocketPingIntervalMillis=15000
chat.websocketTimeoutMillis=35000

app.latestVersionCode=5
app.latestVersionName=1.0.3

log.level=INFO
"@ | Set-Content -Encoding UTF8 (Join-Path $backendRuntimeTarget "config\application.conf")

$jlink = (Get-Command jlink -ErrorAction SilentlyContinue)
if ($jlink) {
    $jreTarget = Join-Path $backendRuntimeTarget "jre"
    if (Test-Path $jreTarget) {
        Remove-Item -Recurse -Force $jreTarget
    }
    & $jlink.Source `
        --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported `
        --strip-debug `
        --no-header-files `
        --no-man-pages `
        --output $jreTarget
}

$pluginJar = Get-ChildItem (Join-Path $pluginProject "build\libs") -Filter "*.jar" |
    Where-Object { $_.Name -like "*deuterium-minecraft-plugin*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $pluginJar) {
    throw "Plugin jar was not generated."
}
Copy-Item -Force $pluginJar.FullName (Join-Path $pluginTarget $pluginJar.Name)

@"
# DeuteriumAPP Production Delivery

## Backend

Run on the Windows server:

```powershell
backend\migrate-db.bat
backend\start-backend.bat
```

For test cleanup only:

```powershell
backend\reset-database.bat
```

Public API listen port: $PublicPort
Plugin bridge local port: $BridgePort

Expose only port $PublicPort through HTTPS intranet tunnel.

Base URL:

```text
https://example.com/api/v1
```

Chat WebSocket:

```text
wss://example.com/api/v1/chat/ws
```

## Minecraft Plugin

Copy the jar in `minecraft-plugin` to the server `plugins` directory.
The jar includes default same-machine bridge settings for `127.0.0.1:$BridgePort`.
"@ | Set-Content -Encoding UTF8 (Join-Path $deliveryRoot "README.md")

Write-Host "Production delivery generated at: $deliveryRoot"

