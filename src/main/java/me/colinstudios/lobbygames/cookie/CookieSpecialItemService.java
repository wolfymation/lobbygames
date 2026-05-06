package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

public final class CookieSpecialItemService {
    public static final String TNT_ID = "tnt";
    public static final String COOKIE_RAIN_ID = "cookieregen";
    public static final String COLOR_SNOWBALL_ID = "farbschneeball";

    private static final double BLAST_RADIUS = 6.0D;
    private static final double MAX_HORIZONTAL_KNOCKBACK = 2.4D;
    private static final double MAX_VERTICAL_KNOCKBACK = 1.15D;
    private static final int COLOR_RADIUS = 10;
    private static final int MAX_COLORED_BLOCKS = 700;
    private static final int RESTORE_DELAY_TICKS = 20 * 6;
    private static final int RESTORE_BATCH_SIZE = 20;
    private static final List<Material> COLOR_BLOCKS = List.of(
        Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE, Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE
    );

    private final JavaPlugin plugin;
    private final NamespacedKey specialItemKey;
    private final Random random = new Random();
    private final Map<String, TemporaryBlock> temporaryBlocks = new HashMap<>();
    private final List<BukkitTask> restoreTasks = new ArrayList<>();

    public CookieSpecialItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.specialItemKey = new NamespacedKey(plugin, "cookie_special_item");
    }

    public ItemStack createSpecialItem(String itemId) {
        String normalized = itemId.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case TNT_ID -> createSpecialItemStack(
                Material.TNT,
                "Cookie TNT",
                List.of(
                    Component.text("Kann ueberall ausgeloest werden.", NamedTextColor.GRAY),
                    Component.text("Explodiert ohne Blockschaden.", NamedTextColor.GRAY)
                ),
                TNT_ID
            );
            case COOKIE_RAIN_ID, "cookierain", "regen" -> createSpecialItemStack(
                Material.COOKIE,
                "Cookie Regen",
                List.of(
                    Component.text("Laesst Cookies um dich regnen.", NamedTextColor.GRAY),
                    Component.text("Rechtsklick zum Ausloesen.", NamedTextColor.GRAY)
                ),
                COOKIE_RAIN_ID
            );
            case COLOR_SNOWBALL_ID, "farbball", "colorsnowball" -> createSpecialItemStack(
                Material.SNOWBALL,
                "Farb-Schneeball",
                List.of(
                    Component.text("Faerbt sichere Bloecke temporaer ein.", NamedTextColor.GRAY),
                    Component.text("Stellt alles automatisch wieder her.", NamedTextColor.GRAY)
                ),
                COLOR_SNOWBALL_ID
            );
            default -> null;
        };
    }

    private ItemStack createSpecialItemStack(Material material, String name, List<Component> lore, String itemId) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(specialItemKey, PersistentDataType.STRING, itemId);
        stack.setItemMeta(meta);
        return stack;
    }

    public String specialItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
    }

    public boolean isSpecialTnt(ItemStack stack) {
        return TNT_ID.equals(specialItemId(stack));
    }

    public boolean isCookieRain(ItemStack stack) {
        return COOKIE_RAIN_ID.equals(specialItemId(stack));
    }

    public boolean isColorSnowball(ItemStack stack) {
        return COLOR_SNOWBALL_ID.equals(specialItemId(stack));
    }

    public NamespacedKey specialItemKey() {
        return specialItemKey;
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

    public void startCookieRain(Player player) {
        Location start = player.getLocation();
        new BukkitRunnable() {
            private int runs;

            @Override
            public void run() {
                if (!player.isOnline() || runs++ >= 16) {
                    cancel();
                    return;
                }
                World world = player.getWorld();
                Location center = player.getLocation().add(0.0D, 4.0D, 0.0D);
                for (int i = 0; i < 28; i++) {
                    Location particle = center.clone().add(random.nextDouble(-4.0D, 4.0D), random.nextDouble(0.0D, 2.5D), random.nextDouble(-4.0D, 4.0D));
                    world.spawnParticle(Particle.ITEM, particle, 1, 0.05D, 0.45D, 0.05D, 0.06D, new ItemStack(Material.COOKIE));
                }
                world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.35f, 1.7f);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> start.getWorld().playSound(start, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f), 20L * 8L);
    }

    public void detonateColorSnowball(Location impact, Predicate<Block> protectedBlockPredicate) {
        World world = impact.getWorld();
        if (world == null) {
            return;
        }

        List<Block> blocks = collectColorableBlocks(impact, protectedBlockPredicate);
        if (blocks.isEmpty()) {
            world.playSound(impact, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }

        Collections.shuffle(blocks, random);
        if (blocks.size() > MAX_COLORED_BLOCKS) {
            blocks = new ArrayList<>(blocks.subList(0, MAX_COLORED_BLOCKS));
        }

        for (Block block : blocks) {
            String key = blockKey(block);
            if (temporaryBlocks.containsKey(key)) {
                continue;
            }
            temporaryBlocks.put(key, new TemporaryBlock(block.getLocation(), block.getBlockData()));
            block.setType(COLOR_BLOCKS.get(random.nextInt(COLOR_BLOCKS.size())), false);
        }

        world.spawnParticle(Particle.HAPPY_VILLAGER, impact, 160, 3.0D, 2.2D, 3.0D, 0.15D);
        world.spawnParticle(Particle.DUST, impact, 90, 3.0D, 2.0D, 3.0D, 0.0D, new Particle.DustOptions(org.bukkit.Color.FUCHSIA, 1.4f));
        world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.35f);

        List<String> restoreKeys = blocks.stream().map(this::blockKey).toList();
        BukkitTask task = new BukkitRunnable() {
            private int ticksUntilRestore = RESTORE_DELAY_TICKS;
            private int index;

            @Override
            public void run() {
                if (ticksUntilRestore > 0) {
                    ticksUntilRestore--;
                    return;
                }
                for (int restored = 0; restored < RESTORE_BATCH_SIZE && index < restoreKeys.size(); restored++) {
                    restoreTemporaryBlock(restoreKeys.get(index++));
                }
                if (index >= restoreKeys.size()) {
                    restoreTasks.removeIf(BukkitTask::isCancelled);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        restoreTasks.add(task);
    }

    private List<Block> collectColorableBlocks(Location center, Predicate<Block> protectedBlockPredicate) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int radiusSquared = COLOR_RADIUS * COLOR_RADIUS;
        for (int x = -COLOR_RADIUS; x <= COLOR_RADIUS; x++) {
            for (int y = -COLOR_RADIUS; y <= COLOR_RADIUS; y++) {
                for (int z = -COLOR_RADIUS; z <= COLOR_RADIUS; z++) {
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }
                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (isColorable(block, protectedBlockPredicate)) {
                        blocks.add(block);
                    }
                }
            }
        }
        return blocks;
    }

    private boolean isColorable(Block block, Predicate<Block> protectedBlockPredicate) {
        Material type = block.getType();
        if (type.isAir() || !type.isSolid() || !type.isOccluding() || type.hasGravity()) {
            return false;
        }
        if (protectedBlockPredicate.test(block) || block.getState() instanceof TileState) {
            return false;
        }
        String name = type.name();
        return !name.contains("HEAD") && !name.contains("SKULL") && !name.contains("CHEST") && !name.contains("SHULKER")
            && !name.contains("BARREL") && !name.contains("SIGN") && !name.contains("BANNER") && !name.contains("BED")
            && !name.contains("DOOR") && !name.contains("TRAPDOOR") && !name.contains("COMMAND_BLOCK");
    }

    public void restoreTemporaryBlocks() {
        for (BukkitTask task : new ArrayList<>(restoreTasks)) {
            task.cancel();
        }
        restoreTasks.clear();
        for (String key : new ArrayList<>(temporaryBlocks.keySet())) {
            restoreTemporaryBlock(key);
        }
    }

    private void restoreTemporaryBlock(String key) {
        TemporaryBlock temporaryBlock = temporaryBlocks.remove(key);
        if (temporaryBlock == null) {
            return;
        }
        Block block = temporaryBlock.location().getBlock();
        block.setBlockData(temporaryBlock.blockData(), false);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private record TemporaryBlock(Location location, BlockData blockData) {
    }
}
