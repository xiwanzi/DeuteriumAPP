# DeuteriumAPP Technical Architecture / 技术架构文档

Last updated / 更新时间：2026-05-02

## 1. 中文版

### 1.1 目标

本文档记录 DeuteriumAPP 当前公开版本需要理解的技术架构、运行模块、核心数据流、接口约束、构建环境和安全发布规则。维护者应先读本文，再进入具体代码。

### 1.2 运行模块

DeuteriumAPP 由三个运行模块组成：

- Android App：原生 Android 客户端，面向玩家。负责界面、输入、通知、本地聊天历史、网络调用和 WebSocket 连接。
- Backend API：Kotlin/Ktor 后端。负责账号、会话、钱包、转账、聊天、玩家目录、版本检查、数据库持久化和插件桥编排。
- Minecraft Plugin Bridge：Java 插件。运行在 Minecraft 服务器内，通过 WebSocket 连接后端，并调用 Bukkit/Spigot、Vault、XConomy 等服务器能力。

模块关系：

```text
Android App
  HTTP + App WebSocket
Backend API
  Local plugin WebSocket
Minecraft Plugin Bridge
  Bukkit/Spigot + Vault/XConomy
Minecraft Server
```

### 1.3 技术栈

Android:

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit
- OkHttp
- Gson
- Kotlin Coroutines
- SQLiteOpenHelper
- Gradle Wrapper 8.7
- Android Gradle Plugin 8.6.1
- Kotlin Android plugin 2.3.10
- Java target 17

Backend:

- Kotlin/JVM 1.9.24
- Ktor 2.3.12
- Netty
- kotlinx.serialization JSON
- MySQL/MariaDB
- HikariCP
- Exposed 0.53.0
- Flyway 10.17.0
- Argon2
- Java 17

Minecraft plugin:

- Java 17
- Bukkit/Spigot API 1.20.1
- Mohist 1.20.1 target runtime
- Vault API
- XConomy as Vault economy provider
- Java-WebSocket 1.5.7
- org.json 20240303
- Shadow plugin with relocated runtime dependencies

### 1.4 关键 interface 与 seam

Android network seam:

- `BackendApi` 定义 HTTP interface。
- `ApiClient` 是 Retrofit、OkHttp、错误解析和 WebSocket 创建的 adapter。
- UI 不应直接处理 Retrofit response。

Android state and domain seam:

- `AppRepositories.kt` 目前承载账号、钱包、聊天仓库。
- UI 使用 repository 暴露的状态和 `RepoResult`。
- `ChatFeedMerger` 负责聊天消息去重、合并和顺序保持。
- `ChatHistoryStore` 负责本地 SQLite 聊天历史。

Backend plugin seam:

- `PluginBridge` 是后端访问 Minecraft 能力的 interface。
- `WebSocketPluginBridge` 是当前 adapter。
- 后端业务代码不直接依赖插件内部实现。

Backend App WebSocket seam:

- `AppChatHub` 维护 App WebSocket session、前后台状态、广播、@ 提及、钱包事件和失败连接清理。
- 后端使用标准 WebSocket ping/pong 探活清理半开连接，不新增业务心跳消息。

Database seam:

- Exposed table 定义在 `Tables.kt`。
- Flyway migration 定义在 `backend-api/src/main/resources/db/migration/`。
- 数据库 schema 变更必须通过 migration，不要依赖手动改表。

### 1.5 数据流

账号注册：

1. App 调用 `POST /account/registration-code`。
2. Backend 生成 verification token 与验证码上下文。
3. Backend 通过插件桥让 Minecraft 服务器向玩家私聊验证码。
4. App 提交验证码到 `POST /account/register`。
5. Backend 完成账号绑定并返回 opaque bearer token。

钱包转账：

1. App 只提交目标 `playerRef`、金额和 `clientRequestId`。
2. Backend 校验登录、玩家引用、金额、幂等键和业务状态。
3. Backend 通过插件桥让服务器经济系统执行钱包操作。
4. Backend 写入 transfer 与 wallet record。
5. App 通过 HTTP 和 WebSocket 同步状态。

聊天互通：

1. App 建立 `/api/v1/chat/ws`。
2. App 发送 `chat.send`，只提交内容和可选 `mentionedPlayerRefs`。
3. Backend 使用当前 session 身份作为发送者，不信任客户端发送者字段。
4. Backend 通过插件桥转发到 Minecraft。
5. Backend 写入聊天记录并广播 `chat.message`。
6. Minecraft 服务器事件和聊天进入后端后，通过 App WebSocket 推送。

钱包离线流水同步：

1. 后端在 App 离线时继续写入 `wallet_records`。
2. Android 为每个账号保存最后同步的 `walletRecordId`。
3. 登录、重连、回前台时调用 `GET /wallet/records?afterRecordId=...`。
4. 收到实时 `wallet.record.event` 时去重合并并推进同步点。

版本检查：

1. App 调用 `GET /api/v1/app/update-check?versionCode=...&versionName=...`。
2. Backend 只读取配置 `app.latestVersionCode` 和 `app.latestVersionName`。
3. 不访问数据库。

### 1.6 公共接口

