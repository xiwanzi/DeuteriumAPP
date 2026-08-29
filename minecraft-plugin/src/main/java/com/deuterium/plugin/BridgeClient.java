package com.deuterium.plugin;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeClient {
    private static final int MAX_DEBIT_RESULT_CACHE_SIZE = 8192;

    private final DeuteriumBridgePlugin plugin;
    private final URI uri;
    private final String token;
    private final int reconnectSeconds;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Map<String, JSONObject> debitResultsByIdempotencyKey = new ConcurrentHashMap<>();
    private final Object debitLock = new Object();
    private final File debitResultCacheFile;
    private volatile Client client;

    public BridgeClient(DeuteriumBridgePlugin plugin, URI uri, String token, int reconnectSeconds) {
        this.plugin = plugin;
        this.uri = uri;
        this.token = token;
        this.reconnectSeconds = reconnectSeconds;
        this.debitResultCacheFile = new File(plugin.getDataFolder(), "wallet-debit-results.json");
        loadDebitResults();
    }

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::connect);
    }

    public void shutdown() {
        shutdown.set(true);
        Client active = client;
        if (active != null) {
            active.close();
        }
    }

    public void sendServerChat(Player player, String content) {
        JSONObject payload = new JSONObject()
            .put("serverMessageId", "mc_msg_" + UUID.randomUUID().toString().replace("-", ""))
            .put("serverUuid", player.getUniqueId().toString())
            .put("currentGameId", player.getName())
            .put("content", content)
            .put("occurredAt", Instant.now().toString());
        sendEvent("chat.serverMessage.event", payload);
    }

    public void sendServerEvent(String eventType, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        JSONObject payload = new JSONObject()
            .put("eventId", "mc_evt_" + UUID.randomUUID().toString().replace("-", ""))
            .put("eventType", eventType)
            .put("content", content)
            .put("occurredAt", Instant.now().toString());
        sendEvent("server.event", payload);
    }

    public boolean sendWalletPayEvent(
        String payEventId,
        String fromServerUuid,
        String fromGameId,
        String toServerUuid,
        String toGameId,
        BigDecimal amount,
        double fromBalanceAfter,
        double toBalanceAfter
    ) {
        JSONObject payload = new JSONObject()
            .put("payEventId", payEventId)
            .put("fromServerUuid", fromServerUuid)
            .put("fromGameId", fromGameId)
            .put("toServerUuid", toServerUuid)
            .put("toGameId", toGameId)
            .put("amount", money(amount))
            .put("currency", "CREDIT")
            .put("status", "success")
            .put("note", "\u670D\u52A1\u5668\u5185\u8F6C\u8D26")
            .put("fromBalanceAfter", money(fromBalanceAfter))
            .put("toBalanceAfter", money(toBalanceAfter))
            .put("occurredAt", Instant.now().toString());
        return sendEvent("wallet.pay.event", payload);
    }

    public boolean sendWalletBalanceChangeEvent(
        String eventId,
        String serverUuid,
        String gameId,
        String direction,
        BigDecimal amount,
        double balanceAfter,
        String source,
        String note
    ) {
        JSONObject payload = new JSONObject()
            .put("eventId", eventId)
            .put("serverUuid", serverUuid)
            .put("gameId", gameId)
            .put("direction", direction)
            .put("amount", money(amount))
            .put("currency", "CREDIT")
            .put("status", "success")
            .put("source", source)
            .put("note", note)
            .put("balanceAfter", money(balanceAfter))
            .put("occurredAt", Instant.now().toString());
        return sendEvent("wallet.balanceChange.event", payload);
    }

    public void sendPresenceSnapshot() {
        JSONArray players = new JSONArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.put(new JSONObject()
                .put("serverUuid", player.getUniqueId().toString())
                .put("currentGameId", player.getName())
                .put("onlineSince", Instant.now().toString()));
        }
        JSONObject payload = new JSONObject()
            .put("onlineCount", players.length())
            .put("players", players);
        sendEvent("presence.snapshot.event", payload);
    }

    private void connect() {
        if (shutdown.get()) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        Client next = new Client(uri, headers);
        client = next;
        try {
            next.connect();
        } catch (Exception e) {
            plugin.getLogger().warning("Plugin bridge connect failed: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shutdown.get()) {
            return;
        }
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::connect, reconnectSeconds * 20L);
    }

    public boolean isOpen() {
        Client active = client;
        return active != null && active.isOpen();
    }

    private boolean sendEvent(String type, JSONObject payload) {
        Client active = client;
        if (active == null || !active.isOpen()) {
            return false;
        }
        active.send(envelope(type, messageId(), null, payload).toString());
        return true;
    }

    private void sendResult(String type, String replyTo, JSONObject payload) {
        Client active = client;
        if (active == null || !active.isOpen()) {
            return;
        }
        active.send(envelope(type, messageId(), replyTo, payload).toString());
    }

    private JSONObject envelope(String type, String messageId, String replyTo, JSONObject payload) {
        JSONObject body = new JSONObject()
            .put("type", type)
            .put("messageId", messageId)
            .put("sentAt", Instant.now().toString())
            .put("payload", payload);
        if (replyTo != null) {
            body.put("replyTo", replyTo);
        }
        return body;
    }

    private String messageId() {
        return "bridge_msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    private final class Client extends WebSocketClient {
        private Client(URI serverUri, Map<String, String> httpHeaders) {
            super(serverUri, httpHeaders);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            plugin.getLogger().info("Connected to DeuteriumAPP backend bridge.");
            Bukkit.getScheduler().runTask(plugin, BridgeClient.this::sendPresenceSnapshot);
        }

        @Override
        public void onMessage(String message) {
            JSONObject envelope = new JSONObject(message);
            String type = envelope.optString("type");
            if ("bridge.ping".equals(type)) {
                sendResult("bridge.pong", envelope.optString("messageId"), new JSONObject());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> handleRequest(envelope));
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            String detail = reason == null || reason.isBlank()
                ? "code=" + code + ", remote=" + remote
                : "code=" + code + ", reason=" + reason + ", remote=" + remote;
            plugin.getLogger().warning("Backend bridge closed: " + detail);
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            plugin.getLogger().warning("Backend bridge error: " + ex.getMessage());
        }
    }

    private void handleRequest(JSONObject envelope) {
        String type = envelope.optString("type");
        String messageId = envelope.optString("messageId");
        JSONObject payload = envelope.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        switch (type) {
            case "verification.deliver.request" -> handleVerification(messageId, payload);
            case "player.resolve.request" -> handleResolve(messageId, payload);
            case "wallet.balance.request" -> handleBalance(messageId, payload);
            case "wallet.transfer.request" -> handleTransfer(messageId, payload);
            case "wallet.debit.request" -> handleDebit(messageId, payload);
            case "chat.appMessage.request" -> handleAppChat(messageId, payload);
            case "presence.list.request" -> handlePresenceList(messageId);
            default -> plugin.getLogger().warning("Unknown bridge request type: " + type);
        }
    }

    private void handleVerification(String replyTo, JSONObject payload) {
        String gameId = payload.optString("gameId");
        Optional<Player> player = plugin.findOnlinePlayerExact(gameId);
        if (player.isEmpty()) {
            Optional<DeuteriumBridgePlugin.ResolvedPlayer> known = plugin.resolvePlayer(gameId);
            sendResult("verification.deliver.result", replyTo, new JSONObject()
                .put("status", known.isPresent() ? "player_offline" : "player_not_found"));
            return;
        }
        String code = payload.optString("code");
        String purpose = payload.optString("purpose");
        Player target = player.get();
        target.sendMessage("§a[DeuteriumAPP] §f你的 " + purposeLabel(purpose) + " 验证码是：§e" + code + "§f，10 分钟内有效。");
        sendResult("verification.deliver.result", replyTo, new JSONObject()
            .put("status", "delivered")
            .put("serverUuid", target.getUniqueId().toString())
            .put("currentGameId", target.getName()));
    }

    private String purposeLabel(String purpose) {
        if ("password_reset".equals(purpose)) {
            return "密码重设";
        }
        return "注册";
    }

    private void handleResolve(String replyTo, JSONObject payload) {
        String gameId = payload.optString("gameId");
        Optional<DeuteriumBridgePlugin.ResolvedPlayer> resolved = plugin.resolvePlayer(gameId);
        if (resolved.isEmpty()) {
            sendResult("player.resolve.result", replyTo, new JSONObject().put("status", "player_not_found"));
            return;
        }
        DeuteriumBridgePlugin.ResolvedPlayer player = resolved.get();
        sendResult("player.resolve.result", replyTo, new JSONObject()
            .put("status", "resolved")
            .put("serverUuid", player.serverUuid())
            .put("currentGameId", player.currentGameId())
            .put("online", player.online()));
    }

    private void handleBalance(String replyTo, JSONObject payload) {
        if (!plugin.walletAvailable()) {
            sendResult("wallet.balance.result", replyTo, new JSONObject().put("status", "economy_unavailable"));
            return;
        }
        OfflinePlayer player = offlineByUuid(payload.optString("serverUuid"));
        if (player == null || !plugin.economy().hasAccount(player)) {
            sendResult("wallet.balance.result", replyTo, new JSONObject().put("status", "player_not_found"));
            return;
        }
        double balance = plugin.economy().getBalance(player);
        sendResult("wallet.balance.result", replyTo, new JSONObject()
            .put("status", "success")
            .put("amount", money(balance))
            .put("currency", "CREDIT")
            .put("currentGameId", player.getName() == null ? "" : player.getName()));
    }

    private void handleTransfer(String replyTo, JSONObject payload) {
        if (!plugin.walletAvailable()) {
            sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "economy_unavailable"));
            return;
        }
        OfflinePlayer from = offlineByUuid(payload.optString("fromServerUuid"));
        OfflinePlayer to = offlineByUuid(payload.optString("toServerUuid"));
        if (from == null || to == null || !plugin.economy().hasAccount(from) || !plugin.economy().hasAccount(to)) {
            sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "recipient_not_found"));
            return;
        }
        double amount = payload.optDouble("amount", -1);
        if (amount <= 0) {
            sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "failed").put("reason", "invalid_amount"));
            return;
        }
        if (plugin.economy().getBalance(from) + 0.000001 < amount) {
            sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "balance_insufficient"));
            return;
        }
        EconomyResponse withdraw = plugin.economy().withdrawPlayer(from, amount);
        if (!withdraw.transactionSuccess()) {
            sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "failed").put("reason", withdraw.errorMessage));
            return;
        }
        EconomyResponse deposit = plugin.economy().depositPlayer(to, amount);
        if (!deposit.transactionSuccess()) {
            EconomyResponse rollback = plugin.economy().depositPlayer(from, amount);
            if (rollback.transactionSuccess()) {
                sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "failed").put("reason", deposit.errorMessage));
            } else {
                sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "unknown").put("reason", "rollback_failed"));
            }
            return;
        }
        plugin.rememberWalletBalance(from, plugin.economy().getBalance(from));
        plugin.rememberWalletBalance(to, plugin.economy().getBalance(to));
        sendResult("wallet.transfer.result", replyTo, new JSONObject().put("status", "success"));
    }

    private void handleDebit(String replyTo, JSONObject payload) {
        String idempotencyKey = payload.optString("idempotencyKey", payload.optString("debitId", ""));
        double amount = payload.optDouble("amount", -1);
        synchronized (debitLock) {
            JSONObject cached = cachedDebitResult(idempotencyKey, payload, amount);
            if (cached != null) {
                sendResult("wallet.debit.result", replyTo, cached);
                return;
            }
            JSONObject result;
            if (!plugin.walletAvailable()) {
                result = new JSONObject().put("status", "economy_unavailable");
                sendResult("wallet.debit.result", replyTo, result);
                return;
            }
            OfflinePlayer player = offlineByUuid(payload.optString("serverUuid"));
            if (player == null || !plugin.economy().hasAccount(player)) {
                result = new JSONObject().put("status", "player_not_found");
                sendResult("wallet.debit.result", replyTo, result);
                return;
            }
            if (amount <= 0) {
                result = new JSONObject().put("status", "failed").put("reason", "invalid_amount");
                rememberDebitResult(idempotencyKey, debitResultWithMetadata(result, payload, amount));
                sendResult("wallet.debit.result", replyTo, result);
                return;
            }
            if (plugin.economy().getBalance(player) + 0.000001 < amount) {
                result = new JSONObject().put("status", "balance_insufficient");
                rememberDebitResult(idempotencyKey, debitResultWithMetadata(result, payload, amount));
                sendResult("wallet.debit.result", replyTo, result);
                return;
            }
            EconomyResponse withdraw = plugin.economy().withdrawPlayer(player, amount);
            if (!withdraw.transactionSuccess()) {
                result = new JSONObject().put("status", "failed").put("reason", withdraw.errorMessage);
                rememberDebitResult(idempotencyKey, debitResultWithMetadata(result, payload, amount));
                sendResult("wallet.debit.result", replyTo, result);
                return;
            }
            double balanceAfter = plugin.economy().getBalance(player);
            plugin.rememberWalletBalance(player, balanceAfter);
            result = new JSONObject()
                .put("status", "success")
                .put("balanceAfter", money(balanceAfter));
            rememberDebitResult(idempotencyKey, debitResultWithMetadata(result, payload, amount));
            sendResult("wallet.debit.result", replyTo, result);
        }
    }

    private JSONObject cachedDebitResult(String idempotencyKey, JSONObject payload, double amount) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        JSONObject cached = debitResultsByIdempotencyKey.get(idempotencyKey);
        if (cached == null) {
            return null;
        }
        if (!cachedDebitResultMatches(cached, payload, amount)) {
            return new JSONObject().put("status", "failed").put("reason", "idempotency_mismatch");
        }
        return publicDebitResult(cached);
    }

    private boolean cachedDebitResultMatches(JSONObject cached, JSONObject payload, double amount) {
        String cachedServerUuid = cached.optString("serverUuid", "");
        if (!cachedServerUuid.isBlank() && !cachedServerUuid.equals(payload.optString("serverUuid", ""))) {
            return false;
        }
        if (cached.has("amount")) {
            return Math.abs(cached.optDouble("amount", Double.NaN) - amount) < 0.000001;
        }
        return true;
    }

    private JSONObject publicDebitResult(JSONObject cached) {
        JSONObject result = new JSONObject()
            .put("status", cached.optString("status", "failed"));
        if (cached.has("reason")) {
            result.put("reason", cached.optString("reason"));
        }
        if (cached.has("balanceAfter")) {
            result.put("balanceAfter", cached.opt("balanceAfter"));
        }
        return result;
    }

    private void rememberDebitResult(String idempotencyKey, JSONObject result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        String status = result.optString("status", "");
        if (!"success".equals(status) && !"balance_insufficient".equals(status) && !"failed".equals(status)) {
            return;
        }
        debitResultsByIdempotencyKey.putIfAbsent(idempotencyKey, new JSONObject(result.toString()));
        while (debitResultsByIdempotencyKey.size() > MAX_DEBIT_RESULT_CACHE_SIZE) {
            String firstKey = debitResultsByIdempotencyKey.keySet().iterator().next();
            debitResultsByIdempotencyKey.remove(firstKey);
        }
        persistDebitResults();
    }

    private JSONObject debitResultWithMetadata(JSONObject result, JSONObject payload, double amount) {
        return new JSONObject(result.toString())
            .put("debitId", payload.optString("debitId", ""))
            .put("idempotencyKey", payload.optString("idempotencyKey", payload.optString("debitId", "")))
            .put("serverUuid", payload.optString("serverUuid", ""))
            .put("amount", amount)
            .put("createdAt", Instant.now().toString());
    }

    private void loadDebitResults() {
        if (!debitResultCacheFile.isFile()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(debitResultCacheFile.toPath(), StandardCharsets.UTF_8));
            for (String key : root.keySet()) {
                JSONObject value = root.optJSONObject(key);
                if (value != null && debitResultsByIdempotencyKey.size() < MAX_DEBIT_RESULT_CACHE_SIZE) {
                    debitResultsByIdempotencyKey.put(key, new JSONObject(value.toString()));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read wallet debit idempotency cache: " + e.getMessage());
        }
    }

    private void persistDebitResults() {
        try {
            File parent = debitResultCacheFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            JSONObject root = new JSONObject();
            for (Map.Entry<String, JSONObject> entry : debitResultsByIdempotencyKey.entrySet()) {
                root.put(entry.getKey(), entry.getValue());
            }
            File tempFile = new File(parent == null ? plugin.getDataFolder() : parent, debitResultCacheFile.getName() + ".tmp");
            Files.writeString(tempFile.toPath(), root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(
                    tempFile.toPath(),
                    debitResultCacheFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile.toPath(), debitResultCacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write wallet debit idempotency cache: " + e.getMessage());
        }
    }

    private void handleAppChat(String replyTo, JSONObject payload) {
        String senderUuid = payload.optString("senderServerUuid");
        String senderGameId = payload.optString("senderGameId");
        String content = payload.optString("content");
        boolean allowVirtualSender = payload.optBoolean("allowVirtualSender", false);
        if (!allowVirtualSender) {
            OfflinePlayer sender = offlineByUuid(senderUuid);
            if (sender == null || (!sender.hasPlayedBefore() && plugin.findOnlinePlayerExact(senderGameId).isEmpty())) {
                sendResult("chat.appMessage.result", replyTo, new JSONObject().put("status", "sender_not_found"));
                return;
            }
        }
        String formatted = plugin.chatBroadcastFormat()
            .replace("%player%", senderGameId)
            .replace("%message%", content);
        Bukkit.broadcastMessage(formatted);
        sendResult("chat.appMessage.result", replyTo, new JSONObject().put("status", "sent"));
    }

    private void handlePresenceList(String replyTo) {
        JSONArray players = new JSONArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.put(new JSONObject()
                .put("serverUuid", player.getUniqueId().toString())
                .put("currentGameId", player.getName())
                .put("onlineSince", Instant.now().toString()));
        }
        sendResult("presence.list.result", replyTo, new JSONObject()
            .put("status", "success")
            .put("onlineCount", players.length())
            .put("players", players));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offlineByUuid(String uuid) {
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String money(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}

