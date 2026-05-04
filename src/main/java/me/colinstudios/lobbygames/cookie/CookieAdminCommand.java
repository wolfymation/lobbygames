package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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
    private static final List<String> PLACE_COMMANDS = List.of(
        "setplaceone", "setplacetwo", "setplacethree", "setplacefour", "setplacefive",
        "setplacesix", "setplaceseven", "setplaceeight", "setplacenine", "setplaceten"
    );
    private static final List<String> SUB_COMMANDS = List.of(
        "setclicker", "setresetinfo", "setshop", "info", "set", "add", "remove", "reset", "specialitem", "reloadhologram", "reloadleaderboard",
        "setplaceone", "setplacetwo", "setplacethree", "setplacefour", "setplacefive",
        "setplacesix", "setplaceseven", "setplaceeight", "setplacenine", "setplaceten"
    );

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

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase(LocaleRoot.VALUE)) {
            case "setclicker" -> setClicker(sender);
            case "setresetinfo" -> setResetInfo(sender);
            case "setshop" -> setShop(sender);
            case "info" -> showInfo(sender, args);
            case "set" -> updateCookies(sender, args, UpdateMode.SET);
            case "add" -> updateCookies(sender, args, UpdateMode.ADD);
            case "remove" -> updateCookies(sender, args, UpdateMode.REMOVE);
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
            default -> {
                Integer rank = rankFromPlaceCommand(args[0]);
                if (rank == null) {
                    sendUsage(sender);
                } else {
                    setLeaderboardPlace(sender, args, rank);
                }
            }
        }
        return true;
    }

    private void setClicker(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Position setzen."));
            return;
        }
        Location target = player.getTargetBlockExact(8) == null
            ? player.getLocation().getBlock().getLocation()
            : player.getTargetBlockExact(8).getLocation();
        service.setClickerLocation(target);
        sender.sendMessage(Component.text("CookieClicker-Block gesetzt auf " + formatLocation(target) + "."));
    }

    private void setResetInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Reset-Info-Position setzen."));
            return;
        }
        Location target = player.getTargetBlockExact(8) == null
            ? player.getLocation().getBlock().getLocation()
            : player.getTargetBlockExact(8).getLocation();
        service.setResetInfoLocation(target);
        sender.sendMessage(Component.text("Reset-Info gesetzt auf " + formatLocation(target) + "."));
    }

    private void setShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen die Shop-Position setzen."));
            return;
        }
        Location target = player.getTargetBlockExact(8) == null
            ? player.getLocation().getBlock().getLocation()
            : player.getTargetBlockExact(8).getLocation();
        service.setShopLocation(target);
        sender.sendMessage(Component.text("Cookie-Shop gesetzt auf " + formatLocation(target) + "."));
    }

    private void setLeaderboardPlace(CommandSender sender, String[] args, int rank) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler koennen Leaderboard-Positionen setzen."));
            return;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("head") && !args[1].equalsIgnoreCase("sign"))) {
            sender.sendMessage(Component.text("Nutzung: /cookieadmin setplace" + rankName(rank) + " <head|sign>"));
            return;
        }

        Location target = player.getTargetBlockExact(8) == null
            ? player.getLocation().getBlock().getLocation()
            : player.getTargetBlockExact(8).getLocation();
        String type = args[1].toLowerCase(LocaleRoot.VALUE);
        service.setLeaderboardLocation(rank, type, target);
        sender.sendMessage(Component.text("Leaderboard Platz " + rank + " " + type + " gesetzt auf " + formatLocation(target) + "."));
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Nutzung: /cookieadmin info <spieler>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        CookieAccount account = storage.getAccount(target);
        sender.sendMessage(Component.text(target.getName() + ": "
            + service.format(account.cookies()) + " Cookies, Klick "
            + account.clickPowerLevel() + ", Auto " + account.autoClickerLevel()
            + ", Ofen " + account.ovenLevel() + ", Glueck " + account.fortuneLevel()
            + ", Rabatt " + account.discountLevel()));
    }

    private void updateCookies(CommandSender sender, String[] args, UpdateMode mode) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Nutzung: /cookieadmin " + mode.command + " <spieler> <anzahl>"));
            return;
        }

        Long amount = parseAmount(args[2]);
        if (amount == null) {
            sender.sendMessage(Component.text("Die Anzahl muss eine ganze Zahl sein."));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
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
        if (args.length < 2) {
            sender.sendMessage(Component.text("Nutzung: /cookieadmin reset <spieler>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        storage.resetAccount(target);
        service.spawnOrRefreshHologram();
        service.updateLeaderboard();
        sender.sendMessage(Component.text(target.getName() + " wurde zurueckgesetzt."));
    }

    private void giveSpecialItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Nutzung: /cookieadmin specialitem <item> <spieler>"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Der Spieler muss online sein."));
            return;
        }

        org.bukkit.inventory.ItemStack item = specialItemService.createSpecialItem(args[1]);
        if (item == null) {
            sender.sendMessage(Component.text("Unbekanntes SpecialItem. Verfügbar: tnt"));
            return;
        }

        target.getInventory().addItem(item).values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(Component.text(target.getName() + " hat das SpecialItem " + args[1].toLowerCase(LocaleRoot.VALUE) + " erhalten."));
    }

    private void sendUsage(CommandSender sender) {
        Location location = service.getClickerLocation();
        sender.sendMessage(Component.text("/cookieadmin setclicker"));
        sender.sendMessage(Component.text("/cookieadmin setresetinfo"));
        sender.sendMessage(Component.text("/cookieadmin setshop"));
        sender.sendMessage(Component.text("/cookieadmin info <spieler>"));
        sender.sendMessage(Component.text("/cookieadmin set|add|remove <spieler> <anzahl>"));
        sender.sendMessage(Component.text("/cookieadmin reset <spieler>"));
        sender.sendMessage(Component.text("/cookieadmin specialitem <item> <spieler>"));
        sender.sendMessage(Component.text("/cookieadmin reloadhologram"));
        sender.sendMessage(Component.text("/cookieadmin reloadleaderboard"));
        sender.sendMessage(Component.text("/cookieadmin setplaceone|...|setplaceten <head|sign>"));
        sender.sendMessage(Component.text("Aktueller Block: " + (location == null ? "ungueltig" : formatLocation(location))));
    }

    private Long parseAmount(String value) {
        try {
            long amount = Long.parseLong(value);
            return amount < 0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
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
            return filter(SUB_COMMANDS, args[0]);
        }
        if (args.length == 2 && List.of("info", "set", "add", "remove", "reset").contains(args[0].toLowerCase(LocaleRoot.VALUE))) {
            return filter(Arrays.stream(Bukkit.getOfflinePlayers()).map(OfflinePlayer::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("specialitem")) {
            return filter(List.of("tnt"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("specialitem")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && PLACE_COMMANDS.contains(args[0].toLowerCase(LocaleRoot.VALUE))) {
            return filter(List.of("head", "sign"), args[1]);
        }
        if (args.length == 3 && List.of("set", "add", "remove").contains(args[0].toLowerCase(LocaleRoot.VALUE))) {
            return filter(List.of("1", "10", "100", "1000"), args[2]);
        }
        return Collections.emptyList();
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
        SET("set"),
        ADD("add"),
        REMOVE("remove");

        private final String command;

        UpdateMode(String command) {
            this.command = command;
        }
    }

    private Integer rankFromPlaceCommand(String command) {
        return switch (command.toLowerCase(LocaleRoot.VALUE)) {
            case "setplaceone" -> 1;
            case "setplacetwo" -> 2;
            case "setplacethree" -> 3;
            case "setplacefour" -> 4;
            case "setplacefive" -> 5;
            case "setplacesix" -> 6;
            case "setplaceseven" -> 7;
            case "setplaceeight" -> 8;
            case "setplacenine" -> 9;
            case "setplaceten" -> 10;
            default -> null;
        };
    }

    private String rankName(int rank) {
        return switch (rank) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            case 6 -> "six";
            case 7 -> "seven";
            case 8 -> "eight";
            case 9 -> "nine";
            case 10 -> "ten";
            default -> String.valueOf(rank);
        };
    }

    private static final class LocaleRoot {
        private static final java.util.Locale VALUE = java.util.Locale.ROOT;
    }
}
