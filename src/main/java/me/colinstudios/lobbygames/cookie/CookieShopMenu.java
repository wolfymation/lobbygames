package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class CookieShopMenu {
    public static final String TITLE = "Cookie Shop";
    public static final int SPECIAL_TNT_SLOT = 11;
    public static final int COOKIE_RAIN_SLOT = 13;
    public static final int COLOR_SNOWBALL_SLOT = 15;

    private CookieShopMenu() {
    }

    public static Inventory create(CookieClickerService service, Player player) {
        CookieAccount account = service.getAccount(player);
        boolean tntAffordable = account.cookies() >= CookieClickerService.SPECIAL_TNT_COST;
        boolean rainAffordable = account.cookies() >= CookieClickerService.COOKIE_RAIN_COST;
        boolean snowballAffordable = account.cookies() >= CookieClickerService.COLOR_SNOWBALL_COST;
        CookieShopMenuHolder holder = new CookieShopMenuHolder();
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, 27, Component.text(TITLE, NamedTextColor.GOLD));
        holder.setInventory(inventory);

        inventory.setItem(SPECIAL_TNT_SLOT, item(
            Material.TNT,
            "Cookie TNT",
            List.of(
                line("Kosten: ", service.format(CookieClickerService.SPECIAL_TNT_COST) + " Cookies", tntAffordable ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Kann ueberall ausgeloest werden.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Explodiert ohne Blockschaden.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Schleudert Spieler weg.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(tntAffordable ? "Bereit zum Kaufen" : "Nicht genug Cookies", tntAffordable ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            ),
            tntAffordable
        ));

        inventory.setItem(COOKIE_RAIN_SLOT, item(
            Material.COOKIE,
            "Cookie Regen",
            List.of(
                line("Kosten: ", service.format(CookieClickerService.COOKIE_RAIN_COST) + " Cookies", rainAffordable ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Laesst 6 Sekunden Cookies regnen.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Nur Effekt, keine aufhebbaren Items.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(rainAffordable ? "Bereit zum Kaufen" : "Nicht genug Cookies", rainAffordable ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            ),
            rainAffordable
        ));

        inventory.setItem(COLOR_SNOWBALL_SLOT, item(
            Material.SNOWBALL,
            "Farb-Schneeball",
            List.of(
                line("Kosten: ", service.format(CookieClickerService.COLOR_SNOWBALL_COST) + " Cookies", snowballAffordable ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Kann ohne Baurechte geworfen werden.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Faerbt Bloecke im Radius 10 kurz ein.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(snowballAffordable ? "Bereit zum Kaufen" : "Nicht genug Cookies", snowballAffordable ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            ),
            snowballAffordable
        ));

        return inventory;
    }

    private static Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
            .append(Component.text(value, valueColor))
            .decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, List<Component> lore, boolean enchanted) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (enchanted) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isCookieShop(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof CookieShopMenuHolder;
    }
}
