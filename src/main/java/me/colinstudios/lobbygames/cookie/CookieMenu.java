package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CookieMenu {
    private static final List<Integer> BORDER_SLOTS = Arrays.asList(
        0, 1, 2, 3, 5, 6, 7, 8,
        9, 17,
        18, 26,
        27, 35,
        36, 44,
        45, 46, 47, 48, 50, 51, 52, 53
    );

    private CookieMenu() {
    }

    public static Inventory create(CookieClickerService service, Player player) {
        CookieAccount account = service.getAccount(player);
        CookieMenuHolder holder = new CookieMenuHolder();
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, 54, Component.text(CookieClickerService.GUI_TITLE, NamedTextColor.GOLD));
        holder.setInventory(inventory);

        ItemStack glass = item(Material.BROWN_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, glass);
        }

        ItemStack balance = item(
            Material.COOKIE,
            "Deine Cookies",
            List.of(
                line("Kontostand: ", service.format(account.cookies()) + " Cookies", NamedTextColor.GOLD),
                line("Pro Hau: ", service.format(account.cookiesPerClick()) + " Cookies", NamedTextColor.YELLOW),
                line("Pro Sekunde: ", service.format(account.cookiesPerSecond()) + " Cookies", NamedTextColor.AQUA)
            ),
            true
        );
        inventory.setItem(4, balance);

        inventory.setItem(13, item(
            Material.EXPERIENCE_BOTTLE,
            "Cookie Stats",
            List.of(
                line("Multiplikator: ", "x" + account.cookieMultiplier(), NamedTextColor.GREEN),
                line("Krit-Chance: ", account.criticalChancePercent() + "%", NamedTextColor.LIGHT_PURPLE),
                line("Rabatt: ", account.discountPercent() + "%", NamedTextColor.AQUA)
            ),
            false
        ));

        for (CookieUpgrade upgrade : CookieUpgrade.values()) {
            inventory.setItem(upgrade.slot(), upgradeItem(service, account, upgrade));
        }

        inventory.setItem(49, item(
            Material.LIME_DYE,
            "Upgrade kaufen",
            List.of(
                Component.text("Klicke ein Upgrade an.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Jeder Spieler bezahlt nur mit seinen eigenen Cookies.", NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false)
            ),
            false
        ));

        return inventory;
    }

    public static CookieUpgrade upgradeAt(int slot) {
        for (CookieUpgrade upgrade : CookieUpgrade.values()) {
            if (upgrade.slot() == slot) {
                return upgrade;
            }
        }
        return null;
    }

    public static boolean isCookieMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof CookieMenuHolder;
    }

    private static ItemStack upgradeItem(CookieClickerService service, CookieAccount account, CookieUpgrade upgrade) {
        int level = upgrade.level(account);
        long cost = service.upgradeCost(upgrade, account);
        boolean maxLevel = service.isMaxLevel(account, upgrade);
        boolean affordable = !maxLevel && account.cookies() >= cost;
        List<Component> lore = new ArrayList<>();
        lore.add(line("Level: ", level + " / " + upgrade.maxLevel(), NamedTextColor.WHITE));
        lore.add(maxLevel
            ? Component.text("Max-Level erreicht", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
            : line("Kosten: ", service.format(cost) + " Cookies", affordable ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.empty());
        switch (upgrade) {
            case CLICK_POWER -> lore.add(line("Effekt: ", "ca. +0,4 Basis-Cookies pro Hau", NamedTextColor.YELLOW));
            case AUTO_CLICKER -> lore.add(line("Effekt: ", "ca. +0,4 Basis-Cookies pro Sekunde", NamedTextColor.AQUA));
            case OVEN -> lore.add(line("Effekt: ", "ca. +0,4x Multiplikator für Hau und Auto", NamedTextColor.GOLD));
            case FORTUNE -> {
                lore.add(line("Effekt: ", "+2% Chance auf doppelten Hau", NamedTextColor.LIGHT_PURPLE));
                lore.add(line("Aktuell: ", account.criticalChancePercent() + "% / 14%", NamedTextColor.LIGHT_PURPLE));
            }
            case DISCOUNT -> {
                lore.add(line("Effekt: ", "+1% Rabatt auf alle Upgrades", NamedTextColor.GREEN));
                lore.add(line("Aktuell: ", account.discountPercent() + "% / 12%", NamedTextColor.GREEN));
            }
        }
        lore.add(Component.empty());
        lore.add(Component.text(maxLevel ? "Voll ausgebaut" : affordable ? "Bereit zum Kaufen" : "Nicht genug Cookies", maxLevel || affordable ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        return item(upgrade.material(), upgrade.displayName(), lore, affordable);
    }

    private static Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
            .append(Component.text(value, valueColor))
            .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, List<Component> lore, boolean glow) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
