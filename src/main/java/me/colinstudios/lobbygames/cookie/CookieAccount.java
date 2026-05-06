package me.colinstudios.lobbygames.cookie;

public record CookieAccount(
    long cookies,
    int clickPowerLevel,
    int autoClickerLevel,
    int ovenLevel,
    int fortuneLevel,
    int discountLevel
) {
    private static final int UPGRADE_EFFECT_PERCENT = 40;

    public long cookiesPerClick() {
        return (1L + scaledUpgradeLevel(clickPowerLevel)) * cookieMultiplier();
    }

    public long cookiesPerSecond() {
        return scaledUpgradeLevel(autoClickerLevel) * cookieMultiplier();
    }

    public long cookieMultiplier() {
        return 1L + scaledUpgradeLevel(ovenLevel);
    }

    public int criticalChancePercent() {
        return Math.min(14, fortuneLevel * 2);
    }

    public int discountPercent() {
        return Math.min(12, discountLevel);
    }

    private static int scaledUpgradeLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(level * UPGRADE_EFFECT_PERCENT / 100.0f));
    }
}
