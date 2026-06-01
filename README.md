# DeuteriumAPP

Minecraft server companion app with a native Android client, Kotlin backend, and Bukkit plugin bridge.

<p align="center">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" /></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84.svg" />
  <img alt="Backend" src="https://img.shields.io/badge/backend-Kotlin%20%2F%20Ktor-7F52FF.svg" />
  <img alt="Minecraft" src="https://img.shields.io/badge/minecraft-Bukkit%20%2F%20Spigot-62B47A.svg" />
</p>

## 项目概览

DeuteriumAPP 是为 Minecraft 服务器 `Deuterium VIII` 构建的原生 Android 伴侣应用。项目把玩家账号、服务器钱包、玩家转账、公共聊天、在线状态、通知和版本检查整合到一套可维护的客户端与服务端系统中。

仓库包含三个运行模块：

| 模块 | 路径 | 技术栈 | 职责 |
| --- | --- | --- | --- |
| Android App | `android-app/` | Kotlin, Jetpack Compose, Material 3 | 玩家登录、钱包、转账、聊天、通知和本地状态 |
| Backend API | `backend-api/` | Kotlin/JVM, Ktor, MySQL, Flyway | 鉴权、业务校验、持久化、WebSocket 和插件桥编排 |
| Minecraft Plugin Bridge | `minecraft-plugin/` | Java 17, Bukkit/Spigot API, Vault/XConomy | 游戏内验证码、聊天转发、服务器钱包操作和事件同步 |

## 功能特性

- 账号体系：玩家 ID 注册与登录、QQ 号登录、游戏内验证码、密码修改。
- 钱包与转账：余额刷新、流水记录、玩家搜索、幂等转账、失败状态反馈。
- 聊天互通：App 与服务器公共聊天互通、历史消息、在线人数、玩家目录、@ 提及通知。
- 实时同步：App WebSocket、钱包事件、聊天事件、断线后的增量补齐。
- 运维边界：后端配置化版本检查、Flyway 数据库迁移、公开源码导出脚本。

## 架构

```text
Android App
  HTTP + App WebSocket
Backend API
  Local plugin WebSocket
Minecraft Plugin Bridge
  Bukkit/Spigot + Vault/XConomy
Minecraft Server
```

核心原则：

- Android App 不直连数据库。
- Android App 不直接修改 Minecraft 服务器经济数据。
- 后端负责鉴权、校验、持久化和业务编排。
- Minecraft 插件只暴露受控的服务器能力，并通过本地 WebSocket 连接后端。
- 生产密钥、数据库密码、pepper、插件 token、APK/JAR/ZIP 交付物不进入公开仓库。

## 当前状态

| 组件 | 版本 / 状态 |
| --- | --- |
| Android App | `versionCode 5` / `versionName 1.0.3` |
| Backend API | `0.1.0` |
| Minecraft Plugin Bridge | `0.1.0` |
| License | Apache License 2.0 |

当前公开版本已经覆盖账号、钱包、转账、聊天和插件桥主流程。Android release signing、正式分发策略、端到端联调脚本和更细的客户端模块拆分仍在后续工作中。

## 快速开始

推荐环境：

- Windows PowerShell
- JDK 17
- Android Studio 或 Android SDK
- MySQL 8 或 MariaDB
- Minecraft 测试服务器：Mohist/Spigot 1.20.1
- Vault 与 XConomy，或其他 Vault economy provider

构建 Android App：

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

构建 Backend API：

```powershell
cd backend-api
.\gradlew.bat test prepareWindowsRuntime
```

构建 Minecraft 插件：

```powershell
cd minecraft-plugin
.\gradlew.bat clean shadowJar
```

完整的本地配置、数据库、插件安装和运行说明见 [快速开始](docs/getting-started.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [快速开始](docs/getting-started.md) | 本地环境、配置、构建与运行 |
| [技术架构](docs/technical-architecture.md) | 运行模块、数据流、接口边界和安全规则 |
| [开发维护手册](docs/development-handbook.md) | 开发流程、版本维护和发布注意事项 |
| [项目状态](docs/project-status.md) | 已实现能力、近期改进和待完成事项 |
| [接口合同](docs/contracts/app-backend-api-v1.md) | App 与后端 API 的字段级约定 |
| [OpenAPI](docs/contracts/openapi-v1.yaml) | 机器可读的 HTTP API 描述 |

## 仓库结构

```text
.
├── android-app/          # Native Android client
├── backend-api/          # Kotlin/Ktor backend service
├── minecraft-plugin/     # Bukkit/Spigot bridge plugin
├── docs/                 # Product, architecture, contracts, and operation docs
├── design/               # App icon and visual assets
└── scripts/              # Release and repository maintenance scripts
```

## 公开发布

公开仓库应只包含源码、示例配置和非敏感文档。维护者从私有工作区导出公开版本时，应使用：

```powershell
.\scripts\export-public-clean.ps1
```

脚本会生成干净副本，并排除本地工具、构建产物、交付包、真实配置和已知生产密钥。

## License

DeuteriumAPP is licensed under the [Apache License 2.0](LICENSE).
