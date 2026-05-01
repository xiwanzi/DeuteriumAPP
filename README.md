# DeuteriumAPP

> Native Android companion app, backend API, and Minecraft plugin bridge for a server community.

## 中文

DeuteriumAPP 是一个面向 Minecraft 服务器社区的开源 App 方案。它把移动端账号、服务器钱包、玩家转账、公共聊天、在线状态、通知和版本检查连接到同一个可维护的系统里。

这个项目最初服务于 `Deuterium VIII` 服务器，但代码结构按可复用的服务器社区产品来组织：Android App 负责玩家体验，Backend API 负责安全与业务编排，Minecraft Plugin Bridge 负责连接服务器内能力。

### 主要能力

- 原生 Android App：账号、钱包、转账、聊天、资料页、通知、本地聊天历史、检查更新。
- 独立后端：登录会话、验证码、钱包流水、转账编排、聊天记录、玩家目录、App WebSocket。
- Minecraft 插件桥：游戏内验证码、聊天转发、Vault/XConomy 钱包操作、服务器事件同步。
- 实时体验：聊天 WebSocket、在线人数、玩家目录、@ 提及、钱包变动事件、断线重连补齐。
- 离线同步：App 离线时后端仍记录钱包流水，App 上线后按增量同步点补齐。
- 兼容策略：新增能力默认通过可选字段、新接口或新 WebSocket 事件实现，尽量不破坏旧客户端。

### 架构原则

- Android App 不直连数据库。
- Android App 不直接修改 Minecraft 服务器经济数据。
- 后端是鉴权、校验、持久化和业务编排中心。
- Minecraft 插件只作为服务器能力 adapter，通过本地 WebSocket 连接后端。
- 生产密钥、数据库密码、pepper、插件 token、APK/JAR/ZIP 交付物不进入公开仓库。

### 当前状态

- Android App：`versionCode 5` / `versionName 1.0.3`
- Backend：Kotlin/JVM + Ktor + MySQL + Exposed + Flyway
- Minecraft Plugin：Java 17 + Bukkit/Spigot API + Vault/XConomy
- 当前重点：稳定聊天连接、钱包流水同步、公开源码整理、后续补 Android release signing。

### 快速开始

```powershell
# Android
cd android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# Backend
cd backend-api
.\gradlew.bat test prepareWindowsRuntime

# Minecraft plugin
cd minecraft-plugin
.\gradlew.bat clean shadowJar
```

更完整的本地配置、数据库、插件和构建说明见：

- [快速开始](docs/getting-started.md)
- [技术架构](docs/technical-architecture.md)
- [开发维护手册](docs/development-handbook.md)
- [当前进度](docs/project-status.md)
- [接口合同](docs/contracts/app-backend-api-v1.md)

### 开源发布说明

本仓库的公开版本应只包含源码、示例配置和非敏感文档。维护者从私有工作区发布公开版本时，应使用：

```powershell
.\scripts\export-public-clean.ps1
```

脚本会生成干净副本，并排除本地工具、构建产物、交付包、真实配置和已知生产密钥。

### License

开源许可证尚未在公开发布前最终确认。正式发布前请补充 `LICENSE` 文件。

## English

DeuteriumAPP is an open-source app stack for Minecraft server communities. It connects mobile accounts, server wallet data, player transfers, public chat, online presence, notifications, and update checks into one maintainable system.

The project was originally built for the `Deuterium VIII` server, but the code is organized as a reusable community-server product: the Android App owns the player experience, the Backend API owns security and orchestration, and the Minecraft Plugin Bridge connects controlled server capabilities.

### Features

- Native Android App: account, wallet, transfers, chat, profile, notifications, local chat history, update check.
- Standalone backend: sessions, verification, wallet records, transfer orchestration, chat history, player directory, App WebSocket.
- Minecraft plugin bridge: in-game verification codes, chat forwarding, Vault/XConomy wallet operations, server event sync.
- Realtime experience: chat WebSocket, online count, player directory, @ mentions, wallet record events, reconnect catch-up.
- Offline sync: backend keeps wallet records while the App is offline; the App catches up from its last synced record id.
- Compatibility policy: new capabilities should use optional fields, new endpoints, or new WebSocket events whenever possible.

### Architecture Rules

- The Android App never connects directly to the database.
- The Android App never mutates Minecraft economy data directly.
- The backend is the center for authentication, validation, persistence, and business orchestration.
- The Minecraft plugin is a server-capability adapter connected to the backend over a local WebSocket.
- Production secrets, database passwords, peppers, plugin tokens, APK/JAR/ZIP delivery artifacts must not be committed.

### Current Status

- Android App: `versionCode 5` / `versionName 1.0.3`
- Backend: Kotlin/JVM + Ktor + MySQL + Exposed + Flyway
- Minecraft Plugin: Java 17 + Bukkit/Spigot API + Vault/XConomy
- Current focus: stable chat connectivity, wallet record sync, public source release, and future Android release signing.

### Quick Start

```powershell
# Android
cd android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# Backend
cd backend-api
.\gradlew.bat test prepareWindowsRuntime

# Minecraft plugin
cd minecraft-plugin
.\gradlew.bat clean shadowJar
```

Read more:

- [Getting Started](docs/getting-started.md)
- [Technical Architecture](docs/technical-architecture.md)
- [Development Handbook](docs/development-handbook.md)
- [Project Status](docs/project-status.md)
- [Interface Contract](docs/contracts/app-backend-api-v1.md)

### Public Release Safety

The public repository should contain source code, example config, and non-sensitive documentation only. Maintainers publishing from a private workspace should run:

```powershell
.\scripts\export-public-clean.ps1
```

The script creates a clean copy and excludes local tools, build artifacts, delivery packages, filled config, and known production secrets.

### License

The open-source license has not been finalized yet. Add a `LICENSE` file before the first official public release.

