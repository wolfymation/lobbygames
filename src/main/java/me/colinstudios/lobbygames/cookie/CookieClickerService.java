package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

public final class CookieClickerService {
    public static final String GUI_TITLE = "Cookie Upgrades";
    public static final long MAX_COOKIES = 9_000_000_000_000_000L;
    public static final long SPECIAL_TNT_COST = 10_000_000L;
    public static final long COOKIE_RAIN_COST = 5_000_000L;
    public static final long COLOR_SNOWBALL_COST = 5_000_000L;
    private static final long RESET_INTERVAL_MILLIS = 3L * 24L * 60L * 60L * 1000L;
    private static final LocalTime RESET_TIME = LocalTime.of(20, 0);
    private static final String HOLOGRAM_TAG = "lobbygames_cookie_clicker_hologram";
    private static final String RESET_INFO_TAG = "lobbygames_cookie_reset_info";
    private static final String SHOP_TAG = "lobbygames_cookie_shop";
    private static final long STILL_CLICK_LIMIT_MILLIS = 10_000L;
    private static final double MIN_MOVEMENT_DISTANCE_SQUARED = 0.04D;
    private static final long CLICK_RATE_WINDOW_MILLIS = 1_000L;
    private static final int CLICK_RATE_LIMIT = 12;
    private static final long CLICK_SLOWDOWN_MILLIS = 2_000L;
    private static final int ANTICHEAT_NOTIFY_THRESHOLD = 3;
    private static final long ANTICHEAT_REPEAT_WINDOW_MILLIS = 60_000L;
    private static final long ANTICHEAT_NOTIFY_COOLDOWN_MILLIS = 30_000L;
    private static final long ANTICHEAT_LOG_COOLDOWN_MILLIS = 2_000L;
    private static final int ANTICHEAT_MAX_LOG_ENTRIES = 50;
    private static final DateTimeFormatter ANTICHEAT_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss");

