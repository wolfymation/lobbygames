package me.colinstudios.lobbygames.cookie;

import org.bukkit.Material;

public enum CookieUpgrade {
    CLICK_POWER("click_power_level", 20, Material.GOLDEN_PICKAXE, "Klick-Stärke", 50L, 1_000),
    AUTO_CLICKER("auto_clicker_level", 24, Material.CLOCK, "Auto-Clicker", 250L, 1_000),
    OVEN("oven_level", 29, Material.FURNACE, "Cookie-Ofen", 750L, 1_000),
    FORTUNE("fortune_level", 31, Material.EMERALD, "Glückskeks", 500L, 7),
    DISCOUNT("discount_level", 33, Material.AMETHYST_SHARD, "Coupon-Buch", 900L, 10);

    private final String column;
    private final int slot;
    private final Material material;
    private final String displayName;
    private final long baseCost;
    private final int maxLevel;

    CookieUpgrade(String column, int slot, Material material, String displayName, long baseCost, int maxLevel) {
        this.column = column;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.baseCost = baseCost;
        this.maxLevel = maxLevel;
    }

    public String column() {
        return column;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public long baseCost() {
        return baseCost;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int level(CookieAccount account) {
        return switch (this) {
            case CLICK_POWER -> account.clickPowerLevel();
            case AUTO_CLICKER -> account.autoClickerLevel();
            case OVEN -> account.ovenLevel();
            case FORTUNE -> account.fortuneLevel();
            case DISCOUNT -> account.discountLevel();
        };
    }
}
