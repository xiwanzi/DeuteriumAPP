# DeuteriumAPP 运行端口与 Base URL 约定 v1

## 端口

后端第一版使用两个不常见端口：

- `28657`：公开 App API 与 App Chat WebSocket 端口。
- `28658`：同机 Minecraft 插件桥端口，只监听 `127.0.0.1`。

内网穿透只需要转发 `28657`。不要把 `28658` 暴露到公网或内网穿透。

## Android Base URL

本机测试时：

```text
http://127.0.0.1:28657/api/v1
```

真实手机通过内网穿透访问时：

```text
https://<你的内网穿透域名>/api/v1
```

如果内网穿透只提供 HTTP，则临时测试地址为：

```text
http://<你的内网穿透域名>/api/v1
```

正式发布建议使用 HTTPS。

## Chat WebSocket URL

通过 HTTPS 隧道时：

```text
wss://<你的内网穿透域名>/api/v1/chat/ws
```

通过 HTTP 隧道临时测试时：

```text
ws://<你的内网穿透域名>/api/v1/chat/ws
```

## 插件桥 URL

后端和 Minecraft 服务端在同一台机器时，插件配置保持：

```text
ws://127.0.0.1:28658/bridge/plugin/ws
```

插件桥使用 `Authorization: Bearer <plugin-bridge-token>` 鉴权。token 必须只写在生产机器本地配置里，不提交到仓库。


