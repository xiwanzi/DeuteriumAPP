# DeuteriumAPP Backend API

Backend service for DeuteriumAPP.

## Stack

- Kotlin/JVM
- Ktor
- MySQL/MariaDB
- Exposed
- Flyway
- Java 17

## Build

```powershell
.\gradlew.bat test prepareWindowsRuntime
```

The Windows runtime directory is generated at:

```text
backend-api/build/deuterium-backend-runtime
```

## Configuration

Copy:

```text
backend-api/src/main/dist/config/application.example.conf
```

to the runtime directory as:

```text
config/application.conf
```

Fill real database credentials, plugin bridge token, and security peppers. Do not commit the filled config file.

## Runtime

Inside the generated runtime directory:

```powershell
.\migrate-db.bat
.\start-backend.bat
```

Run `migrate-db.bat` before replacing a production backend. Current migrations include the V4 maintenance indexes used by the expired-data cleanup task and the V5 OIDC Provider tables used by Wiki.js login.

The backend starts a lightweight cleanup loop every 6 hours. It removes expired sessions, old verification contexts, stale login failures, chat records older than `chat.historyRetentionDays`, and old server events. This does not require Android or Minecraft plugin changes.

## Ports And URLs

Production defaults:

- Public App API listen address: `0.0.0.0:28657`
- Local plugin bridge listen address: `127.0.0.1:28658`

Only expose `28657` through the intranet tunnel. Do not expose `28658`.

Local public API base URL:

```text
http://127.0.0.1:28657/api/v1
```

Tunnel public API base URL:

```text
https://<your-tunnel-domain>/api/v1
```

App chat WebSocket through the tunnel:

```text
wss://<your-tunnel-domain>/api/v1/chat/ws
```

Plugin bridge WebSocket on the same production machine:

```text
ws://127.0.0.1:28658/bridge/plugin/ws
```

Public App API base path:

```text
/api/v1
```

Health checks:

```text
/health/live
/health/ready
```

## Wiki.js OIDC Provider

OIDC is disabled by default. When enabled, the backend also exposes these routes on the public `28657` server:

```text
/.well-known/openid-configuration
/.well-known/jwks.json
/oauth/authorize
/oauth/login
/oauth/token
/oauth/userinfo
/oauth/logout
```

Minimum config keys:

```properties
oidc.enabled=true
oidc.issuer=http://authdeuterium.s.odn.cc
oidc.clientId=wikijs
oidc.clientSecretHash=<sha256-hex-of-client-secret>
oidc.redirectUri=https://wiki.deuterium.cafe/login/oidc/callback
oidc.signingKeyPath=config/oidc-signing-key.json
oidc.allowInsecureHttp=true
```

Use HTTPS for any public password-login origin. `oidc.allowInsecureHttp=true` is only for internal HTTP-only environments.

Keep `config/oidc-signing-key.json` when replacing runtime directories. Do not commit the Wiki.js client secret, the filled `application.conf`, or the OIDC signing private key.

Full contract: `docs/contracts/oidc-provider-v1.md`.