    private final JavaPlugin plugin;
    private final CookieStorage storage;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.GERMANY);
    private final Random random = new Random();
    private final Map<UUID, TextDisplay> playerHolograms = new HashMap<>();
    private final Map<UUID, Long> lastMovementAt = new HashMap<>();
    private final Map<UUID, Location> lastMovementLocation = new HashMap<>();
    private final Map<UUID, Deque<Long>> recentCookieClicks = new HashMap<>();
    private final Map<UUID, Long> clickSlowdownUntil = new HashMap<>();
    private final Map<UUID, Deque<AntiCheatLogEntry>> antiCheatLogs = new HashMap<>();
    private final Map<String, Deque<Long>> recentAntiCheatDetections = new HashMap<>();
    private final Map<String, Long> antiCheatNotificationCooldownUntil = new HashMap<>();
    private final Map<String, Long> antiCheatLogCooldownUntil = new HashMap<>();
    private TextDisplay resetInfoDisplay;
    private TextDisplay shopDisplay;
    private BukkitTask hologramTask;
    private BukkitTask autoClickerTask;
    private BukkitTask leaderboardTask;
    private BukkitTask resetTask;
    private long nextAuxiliaryDisplayCleanupAt;

    public CookieClickerService(JavaPlugin plugin, CookieStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void start() {
        ensureDefaultConfig();
        for (Player player : Bukkit.getOnlinePlayers()) {
            registerPlayerActivity(player);
        }
        spawnOrRefreshHologram();
        hologramTask = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::updateHologramText, 20L * 5L, 20L * 5L);
        autoClickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAutoClickers, 20L, 20L);
        leaderboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateLeaderboard, 20L, 20L * 30L);
        resetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickResetTimer, 20L, 20L);
        spawnOrRefreshResetInfo();
        spawnOrRefreshShop();
    }

    public void stop() {
        if (hologramTask != null) {
            hologramTask.cancel();
        }
        if (autoClickerTask != null) {
            autoClickerTask.cancel();
        }
        if (leaderboardTask != null) {
            leaderboardTask.cancel();
        }
        if (resetTask != null) {
            resetTask.cancel();
        }
        removePlayerHolograms();
        lastMovementAt.clear();
        lastMovementLocation.clear();
        recentCookieClicks.clear();
        clickSlowdownUntil.clear();
        antiCheatLogs.clear();
        recentAntiCheatDetections.clear();
        antiCheatNotificationCooldownUntil.clear();
        antiCheatLogCooldownUntil.clear();
        if (resetInfoDisplay != null && !resetInfoDisplay.isDead()) {
            resetInfoDisplay.remove();
        }
        if (shopDisplay != null && !shopDisplay.isDead()) {
            shopDisplay.remove();
        }
        removeOldResetInfoDisplays(getResetInfoLocation());
        removeOldShopDisplays(getShopLocation());
    }

    public boolean isClickerBlock(Block block) {
        Location location = getClickerLocation();
        return location != null
            && block.getWorld().equals(location.getWorld())
            && block.getX() == location.getBlockX()
            && block.getY() == location.getBlockY()
            && block.getZ() == location.getBlockZ();
    }

    public boolean isProtectedBlock(Block block) {
        return isClickerBlock(block) || isShopBlock(block) || isLeaderboardBlock(block) || isResetInfoBlock(block);
    }

    public boolean isShopBlock(Block block) {
        return isSameBlock(block, getShopLocation());
    }

    public boolean isLeaderboardBlock(Block block) {
        for (int rank = 1; rank <= 10; rank++) {
            Location head = getLeaderboardLocation(rank, "head");
            Location sign = getLeaderboardLocation(rank, "sign");
            if (isSameBlock(block, head) || isSameBlock(block, sign)) {
                return true;
            }
        }
        return false;
    }

    public boolean isResetInfoBlock(Block block) {
        return isSameBlock(block, getResetInfoLocation());
    }

    public CookieClickResult click(org.bukkit.entity.Player player) {
        CookieAccount account = storage.getAccount(player);
        boolean critical = account.criticalChancePercent() > 0 && random.nextInt(100) < account.criticalChancePercent();
        long earned = account.cookiesPerClick() * (critical ? 2L : 1L);
        long total = storage.addCookies(player, earned);
        playClickEffects(player, critical);
        updateHologramText(player);
        return new CookieClickResult(earned, total, critical);
    }

    public Component getCookieClickBlockReason(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        Long lastMoveAt = lastMovementAt.computeIfAbsent(playerId, ignored -> now);
        if (now - lastMoveAt >= STILL_CLICK_LIMIT_MILLIS) {
            recentCookieClicks.remove(playerId);
            recordAntiCheatDetection(player, "AFK_FARMING", "Cookie-Klicks nach " + ((now - lastMoveAt) / 1000L) + "s ohne Bewegung blockiert.", now);
            return Component.text("Bewege dich kurz, um weiter Cookies zu sammeln.", NamedTextColor.RED);
        }

        Long slowdownUntil = clickSlowdownUntil.get(playerId);
        if (slowdownUntil != null) {
            long remaining = slowdownUntil - now;
            if (remaining > 0L) {
                long remainingSeconds = Math.max(1L, (long) Math.ceil(remaining / 1000.0D));
                return Component.text("Du klickst zu schnell. Warte " + remainingSeconds + "s.", NamedTextColor.RED);
            }
            clickSlowdownUntil.remove(playerId);
        }

        Deque<Long> clicks = recentCookieClicks.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        removeOldClicks(clicks, now);
        clicks.addLast(now);
        if (clicks.size() > CLICK_RATE_LIMIT) {
            clicks.clear();
            clickSlowdownUntil.put(playerId, now + CLICK_SLOWDOWN_MILLIS);
            recordAntiCheatDetection(player, "AUTOCLICKER", "Mehr als " + CLICK_RATE_LIMIT + " Cookie-Klicks in " + CLICK_RATE_WINDOW_MILLIS + "ms erkannt.", now);
            return Component.text("Autoclicker-Schutz: Du wurdest kurz gebremst.", NamedTextColor.RED);
        }

        return null;
    }

    public void registerPlayerActivity(Player player) {
        lastMovementAt.put(player.getUniqueId(), System.currentTimeMillis());
        lastMovementLocation.put(player.getUniqueId(), player.getLocation());
    }

    public void handlePlayerMove(Player player, Location from, Location to) {
        if (to == null) {
            return;
        }
        Location lastMovement = lastMovementLocation.get(player.getUniqueId());
        if (lastMovement == null
            || !lastMovement.getWorld().equals(to.getWorld())
            || lastMovement.distanceSquared(to) >= MIN_MOVEMENT_DISTANCE_SQUARED) {
            registerPlayerActivity(player);
        }
    }

    public void forgetPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        removePlayerHologram(player);
        lastMovementAt.remove(playerId);
        lastMovementLocation.remove(playerId);
        recentCookieClicks.remove(playerId);
        clickSlowdownUntil.remove(playerId);
    }

    public List<String> getAntiCheatScanLines(org.bukkit.OfflinePlayer player) {
        Deque<AntiCheatLogEntry> entries = antiCheatLogs.get(player.getUniqueId());
        if (entries == null || entries.isEmpty()) {
            return List.of("Keine AntiCheat-Einträge für " + displayName(player) + ".");
        }

        List<String> lines = new ArrayList<>();
        lines.add("AntiCheat-Einträge für " + displayName(player) + " (" + entries.size() + "):");
        for (AntiCheatLogEntry entry : entries) {
            String time = ANTICHEAT_TIME_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(entry.createdAtMillis()), ZoneId.systemDefault()));
            lines.add(time + " | " + entry.type() + " | " + entry.details());
        }
        return lines;
    }

    public CookieAccount getAccount(org.bukkit.OfflinePlayer player) {
        return storage.getAccount(player);
    }

    public boolean buyUpgrade(org.bukkit.entity.Player player, CookieUpgrade upgrade) {
        CookieAccount account = storage.getAccount(player);
        if (isMaxLevel(account, upgrade)) {
            return false;
        }
        boolean bought = storage.buyUpgrade(player, upgrade, upgradeCost(upgrade, account));
        if (bought) {
            playUpgradeEffects(player, upgrade);
            updateLeaderboard();
        }
        updateHologramText(player);
        return bought;
    }

    public boolean buySpecialTnt(org.bukkit.entity.Player player) {
        return buySpecialItem(player, SPECIAL_TNT_COST);
    }

    public boolean buyCookieRain(org.bukkit.entity.Player player) {
        return buySpecialItem(player, COOKIE_RAIN_COST);
    }

    public boolean buyColorSnowball(org.bukkit.entity.Player player) {
        return buySpecialItem(player, COLOR_SNOWBALL_COST);
    }

    private boolean buySpecialItem(org.bukkit.entity.Player player, long cost) {
        CookieAccount account = storage.getAccount(player);
        if (account.cookies() < cost) {
            return false;
        }
        storage.addCookies(player, -cost);
        updateHologramText(player);
        updateLeaderboard();
        return true;
    }

    public long upgradeCost(CookieUpgrade upgrade, CookieAccount account) {
        long next = upgrade.level(account) + 1L;
        long rawCost = Math.min(MAX_COOKIES, upgrade.baseCost() * next * next);
        long discount = rawCost * account.discountPercent() / 100L;
        return Math.max(1L, rawCost - discount);
    }

    public boolean isMaxLevel(CookieAccount account, CookieUpgrade upgrade) {
        return upgrade.level(account) >= upgrade.maxLevel();
    }

    public String format(long amount) {
        return numberFormat.format(amount);
    }

    public Location getClickerLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("cookie-clicker.world");
        World world = worldName == null ? Bukkit.getWorlds().getFirst() : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            config.getInt("cookie-clicker.x"),
            config.getInt("cookie-clicker.y"),
            config.getInt("cookie-clicker.z")
        );
    }

    public void setClickerLocation(Location location) {
        FileConfiguration config = plugin.getConfig();
        config.set("cookie-clicker.world", location.getWorld().getName());
        config.set("cookie-clicker.x", location.getBlockX());
        config.set("cookie-clicker.y", location.getBlockY());
        config.set("cookie-clicker.z", location.getBlockZ());
        plugin.saveConfig();
        spawnOrRefreshHologram();
    }

    public void setLeaderboardLocation(int rank, String type, Location location) {
        setLeaderboardLocation(rank, type, location, null);
    }

    public void setLeaderboardLocation(int rank, String type, Location location, BlockFace facing) {
        FileConfiguration config = plugin.getConfig();
        String path = "cookie-leaderboard." + rank + "." + type;
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getBlockX());
        config.set(path + ".y", location.getBlockY());
        config.set(path + ".z", location.getBlockZ());
        config.set(path + ".facing", facing == null ? null : facing.name().toLowerCase(Locale.ROOT));
        plugin.saveConfig();
        updateLeaderboard();
    }

    public void setResetInfoLocation(Location location) {
        FileConfiguration config = plugin.getConfig();
        config.set("cookie-reset-info.world", location.getWorld().getName());
        config.set("cookie-reset-info.x", location.getBlockX());
        config.set("cookie-reset-info.y", location.getBlockY());
        config.set("cookie-reset-info.z", location.getBlockZ());
        plugin.saveConfig();
        spawnOrRefreshResetInfo();
    }

    public void setShopLocation(Location location) {
        FileConfiguration config = plugin.getConfig();
        config.set("cookie-shop.world", location.getWorld().getName());
        config.set("cookie-shop.x", location.getBlockX());
        config.set("cookie-shop.y", location.getBlockY());
        config.set("cookie-shop.z", location.getBlockZ());
        plugin.saveConfig();
        spawnOrRefreshShop();
    }

    public Location getShopLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("cookie-shop.world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, config.getInt("cookie-shop.x"), config.getInt("cookie-shop.y"), config.getInt("cookie-shop.z"));
    }

    public Location getResetInfoLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("cookie-reset-info.world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, config.getInt("cookie-reset-info.x"), config.getInt("cookie-reset-info.y"), config.getInt("cookie-reset-info.z"));
    }

    public Location getLeaderboardLocation(int rank, String type) {
        FileConfiguration config = plugin.getConfig();
        String path = "cookie-leaderboard." + rank + "." + type;
        String worldName = config.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, config.getInt(path + ".x"), config.getInt(path + ".y"), config.getInt(path + ".z"));
    }

    public BlockFace getLeaderboardFacing(int rank, String type) {
        FileConfiguration config = plugin.getConfig();
        String path = "cookie-leaderboard." + rank + "." + type + ".facing";
        String value = config.getString(path);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "north" -> BlockFace.NORTH;
            case "east" -> BlockFace.EAST;
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            default -> null;
        };
    }

    public void spawnOrRefreshHologram() {
        Location blockLocation = getClickerLocation();
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }

        removeOldHolograms(blockLocation);
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnPlayerHologram(player);
        }
        updateHologramText();
    }

    public void spawnOrRefreshResetInfo() {
        Location blockLocation = getResetInfoLocation();
        if (resetInfoDisplay != null && !resetInfoDisplay.isDead()) {
            resetInfoDisplay.remove();
        }
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }

        removeOldResetInfoDisplays(blockLocation);
        Location displayLocation = blockLocation.toCenterLocation().add(0.0, 1.35, 0.0);
        resetInfoDisplay = (TextDisplay) blockLocation.getWorld().spawnEntity(displayLocation, EntityType.TEXT_DISPLAY);
        resetInfoDisplay.addScoreboardTag(RESET_INFO_TAG);
        resetInfoDisplay.setPersistent(false);
        resetInfoDisplay.setBillboard(Display.Billboard.CENTER);
        resetInfoDisplay.setSeeThrough(true);
        resetInfoDisplay.setShadowed(true);
        updateResetInfoText();
    }

    public void spawnOrRefreshShop() {
        Location blockLocation = getShopLocation();
        if (shopDisplay != null && !shopDisplay.isDead()) {
            shopDisplay.remove();
        }
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }

        removeOldShopDisplays(blockLocation);
        Location displayLocation = blockLocation.toCenterLocation().add(0.0, 1.35, 0.0);
        shopDisplay = (TextDisplay) blockLocation.getWorld().spawnEntity(displayLocation, EntityType.TEXT_DISPLAY);
        shopDisplay.addScoreboardTag(SHOP_TAG);
        shopDisplay.setPersistent(false);
        shopDisplay.setBillboard(Display.Billboard.CENTER);
        shopDisplay.setSeeThrough(true);
        shopDisplay.setShadowed(true);
        shopDisplay.text(Component.text("Cookie Shop", NamedTextColor.GOLD)
            .append(Component.newline())
            .append(Component.text("Rechtsklick", NamedTextColor.YELLOW)));
    }

    public void spawnPlayerHologram(Player player) {
        Location blockLocation = getClickerLocation();
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }

        removePlayerHologram(player);
        Location displayLocation = blockLocation.toCenterLocation().add(0.0, 1.35, 0.0);
        TextDisplay display = (TextDisplay) blockLocation.getWorld().spawnEntity(displayLocation, EntityType.TEXT_DISPLAY);
        display.addScoreboardTag(HOLOGRAM_TAG);
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.getUniqueId().equals(player.getUniqueId())) {
                onlinePlayer.hideEntity(plugin, display);
            }
        }
        playerHolograms.put(player.getUniqueId(), display);
        updateHologramText(player);
    }

    public void removePlayerHologram(Player player) {
        TextDisplay display = playerHolograms.remove(player.getUniqueId());
        if (display != null && !display.isDead()) {
            display.remove();
        }
    }

    public void hideOtherHolograms(Player player) {
        for (Map.Entry<UUID, TextDisplay> entry : playerHolograms.entrySet()) {
            if (!entry.getKey().equals(player.getUniqueId()) && !entry.getValue().isDead()) {
                player.hideEntity(plugin, entry.getValue());
            }
        }
    }

    private void updateHologramText() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateHologramText(player);
        }
    }

    private void updateHologramText(Player player) {
        TextDisplay display = playerHolograms.get(player.getUniqueId());
        if (display == null || display.isDead()) {
            spawnPlayerHologram(player);
            return;
        }
        CookieAccount account = storage.getAccount(player);
        display.text(Component.text("Deine Cookies: ", NamedTextColor.GOLD)
            .append(Component.text(format(account.cookies()), NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Hau", NamedTextColor.YELLOW))
            .append(Component.text(" = ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Cookie ", NamedTextColor.GOLD))
            .append(Component.text("| ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Rechtsklick = Upgrade", NamedTextColor.GRAY)));
    }

    private void tickAutoClickers() {
        for (UUID playerId : storage.addOnlineAutoClickerCookies(Bukkit.getOnlinePlayers())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updateHologramText(player);
            }
        }
    }

    private void tickResetTimer() {
        ensureAuxiliaryDisplays();
        ensureNextResetAt();
        long now = System.currentTimeMillis();
        long nextResetAt = plugin.getConfig().getLong("cookie-reset.next-reset-at");
        if (now >= nextResetAt) {
            int resetAccounts = storage.resetAllAccounts();
            plugin.getConfig().set("cookie-reset.next-reset-at", nextScheduledResetAfter(now, nextResetAt));
            plugin.saveConfig();
            Bukkit.broadcast(Component.text("CookieClicker wurde zurückgesetzt. Neuer Wettbewerb gestartet.", NamedTextColor.GOLD));
            plugin.getLogger().info("CookieClicker reset completed for " + resetAccounts + " accounts.");
            updateHologramText();
            updateLeaderboard();
        }
        updateResetInfoText();
    }

    private void updateResetInfoText() {
        if (resetInfoDisplay == null || resetInfoDisplay.isDead()) {
            return;
        }
        ensureNextResetAt();
        long remaining = Math.max(0L, plugin.getConfig().getLong("cookie-reset.next-reset-at") - System.currentTimeMillis());
        resetInfoDisplay.text(Component.text("Nächster Cookie-Reset", NamedTextColor.GOLD)
            .append(Component.newline())
            .append(Component.text(formatDuration(remaining), NamedTextColor.YELLOW))
            .append(Component.newline())
            .append(Component.text("Alle 3 Tage um 20:00 Uhr", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Reset: Cookies & Upgrades", NamedTextColor.DARK_GREEN)));
    }

    private void ensureAuxiliaryDisplays() {
        if (resetInfoDisplay == null || resetInfoDisplay.isDead()) {
            spawnOrRefreshResetInfo();
        }
        if (shopDisplay == null || shopDisplay.isDead()) {
            spawnOrRefreshShop();
        }

        long now = System.currentTimeMillis();
        if (now >= nextAuxiliaryDisplayCleanupAt) {
            nextAuxiliaryDisplayCleanupAt = now + 60_000L;
            removeDuplicateResetInfoDisplays();
            removeDuplicateShopDisplays();
        }
    }

    public void updateLeaderboard() {
        List<CookieLeaderboardEntry> entries = storage.getTopAccounts(10);
        for (int rank = 1; rank <= 10; rank++) {
            CookieLeaderboardEntry entry = rank <= entries.size() ? entries.get(rank - 1) : null;
            updateLeaderboardHead(rank, entry);
            updateLeaderboardSign(rank, entry);
        }
    }

    private void updateLeaderboardHead(int rank, CookieLeaderboardEntry entry) {
        Location location = getLeaderboardLocation(rank, "head");
        if (location == null || !location.isWorldLoaded()) {
            return;
        }
        Block block = location.getBlock();
        BlockFace facing = getLeaderboardFacing(rank, "head");
        if (entry == null) {
            block.setType(facing == null ? Material.SKELETON_SKULL : Material.SKELETON_WALL_SKULL);
            applyFacing(block, facing);
            return;
        }

        block.setType(facing == null ? Material.PLAYER_HEAD : Material.PLAYER_WALL_HEAD);
        applyFacing(block, facing);
        if (block.getState() instanceof Skull skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
            skull.update(true, false);
        }
    }

    private void updateLeaderboardSign(int rank, CookieLeaderboardEntry entry) {
        Location location = getLeaderboardLocation(rank, "sign");
        if (location == null || !location.isWorldLoaded()) {
            return;
        }
        Block block = location.getBlock();
        BlockFace facing = getLeaderboardFacing(rank, "sign");
        if (!(block.getState() instanceof Sign) || (facing != null && !(block.getBlockData() instanceof Directional))) {
            block.setType(facing == null ? Material.OAK_SIGN : Material.OAK_WALL_SIGN);
        }
        applyFacing(block, facing);
        if (block.getState() instanceof Sign sign) {
            if (entry == null) {
                sign.line(0, Component.text("Platz " + rank, rankColor(rank)));
                sign.line(1, Component.text("Leer", NamedTextColor.GRAY));
                sign.line(2, Component.text("0", NamedTextColor.GOLD));
                sign.line(3, Component.text("0 pro Klick", NamedTextColor.GRAY));
            } else {
                CookieAccount account = storage.getAccount(Bukkit.getOfflinePlayer(entry.uuid()));
                sign.line(0, Component.text("Platz " + rank, rankColor(rank)));
                sign.line(1, Component.text(entry.name(), NamedTextColor.WHITE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                sign.line(2, Component.text(format(entry.cookies()), NamedTextColor.GOLD));
                sign.line(3, Component.text(format(account.cookiesPerClick()) + " pro Klick", NamedTextColor.GRAY));
            }
            sign.update(true, false);
        }
    }

    private NamedTextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
    }

    private void applyFacing(Block block, BlockFace facing) {
        if (facing == null || !(block.getBlockData() instanceof Directional directional)) {
            return;
        }
        directional.setFacing(facing);
        block.setBlockData(directional, false);
    }

    private void playClickEffects(org.bukkit.entity.Player player, boolean critical) {
        Location location = getClickerLocation();
        if (location == null) {
            return;
        }
        Location center = location.toCenterLocation().add(0.0, 0.65, 0.0);
        World world = center.getWorld();
        world.spawnParticle(Particle.ITEM, center, critical ? 18 : 7, 0.25, 0.25, 0.25, 0.02, new org.bukkit.inventory.ItemStack(org.bukkit.Material.COOKIE));
        world.spawnParticle(critical ? Particle.CRIT : Particle.HAPPY_VILLAGER, center, critical ? 12 : 3, 0.25, 0.25, 0.25, 0.01);
        player.playSound(player.getLocation(), critical ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_ITEM_PICKUP, 0.55f, critical ? 1.2f : 1.7f);
    }

    private void playUpgradeEffects(org.bukkit.entity.Player player, CookieUpgrade upgrade) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f + (upgrade.ordinal() * 0.1f));
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0), 24, 0.35, 0.45, 0.35, 0.05);
    }

    private void removeOldHolograms(Location blockLocation) {
        removePlayerHolograms();
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.toCenterLocation(), 3.0, 3.0, 3.0)) {
            if (entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeOldResetInfoDisplays(Location blockLocation) {
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.toCenterLocation(), 3.0, 3.0, 3.0)) {
            if (entity.getScoreboardTags().contains(RESET_INFO_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeOldShopDisplays(Location blockLocation) {
        if (blockLocation == null || !blockLocation.isWorldLoaded()) {
            return;
        }
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.toCenterLocation(), 3.0, 3.0, 3.0)) {
            if (entity.getScoreboardTags().contains(SHOP_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeDuplicateResetInfoDisplays() {
        removeDuplicateDisplays(getResetInfoLocation(), RESET_INFO_TAG, resetInfoDisplay);
    }

    private void removeDuplicateShopDisplays() {
        removeDuplicateDisplays(getShopLocation(), SHOP_TAG, shopDisplay);
    }

    private void removeDuplicateDisplays(Location blockLocation, String tag, TextDisplay currentDisplay) {
        if (blockLocation == null || !blockLocation.isWorldLoaded() || currentDisplay == null) {
            return;
        }
        for (Entity entity : blockLocation.getWorld().getNearbyEntities(blockLocation.toCenterLocation(), 3.0, 3.0, 3.0)) {
            if (!entity.getUniqueId().equals(currentDisplay.getUniqueId()) && entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private void removePlayerHolograms() {
        for (TextDisplay display : playerHolograms.values()) {
            if (!display.isDead()) {
                display.remove();
            }
        }
        playerHolograms.clear();
    }

    private void ensureDefaultConfig() {
        FileConfiguration config = plugin.getConfig();
        config.addDefault("cookie-clicker.world", Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().getFirst().getName());
        config.addDefault("cookie-clicker.x", 63);
        config.addDefault("cookie-clicker.y", -59);
        config.addDefault("cookie-clicker.z", 216);
        ensureNextResetAt();
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private void ensureNextResetAt() {
        FileConfiguration config = plugin.getConfig();
        long nextResetAt = config.getLong("cookie-reset.next-reset-at");
        if (nextResetAt <= 0L || !isResetAtConfiguredTime(nextResetAt)) {
            config.set("cookie-reset.next-reset-at", firstScheduledResetAfter(System.currentTimeMillis()));
            plugin.saveConfig();
        }
    }

    private long firstScheduledResetAfter(long nowMillis) {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault());
        ZonedDateTime next = now.with(RESET_TIME).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.toInstant().toEpochMilli();
    }

    private long nextScheduledResetAfter(long nowMillis, long previousResetAtMillis) {
        ZonedDateTime next = ZonedDateTime.ofInstant(Instant.ofEpochMilli(previousResetAtMillis), ZoneId.systemDefault())
            .with(RESET_TIME)
            .withSecond(0)
            .withNano(0)
            .plusDays(3);
        while (next.toInstant().toEpochMilli() <= nowMillis) {
            next = next.plusDays(3);
        }
        return next.toInstant().toEpochMilli();
    }

    private boolean isResetAtConfiguredTime(long millis) {
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return dateTime.toLocalTime().withSecond(0).withNano(0).equals(RESET_TIME);
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }

    private void removeOldClicks(Deque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > CLICK_RATE_WINDOW_MILLIS) {
            clicks.removeFirst();
        }
    }

    private void recordAntiCheatDetection(Player player, String type, String details, long now) {
        String key = player.getUniqueId() + ":" + type;
        Long nextLogAt = antiCheatLogCooldownUntil.get(key);
        if (nextLogAt != null && now < nextLogAt) {
            return;
        }
        antiCheatLogCooldownUntil.put(key, now + ANTICHEAT_LOG_COOLDOWN_MILLIS);

        Deque<AntiCheatLogEntry> log = antiCheatLogs.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        log.addLast(new AntiCheatLogEntry(now, type, details));
        while (log.size() > ANTICHEAT_MAX_LOG_ENTRIES) {
            log.removeFirst();
        }

        Deque<Long> detections = recentAntiCheatDetections.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!detections.isEmpty() && now - detections.peekFirst() > ANTICHEAT_REPEAT_WINDOW_MILLIS) {
            detections.removeFirst();
        }
        detections.addLast(now);
        if (detections.size() >= ANTICHEAT_NOTIFY_THRESHOLD) {
            notifyAntiCheatRepeated(player, type, details, detections.size(), now);
        }
    }

    private void notifyAntiCheatRepeated(Player player, String type, String details, int detections, long now) {
        String key = player.getUniqueId() + ":" + type;
        Long cooldownUntil = antiCheatNotificationCooldownUntil.get(key);
        if (cooldownUntil != null && now < cooldownUntil) {
            return;
        }
        antiCheatNotificationCooldownUntil.put(key, now + ANTICHEAT_NOTIFY_COOLDOWN_MILLIS);

        String message = "[CookieAntiCheat] " + player.getName() + " mehrfach erkannt: " + type + " (" + detections + "x/60s). " + details;
        plugin.getLogger().warning(message);
        Component adminMessage = Component.text(message, NamedTextColor.RED);
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("lobbygames.cookieadmin")) {
                admin.sendMessage(adminMessage);
            }
        }
    }

    private String displayName(org.bukkit.OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private boolean isSameBlock(Block block, Location location) {
        return location != null
            && block.getWorld().equals(location.getWorld())
            && block.getX() == location.getBlockX()
            && block.getY() == location.getBlockY()
            && block.getZ() == location.getBlockZ();
    }

    public record AntiCheatLogEntry(long createdAtMillis, String type, String details) {
    }
}
