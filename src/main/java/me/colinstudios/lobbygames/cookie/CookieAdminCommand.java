package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CookieAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ADMIN_COMMANDS = List.of(
        "set", "info", "add", "remove", "reset", "specialitem", "reloadhologram", "reloadleaderboard"
    );
    private static final List<String> SET_COMMANDS = List.of("clicker", "resetinfo", "shop", "place", "cookies");
    private static final List<String> PLACE_TYPES = List.of("head", "sign");
    private static final List<String> PLACE_RANKS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");

    private final JavaPlugin plugin;
    private final CookieClickerService service;
    private final CookieStorage storage;
    private final CookieSpecialItemService specialItemService;

    public CookieAdminCommand(JavaPlugin plugin, CookieClickerService service, CookieStorage storage, CookieSpecialItemService specialItemService) {
        this.plugin = plugin;
        this.service = service;
        this.storage = storage;
        this.specialItemService = specialItemService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lobbygames.cookieadmin")) {
            sender.sendMessage(Component.text("Keine Rechte."));
            return true;
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("admin")) {
            sendUsage(sender);
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        switch (args[1].toLowerCase(LocaleRoot.VALUE)) {
            case "set" -> handleSet(sender, args);
            case "info" -> showInfo(sender, args);
            case "add" -> updateCookies(sender, args, UpdateMode.ADD, 2, "/cookie admin add <spieler> <anzahl>");
            case "remove" -> updateCookies(sender, args, UpdateMode.REMOVE, 2, "/cookie admin remove <spieler> <anzahl>");
            case "reset" -> reset(sender, args);
            case "specialitem" -> giveSpecialItem(sender, args);
            case "reloadhologram" -> {
                service.spawnOrRefreshHologram();
                sender.sendMessage(Component.text("Cookie-Hologramm neu geladen."));
            }
            case "reloadleaderboard" -> {
                service.updateLeaderboard();
                sender.sendMessage(Component.text("Cookie-Leaderboard neu geladen."));
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendSetUsage(sender);
            return;
        }

        switch (args[2].toLowerCase(LocaleRoot.VALUE)) {
            case "clicker" -> setClicker(sender);
            case "resetinfo" -> setResetInfo(sender);
            case "shop" -> setShop(sender);
            case "place" -> setLeaderboardPlace(sender, args);
            case "cookies" -> updateCookies(sender, args, UpdateMode.SET, 3, "/cookie admin set cookies <spieler> <anzahl>");
            default -> sendSetUsage(sender);
        }
    }

    private void setClicker(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Position setzen."));
            return;
        }
        Location target = getTargetLocation(player);
        service.setClickerLocation(target);
        sender.sendMessage(Component.text("CookieClicker-Block gesetzt auf " + formatLocation(target) + "."));
    }

    private void setResetInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Reset-Info-Position setzen."));
            return;
        }
        Location target = getTargetLocation(player);
        service.setResetInfoLocation(target);
        sender.sendMessage(Component.text("Reset-Info gesetzt auf " + formatLocation(target) + "."));
    }

    private void setShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Shop-Position setzen."));
            return;
        }
        Location target = getTargetLocation(player);
        service.setShopLocation(target);
        sender.sendMessage(Component.text("Cookie-Shop gesetzt auf " + formatLocation(target) + "."));
    }

    private void setLeaderboardPlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen Leaderboard-Positionen setzen."));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text("Nutzung: /cookie admin set place <1-10> <head|sign> [facing]"));
            return;
        }

        Integer rank = parseRank(args[3]);
        if (rank == null) {
            sender.sendMessage(Component.text("Der Platz muss zwischen 1 und 10 liegen."));
            return;
        }

        String type = args[4].toLowerCase(LocaleRoot.VALUE);
        if (!PLACE_TYPES.contains(type)) {
            sender.sendMessage(Component.text("Der Typ muss head oder sign sein."));
            return;
        }

        BlockFace facing = null;
        if (args.length >= 6) {
            facing = parseDirection(args[5]);
            if (facing == null) {
                sender.sendMessage(Component.text("Facing muss north, east, south oder west sein."));
                return;
            }
        } else if (type.equals("head")) {
            sender.sendMessage(Component.text("Nutzung: /cookie admin set place <1-10> head <facing:north|east|south|west>"));
            return;
        }

        Location target = getTargetLocation(player);
        service.setLeaderboardLocation(rank, type, target, facing);
        String facingText = facing == null ? "" : " facing " + facing.name().toLowerCase(LocaleRoot.VALUE);
        sender.sendMessage(Component.text("Leaderboard Platz " + rank + " " + type + facingText + " gesetzt auf " + formatLocation(target) + "."));
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Nutzung: /cookie admin info <spieler>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        CookieAccount account = storage.getAccount(target);
        sender.sendMessage(Component.text(target.getName() + ": "
            + service.format(account.cookies()) + " Cookies, Klick "
            + account.clickPowerLevel() + ", Auto " + account.autoClickerLevel()
            + ", Ofen " + account.ovenLevel() + ", Glueck " + account.fortuneLevel()
            + ", Rabatt " + account.discountLevel()));
    }

    private void updateCookies(CommandSender sender, String[] args, UpdateMode mode, int playerIndex, String usage) {
        if (args.length <= playerIndex + 1) {
            sender.sendMessage(Component.text("Nutzung: " + usage));
            return;
        }

        Long amount = parseAmount(args[playerIndex + 1]);
        if (amount == null) {
            sender.sendMessage(Component.text("Die Anzahl muss eine ganze Zahl sein."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[playerIndex]);
        long newBalance = switch (mode) {
            case SET -> storage.setCookies(target, amount);
            case ADD -> storage.addCookies(target, amount);
            case REMOVE -> storage.addCookies(target, -amount);
        };
        service.spawnOrRefreshHologram();
        service.updateLeaderboard();
        sender.sendMessage(Component.text(target.getName() + " hat jetzt " + service.format(newBalance) + " Cookies."));
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Nutzung: /cookie admin reset <spieler>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        storage.resetAccount(target);
        service.spawnOrRefreshHologram();
        service.updateLeaderboard();
        sender.sendMessage(Component.text(target.getName() + " wurde zurueckgesetzt."));
    }

    private void giveSpecialItem(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Nutzung: /cookie admin specialitem <item> <spieler>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            sender.sendMessage(Component.text("Der Spieler muss online sein."));
            return;
        }

        org.bukkit.inventory.ItemStack item = specialItemService.createSpecialItem(args[2]);
        if (item == null) {
            sender.sendMessage(Component.text("Unbekanntes SpecialItem. Verfuegbar: tnt, cookieregen, farbschneeball"));
            return;
        }

        target.getInventory().addItem(item).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(Component.text(target.getName() + " hat das SpecialItem " + args[2].toLowerCase(LocaleRoot.VALUE) + " erhalten."));
    }

    private void sendUsage(CommandSender sender) {
        Location location = service.getClickerLocation();
        sender.sendMessage(Component.text("/cookie admin set clicker"));
        sender.sendMessage(Component.text("/cookie admin set resetinfo"));
        sender.sendMessage(Component.text("/cookie admin set shop"));
        sender.sendMessage(Component.text("/cookie admin set place <1-10> head <facing:north|east|south|west>"));
        sender.sendMessage(Component.text("/cookie admin set place <1-10> sign [facing]"));
        sender.sendMessage(Component.text("/cookie admin info <spieler>"));
        sender.sendMessage(Component.text("/cookie admin set cookies <spieler> <anzahl>"));
        sender.sendMessage(Component.text("/cookie admin add|remove <spieler> <anzahl>"));
        sender.sendMessage(Component.text("/cookie admin reset <spieler>"));
        sender.sendMessage(Component.text("/cookie admin specialitem <item> <spieler>"));
        sender.sendMessage(Component.text("/cookie admin reloadhologram"));
        sender.sendMessage(Component.text("/cookie admin reloadleaderboard"));
        sender.sendMessage(Component.text("Aktueller Block: " + (location == null ? "ungueltig" : formatLocation(location))));
    }

    private void sendSetUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Nutzung: /cookie admin set <clicker|resetinfo|shop|place|cookies>"));
    }

    private Location getTargetLocation(Player player) {
        return player.getTargetBlockExact(8) == null
            ? player.getLocation().getBlock().getLocation()
            : player.getTargetBlockExact(8).getLocation();
    }

    private Long parseAmount(String value) {
        try {
            long amount = Long.parseLong(value);
            return amount < 0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseRank(String value) {
        try {
            int rank = Integer.parseInt(value);
            return rank >= 1 && rank <= 10 ? rank : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BlockFace parseDirection(String value) {
        return switch (value.toLowerCase(LocaleRoot.VALUE)) {
            case "north" -> BlockFace.NORTH;
            case "east" -> BlockFace.EAST;
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            default -> null;
        };
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lobbygames.cookieadmin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(ADMIN_COMMANDS, args[1]);
        }
        if (args.length == 3 && isAdminSet(args)) {
            return filter(SET_COMMANDS, args[2]);
        }
        if (args.length == 4 && isAdminSetPlace(args)) {
            return filter(PLACE_RANKS, args[3]);
        }
        if (args.length == 5 && isAdminSetPlace(args)) {
            return filter(PLACE_TYPES, args[4]);
        }
        if (args.length == 6 && isAdminSetPlace(args) && PLACE_TYPES.contains(args[4].toLowerCase(LocaleRoot.VALUE))) {
            return filter(DIRECTIONS, args[5]);
        }
        if (args.length == 4 && isAdminSetCookies(args)) {
            return filter(Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).toList(), args[3]);
        }
        if (args.length == 3 && List.of("info", "add", "remove", "reset").contains(args[1].toLowerCase(LocaleRoot.VALUE))) {
            return filter(Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).toList(), args[2]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("specialitem")) {
            return filter(List.of("tnt", "cookieregen", "farbschneeball"), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("specialitem")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        if (args.length == 4 && List.of("add", "remove").contains(args[1].toLowerCase(LocaleRoot.VALUE))) {
            return filter(List.of("1", "10", "100", "1000"), args[3]);
        }
        if (args.length == 5 && isAdminSetCookies(args)) {
            return filter(List.of("1", "10", "100", "1000"), args[4]);
        }
        return Collections.emptyList();
    }

    private boolean isAdminSet(String[] args) {
        return args.length >= 2 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set");
    }

    private boolean isAdminSetPlace(String[] args) {
        return args.length >= 3 && isAdminSet(args) && args[2].equalsIgnoreCase("place");
    }

    private boolean isAdminSetCookies(String[] args) {
        return args.length >= 3 && isAdminSet(args) && args[2].equalsIgnoreCase("cookies");
    }

    private List<String> filter(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(LocaleRoot.VALUE);
        List<String> results = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(LocaleRoot.VALUE).startsWith(lowerPrefix)) {
                results.add(value);
            }
        }
        return results;
    }

    private enum UpdateMode {
        SET,
        ADD,
        REMOVE
    }

    private static final class LocaleRoot {
        private static final java.util.Locale VALUE = java.util.Locale.ROOT;
    }
}
