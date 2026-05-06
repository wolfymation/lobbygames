package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CookieListener implements Listener {
    private final CookieClickerService service;
    private final CookieSpecialItemService specialItemService;
    private final Map<UUID, String> pendingSpecialProjectiles = new HashMap<>();

    public CookieListener(CookieClickerService service, CookieSpecialItemService specialItemService) {
        this.service = service;
        this.specialItemService = specialItemService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && specialItemService.isSpecialTnt(event.getItem())) {
            event.setCancelled(true);
            Block target = event.getClickedBlock().getRelative(event.getBlockFace());
            specialItemService.detonateCookieTnt(target.getLocation());
            consumeOneSpecialTnt(event.getPlayer());
            return;
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && specialItemService.isCookieRain(event.getItem())) {
            event.setCancelled(true);
            specialItemService.startCookieRain(event.getPlayer());
            consumeOneItem(event.getPlayer());
            return;
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && specialItemService.isColorSnowball(event.getItem())) {
            pendingSpecialProjectiles.put(event.getPlayer().getUniqueId(), CookieSpecialItemService.COLOR_SNOWBALL_ID);
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        if (service.isShopBlock(event.getClickedBlock())) {
            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.getPlayer().openInventory(CookieShopMenu.create(service, event.getPlayer()));
            }
            return;
        }

        if (!service.isClickerBlock(event.getClickedBlock())) {
            if (service.isProtectedBlock(event.getClickedBlock())) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            player.openInventory(CookieMenu.create(service, player));
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            CookieClickResult result = service.click(player);
            Component message = Component.text("+" + service.format(result.earned()) + " Cookies", result.critical() ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD)
                .append(Component.text(" | Konto: " + service.format(result.total()), NamedTextColor.YELLOW));
            if (result.critical()) {
                message = Component.text("KRIT! ", NamedTextColor.LIGHT_PURPLE).append(message);
            }
            player.sendActionBar(message);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (CookieShopMenu.isCookieShop(event.getView().getTopInventory())) {
            event.setCancelled(true);
            if (event.getRawSlot() == CookieShopMenu.SPECIAL_TNT_SLOT) {
                buySpecialItem(player, CookieSpecialItemService.TNT_ID);
            } else if (event.getRawSlot() == CookieShopMenu.COOKIE_RAIN_SLOT) {
                buySpecialItem(player, CookieSpecialItemService.COOKIE_RAIN_ID);
            } else if (event.getRawSlot() == CookieShopMenu.COLOR_SNOWBALL_SLOT) {
                buySpecialItem(player, CookieSpecialItemService.COLOR_SNOWBALL_ID);
            }
            return;
        }
        if (!CookieMenu.isCookieMenu(event.getView().getTopInventory())) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        CookieUpgrade upgrade = CookieMenu.upgradeAt(event.getRawSlot());
        if (upgrade == null) {
            return;
        }

        boolean bought = service.buyUpgrade(player, upgrade);
        if (bought) {
            player.sendMessage(Component.text(upgrade.displayName() + " gekauft.", NamedTextColor.GREEN));
            player.openInventory(CookieMenu.create(service, player));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            player.sendMessage(Component.text("Du hast nicht genug Cookies auf deinem Konto.", NamedTextColor.RED));
        }
    }

    private void buySpecialItem(Player player, String itemId) {
        boolean bought = switch (itemId) {
            case CookieSpecialItemService.TNT_ID -> service.buySpecialTnt(player);
            case CookieSpecialItemService.COOKIE_RAIN_ID -> service.buyCookieRain(player);
            case CookieSpecialItemService.COLOR_SNOWBALL_ID -> service.buyColorSnowball(player);
            default -> false;
        };
        if (!bought) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            player.sendMessage(Component.text("Du hast nicht genug Cookies auf deinem Konto.", NamedTextColor.RED));
            return;
        }

        org.bukkit.inventory.ItemStack item = specialItemService.createSpecialItem(itemId);
        player.getInventory().addItem(item).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
        player.sendMessage(Component.text("Special-Item gekauft.", NamedTextColor.GREEN));
        player.openInventory(CookieShopMenu.create(service, player));
    }

    private void consumeOneSpecialTnt(Player player) {
        consumeOneItem(player);
    }

    private void consumeOneItem(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        org.bukkit.inventory.ItemStack stack = player.getInventory().getItemInMainHand();
        int amount = stack.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(amount - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball) || !(snowball.getShooter() instanceof Player player)) {
            return;
        }

        String itemId = pendingSpecialProjectiles.remove(player.getUniqueId());
        if (!CookieSpecialItemService.COLOR_SNOWBALL_ID.equals(itemId)) {
            return;
        }

        snowball.getPersistentDataContainer().set(specialItemService.specialItemKey(), PersistentDataType.STRING, CookieSpecialItemService.COLOR_SNOWBALL_ID);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        String itemId = snowball.getPersistentDataContainer().get(specialItemService.specialItemKey(), PersistentDataType.STRING);
        if (!CookieSpecialItemService.COLOR_SNOWBALL_ID.equals(itemId)) {
            return;
        }
        specialItemService.detonateColorSnowball(snowball.getLocation(), service::isProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (CookieMenu.isCookieMenu(event.getView().getTopInventory()) || CookieShopMenu.isCookieShop(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (service.isProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Dieser CookieClicker-Block ist geschuetzt.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!specialItemService.isSpecialTnt(event.getItemInHand())) {
            return;
        }

        event.setCancelled(false);
        event.setBuild(true);
        event.getBlockPlaced().setType(org.bukkit.Material.AIR, false);
        specialItemService.detonateCookieTnt(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(service::isProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(service::isProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isProtectedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isProtectedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.hideOtherHolograms(event.getPlayer());
        service.spawnPlayerHologram(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingSpecialProjectiles.remove(event.getPlayer().getUniqueId());
        service.removePlayerHologram(event.getPlayer());
    }
}
