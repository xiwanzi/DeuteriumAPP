package com.deuterium.plugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeuteriumBridgePlugin extends JavaPlugin implements Listener {
    private static final String APP_CHAT_BROADCAST_FORMAT = "\u00A7x\u00A7b\u00A71\u00A7f\u00A77\u00A7f\u00A7f%player% \u00A77: \u00A7f%message%";
    private static final BigDecimal PAY_CONFIRM_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal BALANCE_CHANGE_TOLERANCE = new BigDecimal("0.01");

    private Economy economy;
    private BridgeClient bridgeClient;
    private boolean walletAvailable;
    private final Map<UUID, BigDecimal> walletBalanceSnapshots = new HashMap<>();
    private int offlineScanCursor = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        walletAvailable = setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, this);
        connectBridge();
        startWalletMonitor();
    }

    @Override
    public void onDisable() {
        if (bridgeClient != null) {
            bridgeClient.shutdown();
        }
    }

    public Economy economy() {
        return economy;
    }

    public boolean walletAvailable() {
        return walletAvailable;
    }

    public BridgeClient bridgeClient() {
        return bridgeClient;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault is missing; wallet bridge capability is disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Vault economy provider found; install and configure XConomy.");
            return false;
        }
        economy = rsp.getProvider();
        getLogger().info("Vault economy provider: " + economy.getName());
        return true;
    }

    private void connectBridge() {
        YamlConfiguration bundledConfig = loadBundledConfig();
        String bundledUrl = bundledConfig.getString("backend.ws-url", "");
        String bundledToken = bundledConfig.getString("backend.token", "");
        String configuredToken = getConfig().getString("backend.token", "");

        String url = bundledUrl == null || bundledUrl.isBlank()
            ? getConfig().getString("backend.ws-url", "ws://127.0.0.1:28658/bridge/plugin/ws")
            : bundledUrl;
        String token = isRealToken(bundledToken) ? bundledToken : configuredToken;
        int reconnectSeconds = Math.max(1, getConfig().getInt("backend.reconnect-seconds", 5));
        if (token == null || token.isBlank() || token.startsWith("CHANGE_ME")) {
            getLogger().severe("backend.token is not configured; plugin bridge will not connect.");
            return;
        }
        if (isRealToken(bundledToken) && configuredToken != null && !configuredToken.equals(bundledToken)) {
            getLogger().warning("Using bridge credentials bundled in this jar; existing plugin config backend.token is ignored.");
        }
        bridgeClient = new BridgeClient(this, URI.create(url), token, reconnectSeconds);
        bridgeClient.start();
    }

    private YamlConfiguration loadBundledConfig() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().warning("Failed to read bundled config.yml: " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    private boolean isRealToken(String token) {
        return token != null && !token.isBlank() && !token.startsWith("CHANGE_ME");
    }

    public Optional<Player> findOnlinePlayerExact(String gameId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(gameId)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("deprecation")
    public Optional<ResolvedPlayer> resolvePlayer(String gameId) {
        Optional<Player> online = findOnlinePlayerExact(gameId);
        if (online.isPresent()) {
            Player player = online.get();
            return Optional.of(new ResolvedPlayer(player.getUniqueId().toString(), player.getName(), true, player));
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(gameId);
        boolean knownByServer = offline.hasPlayedBefore();
        boolean knownByEconomy = walletAvailable && economy != null && economy.hasAccount(offline);
        if (!knownByServer && !knownByEconomy) {
            return Optional.empty();
        }
        String name = offline.getName() == null || offline.getName().isBlank() ? gameId : offline.getName();
        return Optional.of(new ResolvedPlayer(offline.getUniqueId().toString(), name, false, offline));
    }

    public String chatBroadcastFormat() {
        return APP_CHAT_BROADCAST_FORMAT;
    }

    public void rememberWalletBalance(OfflinePlayer player, double balance) {
        walletBalanceSnapshots.put(player.getUniqueId(), money(balance));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (bridgeClient != null) {
            bridgeClient.sendServerChat(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!walletAvailable || economy == null || bridgeClient == null) {
            return;
        }
        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        if (parts.length < 3 || !isPayCommand(parts[0])) {
            return;
        }
        BigDecimal amount = parsePayAmount(parts[2]);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Optional<ResolvedPlayer> recipient = resolvePlayer(parts[1]);
        if (recipient.isEmpty()) {
            return;
        }
        Player sender = event.getPlayer();
        OfflinePlayer recipientOffline = recipient.get().offlinePlayer();
        if (sender.getUniqueId().equals(recipientOffline.getUniqueId())) {
            return;
        }
        if (!economy.hasAccount(sender) || !economy.hasAccount(recipientOffline)) {
            return;
        }
        double senderBefore = economy.getBalance(sender);
        double recipientBefore = economy.getBalance(recipientOffline);
        String recipientName = recipient.get().currentGameId();
        Bukkit.getScheduler().runTaskLater(this, () ->
            confirmPayCommand(sender.getUniqueId(), sender.getName(), recipientOffline, recipientName, amount, senderBefore, recipientBefore),
            1L
        );
    }

    private void confirmPayCommand(
        UUID senderUuid,
        String senderName,
        OfflinePlayer recipient,
        String recipientName,
        BigDecimal amount,
        double senderBefore,
        double recipientBefore
    ) {
        if (!walletAvailable || economy == null || bridgeClient == null) {
            return;
        }
        OfflinePlayer sender = Bukkit.getOfflinePlayer(senderUuid);
        double senderAfter = economy.getBalance(sender);
        double recipientAfter = economy.getBalance(recipient);
        BigDecimal senderDelta = money(senderBefore - senderAfter);
        BigDecimal recipientDelta = money(recipientAfter - recipientBefore);
        if (!approximatelyEquals(senderDelta, amount) || !approximatelyEquals(recipientDelta, amount)) {
            return;
        }
        boolean sent = bridgeClient.sendWalletPayEvent(
            "mc_pay_" + UUID.randomUUID().toString().replace("-", ""),
            sender.getUniqueId().toString(),
            senderName,
            recipient.getUniqueId().toString(),
            recipientName,
            amount,
            senderAfter,
            recipientAfter
        );
        if (sent) {
            rememberWalletBalance(sender, senderAfter);
            rememberWalletBalance(recipient, recipientAfter);
        }
    }

    private boolean isPayCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return "pay".equals(normalized);
    }

    private BigDecimal parsePayAmount(String raw) {
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal money(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean approximatelyEquals(BigDecimal actual, BigDecimal expected) {
        return actual.subtract(expected).abs().compareTo(PAY_CONFIRM_TOLERANCE) <= 0;
    }

    private void startWalletMonitor() {
        if (!walletAvailable || economy == null || !getConfig().getBoolean("wallet-monitor.enabled", true)) {
            return;
        }
        long onlinePeriod = Math.max(5, getConfig().getInt("wallet-monitor.online-poll-seconds", 15)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::scanOnlineWalletBalances, onlinePeriod, onlinePeriod);
        long offlinePeriod = Math.max(60, getConfig().getInt("wallet-monitor.offline-poll-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::scanOfflineWalletBalances, offlinePeriod, offlinePeriod);
    }

    private void scanOnlineWalletBalances() {
        if (!canReportWalletChange()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            observeWalletBalance(player, player.getName(), "server_economy", "服务器经济变动");
        }
    }

    private void scanOfflineWalletBalances() {
        if (!canReportWalletChange()) {
            return;
        }
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        if (players.length == 0) {
            return;
        }
        int maxPerCycle = Math.max(1, getConfig().getInt("wallet-monitor.max-offline-players-per-cycle", 100));
        for (int scanned = 0; scanned < maxPerCycle && scanned < players.length; scanned++) {
            if (offlineScanCursor >= players.length) {
                offlineScanCursor = 0;
            }
            OfflinePlayer player = players[offlineScanCursor++];
            String name = player.getName() == null || player.getName().isBlank()
                ? player.getUniqueId().toString().substring(0, 8)
                : player.getName();
            observeWalletBalance(player, name, "server_economy", "服务器经济变动");
        }
    }

    private boolean canReportWalletChange() {
        return walletAvailable && economy != null && bridgeClient != null && bridgeClient.isOpen();
    }

    private void observeWalletBalance(OfflinePlayer player, String gameId, String source, String note) {
        if (!economy.hasAccount(player)) {
            return;
        }
        BigDecimal current = money(economy.getBalance(player));
        BigDecimal previous = walletBalanceSnapshots.get(player.getUniqueId());
        if (previous == null) {
            walletBalanceSnapshots.put(player.getUniqueId(), current);
            return;
        }
        BigDecimal diff = current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        if (diff.abs().compareTo(BALANCE_CHANGE_TOLERANCE) <= 0) {
            walletBalanceSnapshots.put(player.getUniqueId(), current);
            return;
        }
        String direction = diff.signum() > 0 ? "income" : "expense";
        boolean sent = bridgeClient.sendWalletBalanceChangeEvent(
            "mc_balance_" + UUID.randomUUID().toString().replace("-", ""),
            player.getUniqueId().toString(),
            gameId,
            direction,
            diff.abs(),
            current.doubleValue(),
            source,
            note
        );
        if (sent) {
            walletBalanceSnapshots.put(player.getUniqueId(), current);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (getConfig().getBoolean("events.death", true) && bridgeClient != null && event.getDeathMessage() != null) {
            bridgeClient.sendServerEvent("death", event.getDeathMessage());
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (!getConfig().getBoolean("events.server-say", true) || bridgeClient == null) {
            return;
        }
        String command = event.getCommand();
        if (command.toLowerCase(Locale.ROOT).startsWith("say ")) {
            bridgeClient.sendServerEvent("server_say", command.substring(4).trim());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("events.join", true) && bridgeClient != null && event.getJoinMessage() != null) {
            bridgeClient.sendServerEvent("join", event.getJoinMessage());
        }
        if (bridgeClient != null) {
            bridgeClient.sendPresenceSnapshot();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("events.quit", true) && bridgeClient != null && event.getQuitMessage() != null) {
            bridgeClient.sendServerEvent("quit", event.getQuitMessage());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (bridgeClient != null) {
                bridgeClient.sendPresenceSnapshot();
            }
        }, 1L);
    }

    public record ResolvedPlayer(String serverUuid, String currentGameId, boolean online, OfflinePlayer offlinePlayer) {
    }
}

