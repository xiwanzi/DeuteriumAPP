# DeuteriumAPP Android

这是 DeuteriumAPP 的 Android 前端工程。

维护者请先阅读：[Android 前端维护文档](docs/maintenance.md)。
AI 代理需要构建 APK 时请阅读：[Android 前端 AI 构建指南](docs/ai-build-guide.md)。

当前版本对接生产后端 API，用于账号、信用点钱包转账和聊天主流程。

## 当前版本

- `versionCode`: `5`
- `versionName`: `1.0.3`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 本版本包含聊天页前台持续收取改进：WebSocket 实时接收、前台 15 秒静默补齐、回前台立即补齐，以及“X 条新消息”提示。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit + OkHttp
- 单 Activity、单 App 模块
- 包名：`com.deuterium.app`
- `minSdk 26`

## 生产接口

- HTTP Base URL：`https://example.com/api/v1`
- Chat WebSocket URL：`wss://example.com/api/v1/chat/ws`

登录成功后，App 使用后端返回的 opaque Bearer token 调用需要登录态的接口。玩家引用使用后端返回的 `playerRef`，Android 不解析、不展示、不构造服务器身份内部值。

## 本地运行

当前仓库已提交 Gradle Wrapper，但没有提交 Android SDK 或本地 SDK 配置。

在已安装 Android Studio 和 Android SDK 的机器上：

1. 用 Android Studio 打开 `android-app`。
2. 等待 Gradle 同步。
3. 运行 `app` 配置。

如果要在命令行构建：

```powershell
cd C:\DeuteriumAPP\android-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