主要 HTTP interface：

- `GET /health/live`
- `GET /health/ready`
- `GET /api/v1/app/update-check`
- `POST /api/v1/account/registration-code`
- `POST /api/v1/account/register`
- `POST /api/v1/account/login`
- `POST /api/v1/account/password-reset-code`
- `POST /api/v1/account/password-reset`
- `POST /api/v1/account/logout`
- `GET /api/v1/account/me`
- `GET /api/v1/wallet/balance`
- `POST /api/v1/wallet/balance/refresh`
- `GET /api/v1/wallet/recipients/search`
- `POST /api/v1/wallet/transfers`
- `GET /api/v1/wallet/transfers/{transferId}`
- `GET /api/v1/wallet/records`
- `GET /api/v1/chat/messages`
- `GET /api/v1/chat/presence`
- `GET /api/v1/chat/online-players`
- `GET /api/v1/chat/player-directory`
- `GET /api/v1/chat/follows`
- `POST /api/v1/chat/follows`
- `DELETE /api/v1/chat/follows/{playerRef}`

WebSocket interface：

- App: `GET /api/v1/chat/ws`
- Plugin: `GET /bridge/plugin/ws`

完整字段以 `docs/contracts/app-backend-api-v1.md` 和 `docs/contracts/openapi-v1.yaml` 为准。

### 1.7 配置与安全

公开版本只保留示例配置。以下内容不得进入 GitHub：

- 填好的 `application.conf`
- 数据库密码
- session pepper
- verification pepper
- plugin bridge token
- Android signing keystore
- APK/JAR/ZIP 交付物
- `delivery/`
- `dist/`
- `.tools/`
- `minecraft-plugin/src/main/local-resources/`
- `android-app/local.properties`

公开导出必须使用：

```powershell
cd C:\DeuteriumAPP
.\scripts\export-public-clean.ps1
```

### 1.8 构建与验证

Android:

```powershell
cd C:\DeuteriumAPP\android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Backend:

```powershell
cd C:\DeuteriumAPP\backend-api
.\gradlew.bat test prepareWindowsRuntime
```

Minecraft plugin:

```powershell
cd C:\DeuteriumAPP\minecraft-plugin
.\gradlew.bat clean shadowJar
```

公开导出验证：

```powershell
cd C:\DeuteriumAPP
.\scripts\export-public-clean.ps1
```

脚本会生成公开副本并扫描当前已知生产密钥、私有域名和敏感配置。

### 1.9 当前限制

- Android release signing 尚未配置。
- Debug App 仍是测试分发形态，不应作为正式发布包。
- 后端-only WebSocket 探活可清理半开连接，但“回前台立即强制重连”仍需要 Android 端小改。
- `MainActivity.kt` 和 `AppRepositories.kt` 仍偏大，未来可按页面和 feature module 继续加深 interface。

## 2. English Version

### 2.1 Goal

This document records the technical architecture, runtime modules, core data flows, interface rules, build environment, and safe public release workflow for the current DeuteriumAPP public source release.

### 2.2 Runtime Modules

DeuteriumAPP has three runtime modules:

- Android App: the native player-facing client. It owns UI, input, notifications, local chat history, network calls, and WebSocket connections.
- Backend API: the Kotlin/Ktor backend. It owns accounts, sessions, wallet, transfers, chat, player directory, update checks, persistence, and plugin bridge orchestration.
- Minecraft Plugin Bridge: the Java plugin running inside the Minecraft server. It connects to the backend over WebSocket and uses Bukkit/Spigot, Vault, and XConomy capabilities.

Module relationship:

```text
Android App
  HTTP + App WebSocket
Backend API
  Local plugin WebSocket
Minecraft Plugin Bridge
  Bukkit/Spigot + Vault/XConomy
