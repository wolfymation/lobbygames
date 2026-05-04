package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.List;

public final class CookieSpecialItemService {
    private static final String TNT_ID = "tnt";
    private static final double BLAST_RADIUS = 6.0D;
    private static final double MAX_HORIZONTAL_KNOCKBACK = 2.4D;
    private static final double MAX_VERTICAL_KNOCKBACK = 1.15D;

    private final NamespacedKey specialItemKey;

    public CookieSpecialItemService(JavaPlugin plugin) {
        this.specialItemKey = new NamespacedKey(plugin, "cookie_special_item");
    }

    public ItemStack createSpecialItem(String itemId) {
        if (!TNT_ID.equalsIgnoreCase(itemId)) {
            return null;
        }

        ItemStack stack = new ItemStack(Material.TNT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Cookie TNT", NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text("Kann überall ausgelöst werden.", NamedTextColor.GRAY),
            Component.text("Explodiert ohne Blockschaden.", NamedTextColor.GRAY)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, TNT_ID);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isSpecialTnt(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TNT || !stack.hasItemMeta()) {
            return false;
        }
        String itemId = stack.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
        return TNT_ID.equals(itemId);
    }

    public void detonateCookieTnt(Location blockLocation) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = blockLocation.toCenterLocation();
        world.spawnParticle(Particle.EXPLOSION, center, 24, 1.0D, 0.8D, 1.0D, 0.08D);
        world.spawnParticle(Particle.FLAME, center, 90, 1.4D, 1.1D, 1.4D, 0.08D);
        world.spawnParticle(Particle.SMOKE, center, 140, 1.8D, 1.2D, 1.8D, 0.03D);
        world.spawnParticle(Particle.CLOUD, center, 70, 1.5D, 1.0D, 1.5D, 0.12D);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.85f);

        for (Entity entity : world.getNearbyEntities(center, BLAST_RADIUS, BLAST_RADIUS, BLAST_RADIUS)) {
            if (!(entity instanceof Player player)) {
                continue;
            }

            Location playerLocation = player.getLocation();
            double distance = Math.max(0.4D, playerLocation.distance(center));
            if (distance > BLAST_RADIUS) {
                continue;
            }

            double strength = 1.0D - (distance / BLAST_RADIUS);
            Vector direction = playerLocation.toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.01D) {
                direction = player.getFacing().getDirection().multiply(-1);
            }

            Vector knockback = direction.normalize().multiply(0.75D + MAX_HORIZONTAL_KNOCKBACK * strength);
            knockback.setY(0.45D + MAX_VERTICAL_KNOCKBACK * strength);
            player.setVelocity(player.getVelocity().add(knockback));
            player.playSound(playerLocation, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        }
    }
}
