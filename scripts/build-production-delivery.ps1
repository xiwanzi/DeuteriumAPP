param(
    [string]$DbHost = "127.0.0.1",
    [string]$DbPort = "3306",
    [string]$DbName = "deuterium_app",
    [string]$DbUser = "root",
    [string]$DbPassword,
    [string]$PublicHost = "0.0.0.0",
    [int]$PublicPort = 28657,
    [string]$BridgeHost = "127.0.0.1",
    [int]$BridgePort = 28658,
    [switch]$EnableOidc,
    [switch]$AllowOidcInsecureHttp,
    [string]$OidcIssuer,
    [string]$OidcClientId,
    [string]$OidcClientSecret,
    [string]$OidcRedirectUri
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

function Get-ValueOrExistingOrDefault([string]$Key, [string]$Value, [string]$Default) {
    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        return $Value
    }
    $existing = Get-ExistingConfigValue $Key
    if (-not [string]::IsNullOrWhiteSpace($existing)) {
        return $existing
    }
    return $Default
}

function Get-Sha256Hex([string]$Value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        $hash = $sha.ComputeHash($bytes)
        return -join ($hash | ForEach-Object { $_.ToString("x2") })
    } finally {
        $sha.Dispose()
    }
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
$existingOidcEnabled = Get-ExistingConfigValue "oidc.enabled"
$oidcEnabled = if ($EnableOidc.IsPresent) { "true" } elseif (-not [string]::IsNullOrWhiteSpace($existingOidcEnabled)) { $existingOidcEnabled } else { "false" }
$existingOidcAllowInsecureHttp = Get-ExistingConfigValue "oidc.allowInsecureHttp"
$oidcAllowInsecureHttp = if ($AllowOidcInsecureHttp.IsPresent) { "true" } elseif (-not [string]::IsNullOrWhiteSpace($existingOidcAllowInsecureHttp)) { $existingOidcAllowInsecureHttp } else { "false" }
$oidcIssuerValue = Get-ValueOrExistingOrDefault "oidc.issuer" $OidcIssuer "https://auth.deuterium.cafe"
$oidcClientIdValue = Get-ValueOrExistingOrDefault "oidc.clientId" $OidcClientId "wikijs"
$oidcRedirectUriValue = Get-ValueOrExistingOrDefault "oidc.redirectUri" $OidcRedirectUri "https://wiki.deuterium.cafe/login/oidc/callback"
$oidcClientSecretHash = if (-not [string]::IsNullOrWhiteSpace($OidcClientSecret)) {
    Get-Sha256Hex $OidcClientSecret
} else {
    Get-ExistingConfigValue "oidc.clientSecretHash"
}
if ($oidcEnabled -eq "true" -and [string]::IsNullOrWhiteSpace($oidcClientSecretHash)) {
    throw "OidcClientSecret is required when enabling OIDC for the first time."
}
$existingOidcSigningKeyPath = Join-Path $deliveryRoot "backend\config\oidc-signing-key.json"
$existingOidcSigningKey = if (Test-Path $existingOidcSigningKeyPath) {
    Get-Content -Raw -Path $existingOidcSigningKeyPath
} else {
    $null
}
$section = [char]0x00A7
$chatBroadcastFormat = "${section}x${section}b${section}1${section}f${section}7${section}f${section}f%player% ${section}7: ${section}f%message%"

$pluginLocalResources = Join-Path $pluginProject "src\main\local-resources"
New-Item -ItemType Directory -Force $pluginLocalResources | Out-Null
@"
backend:
  ws-url: "ws://127.0.0.1:$BridgePort/bridge/plugin/ws"
  token: "$bridgeToken"
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
database.user=$DbUser
database.password=$DbPassword
database.maximumPoolSize=10

security.sessionTokenPepper=$sessionPepper
security.verificationPepper=$verificationPepper
security.sessionDays=30

pluginBridge.token=$bridgeToken
pluginBridge.requestTimeoutMillis=10000
pluginBridge.heartbeatIntervalMillis=30000
pluginBridge.staleAfterMillis=90000

chat.historyRetentionDays=30
chat.websocketPingIntervalMillis=15000
chat.websocketTimeoutMillis=35000

oidc.enabled=$oidcEnabled
oidc.issuer=$oidcIssuerValue
oidc.clientId=$oidcClientIdValue
oidc.clientSecretHash=$oidcClientSecretHash
oidc.redirectUri=$oidcRedirectUriValue
oidc.signingKeyPath=config/oidc-signing-key.json
oidc.allowInsecureHttp=$oidcAllowInsecureHttp
oidc.authorizationCodeMinutes=5
oidc.accessTokenMinutes=10
oidc.idTokenMinutes=10
oidc.webSessionHours=12

app.latestVersionCode=5
app.latestVersionName=1.0.3

log.level=INFO
"@ | Set-Content -Encoding UTF8 (Join-Path $backendRuntimeTarget "config\application.conf")

if (-not [string]::IsNullOrWhiteSpace($existingOidcSigningKey)) {
    Set-Content -Encoding UTF8 -Path (Join-Path $backendRuntimeTarget "config\oidc-signing-key.json") -Value $existingOidcSigningKey
}

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
https://deuterium.s.odn.cc/api/v1
```

Chat WebSocket:

```text
wss://deuterium.s.odn.cc/api/v1/chat/ws
```

OIDC for Wiki.js:

```text
Enabled: $oidcEnabled
Issuer: $oidcIssuerValue
Client ID: $oidcClientIdValue
Redirect URI: $oidcRedirectUriValue
Allow insecure HTTP: $oidcAllowInsecureHttp
```

If OIDC is enabled with HTTP, keep it restricted to the intended internal environment.
Preserve `backend/config/oidc-signing-key.json` across package replacements.

## Minecraft Plugin

Copy the jar in `minecraft-plugin` to the server `plugins` directory.
The jar includes default same-machine bridge settings for `127.0.0.1:$BridgePort`.
"@ | Set-Content -Encoding UTF8 (Join-Path $deliveryRoot "README.md")

Write-Host "Production delivery generated at: $deliveryRoot"
