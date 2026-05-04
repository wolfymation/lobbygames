package me.colinstudios.lobbygames.cookie;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class CookiePlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final CookieClickerService service;

    public CookiePlaceholderExpansion(JavaPlugin plugin, CookieClickerService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lobbygames";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ColinStudios";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        CookieAccount account = service.getAccount(player);
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "cookies" -> String.valueOf(account.cookies());
            case "cookies_formatted" -> service.format(account.cookies());
            case "cookies_per_click" -> String.valueOf(account.cookiesPerClick());
            case "cookies_per_click_formatted" -> service.format(account.cookiesPerClick());
            case "cookies_per_second" -> String.valueOf(account.cookiesPerSecond());
            case "cookies_per_second_formatted" -> service.format(account.cookiesPerSecond());
            case "click_level" -> String.valueOf(account.clickPowerLevel());
            case "auto_level" -> String.valueOf(account.autoClickerLevel());
            case "oven_level" -> String.valueOf(account.ovenLevel());
            case "fortune_level" -> String.valueOf(account.fortuneLevel());
            case "discount_level" -> String.valueOf(account.discountLevel());
            case "multiplier" -> String.valueOf(account.cookieMultiplier());
            case "critical_chance" -> String.valueOf(account.criticalChancePercent());
            case "discount_percent" -> String.valueOf(account.discountPercent());
            default -> null;
        };
    }
}
