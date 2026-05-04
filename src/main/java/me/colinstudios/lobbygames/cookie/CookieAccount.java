package me.colinstudios.lobbygames.cookie;

public record CookieAccount(
    long cookies,
    int clickPowerLevel,
    int autoClickerLevel,
    int ovenLevel,
    int fortuneLevel,
    int discountLevel
) {
    public long cookiesPerClick() {
        return (1L + clickPowerLevel) * cookieMultiplier();
    }

    public long cookiesPerSecond() {
        return autoClickerLevel * cookieMultiplier();
    }

    public long cookieMultiplier() {
        return 1L + ovenLevel;
    }

    public int criticalChancePercent() {
        return Math.min(35, fortuneLevel * 5);
    }

    public int discountPercent() {
        return Math.min(30, discountLevel * 3);
    }
}
