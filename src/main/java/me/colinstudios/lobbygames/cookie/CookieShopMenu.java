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
    public static final int SPECIAL_TNT_SLOT = 13;

    private CookieShopMenu() {
    }

    public static Inventory create(CookieClickerService service, Player player) {
        CookieAccount account = service.getAccount(player);
        boolean affordable = account.cookies() >= CookieClickerService.SPECIAL_TNT_COST;
        CookieShopMenuHolder holder = new CookieShopMenuHolder();
        Inventory inventory = org.bukkit.Bukkit.createInventory(holder, 27, Component.text(TITLE, NamedTextColor.GOLD));
        holder.setInventory(inventory);

        ItemStack glass = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, glass);
        }

        inventory.setItem(SPECIAL_TNT_SLOT, item(
            Material.TNT,
            "Special TNT",
            List.of(
                line("Kosten: ", service.format(CookieClickerService.SPECIAL_TNT_COST) + " Cookies", affordable ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text("Explodiert ohne Blockschaden.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Schleudert Spieler weg.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(affordable ? "Bereit zum Kaufen" : "Nicht genug Cookies", affordable ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
            ),
            affordable
        ));

        return inventory;
    }

    public static boolean isCookieShop(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof CookieShopMenuHolder;
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
