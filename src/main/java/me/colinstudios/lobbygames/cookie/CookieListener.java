package me.colinstudios.lobbygames.cookie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CookieListener implements Listener {
    private final CookieClickerService service;
    private final CookieSpecialItemService specialItemService;

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
            consumeOneSpecialItem(event.getPlayer());
            return;
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            && specialItemService.isColorSnowball(event.getItem())) {
            event.setCancelled(true);
            specialItemService.throwColorSnowball(event.getPlayer());
            consumeOneSpecialItem(event.getPlayer());
            return;
        }

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            && specialItemService.isCookieRain(event.getItem())) {
            event.setCancelled(true);
            if (!specialItemService.throwCookieRain(event.getPlayer())) {
                long remainingSeconds = (long) Math.ceil(specialItemService.getCookieRainCooldownRemainingMillis(event.getPlayer()) / 1000.0D);
                event.getPlayer().sendActionBar(Component.text("Cookie-Regen Cooldown: noch " + remainingSeconds + "s", NamedTextColor.RED));
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                return;
            }
            consumeOneSpecialItem(event.getPlayer());
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
            Component blockReason = service.getCookieClickBlockReason(player);
            if (blockReason != null) {
                player.sendActionBar(blockReason);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.7f);
                return;
            }
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

    private void consumeOneSpecialItem(Player player) {
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
    public void onProjectileHit(ProjectileHitEvent event) {
        boolean colorSnowball = specialItemService.isColorSnowballProjectile(event.getEntity());
        boolean cookieRain = specialItemService.isCookieRainProjectile(event.getEntity());
        if (!colorSnowball && !cookieRain) {
            return;
        }

        org.bukkit.Location impactLocation;
        if (event.getHitBlock() != null) {
            impactLocation = event.getHitBlock().getLocation();
            if (event.getHitBlockFace() != null) {
                impactLocation = impactLocation.add(event.getHitBlockFace().getModX(), event.getHitBlockFace().getModY(), event.getHitBlockFace().getModZ());
            }
        } else if (event.getHitEntity() != null) {
            impactLocation = event.getHitEntity().getLocation();
        } else {
            impactLocation = event.getEntity().getLocation();
        }

        if (colorSnowball) {
            specialItemService.burstColorSnowball(impactLocation, service::isProtectedBlock);
        } else {
            specialItemService.startCookieRain(impactLocation);
        }
        event.getEntity().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (specialItemService.isColorSnowballProjectile(event.getDamager())
            || specialItemService.isCookieRainProjectile(event.getDamager())) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
            && specialItemService.shouldCancelSpecialFallDamage(event.getEntity())) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (CookieMenu.isCookieMenu(event.getView().getTopInventory()) || CookieShopMenu.isCookieShop(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (specialItemService.isTemporarilyColored(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Dieser Effekt-Block wird gleich automatisch zurueckgesetzt.", NamedTextColor.RED));
            return;
        }

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

        event.setCancelled(true);
        event.setBuild(false);
        specialItemService.detonateCookieTnt(event.getBlockPlaced().getLocation());
        consumeOneSpecialItem(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> service.isProtectedBlock(block) || specialItemService.isTemporarilyColored(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> service.isProtectedBlock(block) || specialItemService.isTemporarilyColored(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isProtectedBlock) || specialItemService.containsTemporarilyColoredBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(service::isProtectedBlock) || specialItemService.containsTemporarilyColoredBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.registerPlayerActivity(event.getPlayer());
        service.hideOtherHolograms(event.getPlayer());
        service.spawnPlayerHologram(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        service.handlePlayerMove(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.forgetPlayer(event.getPlayer());
    }
}
