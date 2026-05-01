# DeuteriumBridge Minecraft Plugin

Target server: Mohist 1.20.1 with Java 17.

Required server plugins:

- Vault
- XConomy configured as Vault economy provider

Build:

```powershell
..\.tools\gradle-8.7\bin\gradle.bat -p minecraft-plugin shadowJar
```

Install:

1. Copy `build/libs/deuterium-minecraft-plugin-0.1.0.jar` to the server `plugins` directory.
2. Start once to generate `plugins/DeuteriumBridge/config.yml`.
3. Fill backend WebSocket token. If backend and server are on the same machine, keep:
   `ws://127.0.0.1:28658/bridge/plugin/ws`.
4. Restart the server.

Do not commit production `config.yml` values.

The plugin bridge port `28658` should stay local-only and should not be exposed through the intranet tunnel.