Minecraft Server
```

### 2.3 Technology Stack

Android:

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit
- OkHttp
- Gson
- Kotlin Coroutines
- SQLiteOpenHelper
- Gradle Wrapper 8.7
- Android Gradle Plugin 8.6.1
- Kotlin Android plugin 2.3.10
- Java target 17

Backend:

- Kotlin/JVM 1.9.24
- Ktor 2.3.12
- Netty
- kotlinx.serialization JSON
- MySQL/MariaDB
- HikariCP
- Exposed 0.53.0
- Flyway 10.17.0
- Argon2
- Java 17

Minecraft plugin:

- Java 17
- Bukkit/Spigot API 1.20.1
- Mohist 1.20.1 target runtime
- Vault API
- XConomy as the Vault economy provider
- Java-WebSocket 1.5.7
- org.json 20240303
- Shadow plugin with relocated runtime dependencies

### 2.4 Important Interfaces And Seams

Android network seam:

- `BackendApi` defines the HTTP interface.
- `ApiClient` is the Retrofit, OkHttp, error parsing, and WebSocket adapter.
- UI code should not handle Retrofit responses directly.

Android state and domain seam:

- `AppRepositories.kt` currently owns account, wallet, and chat repositories.
- UI consumes repository state and `RepoResult`.
- `ChatFeedMerger` owns chat message dedupe, merge, and ordering.
- `ChatHistoryStore` owns local SQLite chat history.

Backend plugin seam:

- `PluginBridge` is the backend interface to Minecraft capabilities.
- `WebSocketPluginBridge` is the current adapter.
- Backend business code should not depend on plugin internals.

Backend App WebSocket seam:

- `AppChatHub` owns App WebSocket sessions, foreground state, broadcasts, @ mentions, wallet events, and failed connection cleanup.
- Backend WebSocket keepalive uses standard ping/pong frames. It does not introduce business heartbeat messages.

Database seam:

- Exposed table definitions live in `Tables.kt`.
- Flyway migrations live in `backend-api/src/main/resources/db/migration/`.
- Schema changes must go through migrations.

### 2.5 Data Flows

Account registration:

1. The App calls `POST /account/registration-code`.
2. The backend creates a verification token and verification context.
3. The backend asks the plugin bridge to send the verification code through an in-game private message.
4. The App submits the code to `POST /account/register`.
5. The backend binds the account and returns an opaque bearer token.

Wallet transfer:

1. The App only submits the target `playerRef`, amount, and `clientRequestId`.
2. The backend validates login state, player reference, amount, idempotency key, and business state.
3. The backend asks the plugin bridge to execute the economy operation through the server economy system.
4. The backend writes transfer and wallet record data.
5. The App syncs state through HTTP and WebSocket.

Chat bridge:

1. The App connects to `/api/v1/chat/ws`.
2. The App sends `chat.send` with content and optional `mentionedPlayerRefs`.
3. The backend uses the current session identity as the sender and does not trust client sender fields.
4. The backend forwards the chat message to Minecraft through the plugin bridge.
5. The backend stores the message and broadcasts `chat.message`.
6. Minecraft chat and server events flow back to Apps through backend WebSocket events.

Offline wallet record sync:

1. The backend keeps writing `wallet_records` while the App is offline.
2. Android stores the last synced `walletRecordId` per account.
3. Login, reconnect, and foreground resume call `GET /wallet/records?afterRecordId=...`.
4. Realtime `wallet.record.event` messages are deduped and merged, then advance the sync point.

Update check:

1. The App calls `GET /api/v1/app/update-check?versionCode=...&versionName=...`.
2. The backend only reads `app.latestVersionCode` and `app.latestVersionName` from config.
3. The endpoint does not query the database.

### 2.6 Public Interfaces

Main HTTP interface:

- `GET /health/live`
- `GET /health/ready`
- `GET /api/v1/app/update-check`
- `POST /api/v1/account/registration-code`
- `POST /api/v1/account/register`
- `POST /api/v1/account/login`
- `POST /api/v1/account/password-reset-code`
- `POST /api/v1/account/password-reset`
- `POST /api/v1/account/logout`
- `GET /api/v1/account/me`
- `GET /api/v1/wallet/balance`
- `POST /api/v1/wallet/balance/refresh`
- `GET /api/v1/wallet/recipients/search`
- `POST /api/v1/wallet/transfers`
- `GET /api/v1/wallet/transfers/{transferId}`
- `GET /api/v1/wallet/records`
- `GET /api/v1/chat/messages`
- `GET /api/v1/chat/presence`
- `GET /api/v1/chat/online-players`
- `GET /api/v1/chat/player-directory`
- `GET /api/v1/chat/follows`
- `POST /api/v1/chat/follows`
- `DELETE /api/v1/chat/follows/{playerRef}`

WebSocket interface:

- App: `GET /api/v1/chat/ws`
- Plugin: `GET /bridge/plugin/ws`

For full field-level contracts, see `docs/contracts/app-backend-api-v1.md` and `docs/contracts/openapi-v1.yaml`.

### 2.7 Configuration And Security

The public release only keeps example config. These files and values must not be published:

- filled `application.conf`
- database password
- session pepper
- verification pepper
- plugin bridge token
- Android signing keystore
- APK/JAR/ZIP delivery artifacts
- `delivery/`
- `dist/`
- `.tools/`
- `minecraft-plugin/src/main/local-resources/`
- `android-app/local.properties`

Generate the public copy with:

```powershell
cd C:\DeuteriumAPP
.\scripts\export-public-clean.ps1
```

### 2.8 Build And Verification

Android:

```powershell
cd C:\DeuteriumAPP\android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Backend:

```powershell
cd C:\DeuteriumAPP\backend-api
.\gradlew.bat test prepareWindowsRuntime
```

Minecraft plugin:

```powershell
cd C:\DeuteriumAPP\minecraft-plugin
.\gradlew.bat clean shadowJar
```

Public export verification:

```powershell
cd C:\DeuteriumAPP
.\scripts\export-public-clean.ps1
```

The script creates a public copy and scans for currently known production secrets, private domains, and sensitive config values.

### 2.9 Current Limits

- Android release signing is not configured.
- Debug Android builds are test artifacts and should not be treated as public production releases.
- Backend-only WebSocket keepalive can clean up half-open connections, but deterministic "reconnect immediately on foreground" still needs a small Android patch.
- `MainActivity.kt` and `AppRepositories.kt` are still large. Future work can deepen their interfaces by splitting UI and feature modules.

