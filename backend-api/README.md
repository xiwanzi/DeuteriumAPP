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

