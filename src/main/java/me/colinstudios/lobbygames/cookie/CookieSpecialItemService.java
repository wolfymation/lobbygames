package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public final class CookieSpecialItemService {
    public static final String TNT_ID = "tnt";
    public static final String COOKIE_RAIN_ID = "cookieregen";
    public static final String COLOR_SNOWBALL_ID = "farbschneeball";

    private static final double BLAST_RADIUS = 6.0D;
    private static final double MAX_HORIZONTAL_KNOCKBACK = 2.4D;
    private static final double MAX_VERTICAL_KNOCKBACK = 1.15D;
    private static final int COLOR_RADIUS = 10;
    private static final long COLOR_RESTORE_DELAY_TICKS = 20L * 5L;
    private static final int COLOR_RESTORE_BLOCKS_PER_TICK = 10;
    private static final int COOKIE_RAIN_DURATION_TICKS = 20 * 6;
    private static final double COOKIE_RAIN_RADIUS = 5.5D;
    private static final int COOKIE_RAIN_COOLDOWN_LIMIT = 10;
    private static final long COOKIE_RAIN_COOLDOWN_WINDOW_MILLIS = 60_000L;
    private static final long COOKIE_RAIN_COOLDOWN_MILLIS = 10_000L;
    private static final long TNT_FALL_DAMAGE_PROTECTION_MILLIS = 6_000L;
    private static final List<Material> COLOR_BLOCKS = List.of(
        Material.RED_CONCRETE,
        Material.ORANGE_CONCRETE,
        Material.YELLOW_CONCRETE,
        Material.LIME_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE,
        Material.BLUE_CONCRETE,
        Material.PURPLE_CONCRETE,
        Material.MAGENTA_CONCRETE,
        Material.PINK_CONCRETE
    );

    private final JavaPlugin plugin;
    private final NamespacedKey specialItemKey;
    private final NamespacedKey colorSnowballProjectileKey;
    private final NamespacedKey cookieRainProjectileKey;
    private final Random random = new Random();
    private final Map<BlockKey, TemporaryColorBlock> activeColorBlocks = new HashMap<>();
    private final Map<UUID, Long> fallDamageProtectedUntil = new HashMap<>();
    private final Map<UUID, Deque<Long>> cookieRainUses = new HashMap<>();
    private final Map<UUID, Long> cookieRainCooldownUntil = new HashMap<>();
    private final ItemStack cookieParticleItem = new ItemStack(Material.COOKIE);

    public CookieSpecialItemService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.specialItemKey = new NamespacedKey(plugin, "cookie_special_item");
        this.colorSnowballProjectileKey = new NamespacedKey(plugin, "cookie_color_snowball_projectile");
        this.cookieRainProjectileKey = new NamespacedKey(plugin, "cookie_rain_projectile");
    }

    public ItemStack createSpecialItem(String itemId) {
        if (itemId == null) {
            return null;
        }
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
            case COOKIE_RAIN_ID, "cookierain", "cookie", "rain", "regen" -> createSpecialItemStack(
                Material.COOKIE,
                "Cookie Regen",
                List.of(
                    Component.text("Wirft einen Cookie-Regen herbei.", NamedTextColor.GRAY),
                    Component.text("Nur visuell, keine aufhebbaren Items.", NamedTextColor.GRAY),
                    Component.text("Regnet fuer 6 Sekunden Cookies.", NamedTextColor.GRAY)
                ),
                COOKIE_RAIN_ID
            );
            case COLOR_SNOWBALL_ID, "farbball", "colorsnowball", "snowball" -> createSpecialItemStack(
                Material.SNOWBALL,
                "Farb-Schneeball",
                List.of(
                    Component.text("Kann ohne Baurechte geworfen werden.", NamedTextColor.GRAY),
                    Component.text("Faerbt Bloecke im Radius 10 fuer 5 Sekunden.", NamedTextColor.GRAY),
                    Component.text("Stellt die urspruenglichen Bloecke wieder her.", NamedTextColor.GRAY)
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

    private String specialItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(specialItemKey, PersistentDataType.STRING);
    }

    public boolean isSpecialTnt(ItemStack stack) {
        return TNT_ID.equals(specialItemId(stack));
    }

    public boolean isColorSnowball(ItemStack stack) {
        return COLOR_SNOWBALL_ID.equals(specialItemId(stack));
    }

    public boolean isCookieRain(ItemStack stack) {
        return COOKIE_RAIN_ID.equals(specialItemId(stack));
    }

    public void throwColorSnowball(Player player) {
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(colorSnowballProjectileKey, PersistentDataType.BYTE, (byte) 1);

        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        world.playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.9f);
        world.spawnParticle(Particle.SNOWFLAKE, eye.add(player.getLocation().getDirection().multiply(0.7D)), 12, 0.12D, 0.12D, 0.12D, 0.02D);
    }

    public boolean throwCookieRain(Player player) {
        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        long cooldownRemaining = getCookieRainCooldownRemainingMillis(playerId, now);
        if (cooldownRemaining > 0L) {
            return false;
        }

        recordCookieRainUse(playerId, now);

        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.getPersistentDataContainer().set(cookieRainProjectileKey, PersistentDataType.BYTE, (byte) 1);

        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        world.playSound(eye, Sound.ENTITY_EGG_THROW, 0.8f, 1.35f);
        world.spawnParticle(Particle.ITEM, eye.add(player.getLocation().getDirection().multiply(0.7D)), 18, 0.14D, 0.14D, 0.14D, 0.04D, cookieParticleItem);
        return true;
    }

    public long getCookieRainCooldownRemainingMillis(Player player) {
        return getCookieRainCooldownRemainingMillis(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean isColorSnowballProjectile(Entity entity) {
        return entity instanceof Snowball
            && entity.getPersistentDataContainer().has(colorSnowballProjectileKey, PersistentDataType.BYTE);
    }

    public boolean isCookieRainProjectile(Entity entity) {
        return entity instanceof Snowball
            && entity.getPersistentDataContainer().has(cookieRainProjectileKey, PersistentDataType.BYTE);
    }

    public void stop() {
        restoreTemporaryBlocks();
        fallDamageProtectedUntil.clear();
        cookieRainUses.clear();
        cookieRainCooldownUntil.clear();
    }

    public void restoreTemporaryBlocks() {
        for (TemporaryColorBlock changedBlock : new ArrayList<>(activeColorBlocks.values())) {
            restoreOneColorBlock(changedBlock, false);
        }
        activeColorBlocks.clear();
    }

    public boolean isTemporarilyColored(Block block) {
        return activeColorBlocks.containsKey(BlockKey.from(block));
    }

    public boolean containsTemporarilyColoredBlock(Collection<Block> blocks) {
        return blocks.stream().anyMatch(this::isTemporarilyColored);
    }

    public boolean shouldCancelSpecialFallDamage(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        Long protectedUntil = fallDamageProtectedUntil.get(player.getUniqueId());
        if (protectedUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() > protectedUntil) {
            fallDamageProtectedUntil.remove(player.getUniqueId());
            return false;
        }
        fallDamageProtectedUntil.remove(player.getUniqueId());
        return true;
    }

    public void burstColorSnowball(Location impactLocation, Predicate<Block> protectedBlockPredicate) {
        World world = impactLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = impactLocation.toCenterLocation();
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f, 1.45f);
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.7f);
        world.spawnParticle(Particle.FIREWORK, center, 90, 1.4D, 1.1D, 1.4D, 0.12D);
        world.spawnParticle(Particle.SNOWFLAKE, center, 120, 1.8D, 1.2D, 1.8D, 0.05D);

        List<TemporaryColorBlock> changedBlocks = new ArrayList<>();
        int radiusSquared = COLOR_RADIUS * COLOR_RADIUS;
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = -COLOR_RADIUS; x <= COLOR_RADIUS; x++) {
            for (int y = -COLOR_RADIUS; y <= COLOR_RADIUS; y++) {
                for (int z = -COLOR_RADIUS; z <= COLOR_RADIUS; z++) {
                    if (x * x + y * y + z * z > radiusSquared) {
                        continue;
                    }

                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (!canTemporarilyColor(block, protectedBlockPredicate)) {
                        continue;
                    }

                    BlockKey key = BlockKey.from(block);
                    if (activeColorBlocks.containsKey(key)) {
                        continue;
                    }

                    BlockData originalData = block.getBlockData().clone();
                    Material colorMaterial = COLOR_BLOCKS.get(random.nextInt(COLOR_BLOCKS.size()));
                    TemporaryColorBlock changedBlock = new TemporaryColorBlock(key, block.getLocation(), originalData, colorMaterial);
                    activeColorBlocks.put(key, changedBlock);
                    block.setBlockData(colorMaterial.createBlockData(), false);
                    changedBlocks.add(changedBlock);
                }
            }
        }

        animateColorBurst(center);
        Bukkit.getScheduler().runTaskLater(plugin, () -> startRestoreAnimation(changedBlocks), COLOR_RESTORE_DELAY_TICKS);
    }

    public void startCookieRain(Location impactLocation) {
        World world = impactLocation.getWorld();
        if (world == null) {
            return;
        }

        Location center = impactLocation.toCenterLocation();
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.65f);
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        world.spawnParticle(Particle.ITEM, center.clone().add(0.0D, 1.4D, 0.0D), 45, 1.2D, 0.6D, 1.2D, 0.08D, cookieParticleItem);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0.0D, 1.0D, 0.0D), 35, 1.4D, 0.8D, 1.4D, 0.03D);

        int[] ticks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (ticks[0] >= COOKIE_RAIN_DURATION_TICKS) {
                task[0].cancel();
                world.playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.8f);
                return;
            }

            spawnCookieRainTick(world, center);
            ticks[0] += 2;
        }, 0L, 2L);
    }

    public void detonateCookieTnt(Location blockLocation) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location center = blockLocation.toCenterLocation();
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.FLAME, center, 80, 1.6, 1.2, 1.6, 0.08);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.85f);

        long protectedUntil = System.currentTimeMillis() + TNT_FALL_DAMAGE_PROTECTION_MILLIS;
        for (Entity entity : world.getNearbyEntities(center, BLAST_RADIUS, BLAST_RADIUS, BLAST_RADIUS)) {
            if (!(entity instanceof Player player)) {
                continue;
            }
            Vector direction = player.getLocation().toVector().subtract(center.toVector());
            double distance = Math.max(1.0D, direction.length());
            double strength = Math.max(0.15D, 1.0D - (distance / BLAST_RADIUS));
            direction.setY(0.0D);
            if (direction.lengthSquared() == 0.0D) {
                direction = new Vector(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D);
            }
            direction.normalize().multiply(MAX_HORIZONTAL_KNOCKBACK * strength);
            direction.setY(MAX_VERTICAL_KNOCKBACK * strength + 0.35D);
            player.setVelocity(direction);
            fallDamageProtectedUntil.put(player.getUniqueId(), protectedUntil);
        }
    }

    private boolean canTemporarilyColor(Block block, Predicate<Block> protectedBlockPredicate) {
        Material material = block.getType();
        if (material.isAir()
            || !material.isSolid()
            || material == Material.BEDROCK
            || material == Material.BARRIER
            || material == Material.COMMAND_BLOCK
            || material == Material.CHAIN_COMMAND_BLOCK
            || material == Material.REPEATING_COMMAND_BLOCK
            || material == Material.STRUCTURE_BLOCK
            || material == Material.JIGSAW) {
            return false;
        }
        if (protectedBlockPredicate.test(block)) {
            return false;
        }
        BlockState state = block.getState(false);
        return !(state instanceof TileState);
    }

    private void animateColorBurst(Location center) {
        for (int step = 0; step <= COLOR_RADIUS; step += 2) {
            final int radius = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World world = center.getWorld();
                if (world == null) {
                    return;
                }
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 14 + radius * 2, radius * 0.45D, 0.35D, radius * 0.45D, 0.02D);
                world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    center,
                    20 + radius * 3,
                    radius * 0.35D,
                    0.45D,
                    radius * 0.35D,
                    0.02D,
                    new Particle.DustTransition(Color.AQUA, Color.FUCHSIA, 1.6f)
                );
            }, step);
        }
    }

    private void spawnCookieRainTick(World world, Location center) {
        Location cloud = center.clone().add(0.0D, 7.0D, 0.0D);
        world.spawnParticle(Particle.CLOUD, cloud, 4, COOKIE_RAIN_RADIUS * 0.45D, 0.1D, COOKIE_RAIN_RADIUS * 0.45D, 0.01D);

        for (int i = 0; i < 7; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * COOKIE_RAIN_RADIUS;
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = 3.0D + random.nextDouble() * 5.0D;
            Location cookie = center.clone().add(x, y, z);

            world.spawnParticle(Particle.ITEM, cookie, 2, 0.08D, 0.75D, 0.08D, 0.16D, cookieParticleItem);
            if (random.nextInt(5) == 0) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(x, 0.35D, z), 1, 0.08D, 0.08D, 0.08D, 0.01D);
            }
        }

        if (random.nextInt(10) == 0) {
            world.playSound(center, Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.8f);
        }
    }

    private void recordCookieRainUse(UUID playerId, long now) {
        Deque<Long> uses = cookieRainUses.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        removeExpiredCookieRainUses(uses, now);
        uses.addLast(now);
        if (uses.size() >= COOKIE_RAIN_COOLDOWN_LIMIT) {
            cookieRainCooldownUntil.put(playerId, now + COOKIE_RAIN_COOLDOWN_MILLIS);
            uses.clear();
        }
    }

    private long getCookieRainCooldownRemainingMillis(UUID playerId, long now) {
        Long cooldownUntil = cookieRainCooldownUntil.get(playerId);
        if (cooldownUntil == null) {
            return 0L;
        }
        long remaining = cooldownUntil - now;
        if (remaining <= 0L) {
            cookieRainCooldownUntil.remove(playerId);
            return 0L;
        }
        return remaining;
    }

    private void removeExpiredCookieRainUses(Deque<Long> uses, long now) {
        while (!uses.isEmpty() && now - uses.peekFirst() > COOKIE_RAIN_COOLDOWN_WINDOW_MILLIS) {
            uses.removeFirst();
        }
    }

    private void startRestoreAnimation(List<TemporaryColorBlock> changedBlocks) {
        if (changedBlocks.isEmpty()) {
            return;
        }

        Location location = changedBlocks.getFirst().location().toCenterLocation();
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.35f);

        List<TemporaryColorBlock> restoreQueue = new ArrayList<>(changedBlocks);
        Collections.shuffle(restoreQueue, random);
        int[] index = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int restoredThisTick = 0; restoredThisTick < COLOR_RESTORE_BLOCKS_PER_TICK && index[0] < restoreQueue.size(); restoredThisTick++) {
                restoreOneColorBlock(restoreQueue.get(index[0]++), true);
            }
            if (index[0] >= restoreQueue.size()) {
                task[0].cancel();
            }
        }, 0L, 1L);
    }

    private void restoreOneColorBlock(TemporaryColorBlock changedBlock, boolean playEffects) {
        Block block = changedBlock.location().getBlock();
        if (block.getType() == changedBlock.changedMaterial()) {
            block.setBlockData(changedBlock.originalData(), false);
            if (playEffects) {
                block.getWorld().spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    block.getLocation().toCenterLocation(),
                    2,
                    0.25D,
                    0.25D,
                    0.25D,
                    0.01D,
                    new Particle.DustTransition(Color.FUCHSIA, Color.WHITE, 1.0f)
                );
            }
            if (playEffects && random.nextInt(18) == 0) {
                block.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.25f, 1.8f);
            }
        }
        activeColorBlocks.remove(changedBlock.key());
    }

    private record TemporaryColorBlock(BlockKey key, Location location, BlockData originalData, Material changedMaterial) {
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
