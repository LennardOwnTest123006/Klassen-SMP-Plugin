package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Claim;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;

/**
 * Schutz von Claims, Spawn und geschuetzten Welten.
 *
 * <p>Die Pruefreihenfolge ist immer gleich: Bypass-Recht, geschuetzte Welt,
 * Spawnbereich, Claim. Dadurch ist das Verhalten fuer Spieler vorhersehbar.</p>
 */
public final class ProtectionListener implements Listener {

    private final KlassenSMP plugin;

    public ProtectionListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Zentrale Bauberechtigung.
     *
     * @return {@code true}, wenn der Spieler hier veraendern darf
     */
    private boolean mayBuild(Player player, Location location, boolean notify) {
        if (player.hasPermission("klassensmp.protection.bypass")) {
            return true;
        }
        if (plugin.getWorldManager().isProtected(location.getWorld().getName())) {
            if (notify) {
                plugin.getMessages().send(player, "protection.world");
            }
            return false;
        }
        if (plugin.getSpawnManager().isInSpawnArea(location)) {
            if (notify) {
                plugin.getMessages().send(player, "protection.spawn");
            }
            return false;
        }
        if (!plugin.getClaimManager().canBuild(player, location)) {
            if (notify) {
                Claim claim = plugin.getClaimManager().getClaimAt(location);
                String owner = "?";
                if (claim != null) {
                    var data = plugin.getPlayerDataManager().get(claim.getOwner());
                    owner = data == null ? owner : data.getName();
                }
                plugin.getMessages().send(player, "claims.protected", "%owner%", owner);
            }
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!mayBuild(event.getPlayer(), event.getBlock().getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!mayBuild(event.getPlayer(), event.getBlock().getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (!mayBuild(event.getPlayer(), event.getBlock().getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    /** Schuetzt Kisten, Tueren und Redstone-Bauteile in fremden Claims. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isProtectedInteraction(block.getType())) {
            return;
        }
        if (!mayBuild(event.getPlayer(), block.getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    /** Interaktionen, die in fremden Claims unterbunden werden. */
    private boolean isProtectedInteraction(Material material) {
        if (material.name().endsWith("_DOOR")
                || material.name().endsWith("_TRAPDOOR")
                || material.name().endsWith("_FENCE_GATE")
                || material.name().endsWith("_BUTTON")
                || material.name().endsWith("_SHULKER_BOX")
                || material.name().endsWith("_BED")
                || material.name().equals("CRAFTER")) {
            return true;
        }
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, BLAST_FURNACE, SMOKER, HOPPER, DISPENSER, DROPPER,
                 BREWING_STAND, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, BEACON,
                 LEVER, REPEATER, COMPARATOR, NOTE_BLOCK, JUKEBOX, LECTERN, CAULDRON,
                 RESPAWN_ANCHOR -> true;
            default -> false;
        };
    }

    /** Schuetzt Villager, Rahmen und Tiere in fremden Claims. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfigManager().bool("claims.protect-entities", true)) {
            return;
        }
        if (!mayBuild(event.getPlayer(), event.getRightClicked().getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player
                && !mayBuild(player, event.getEntity().getLocation(), true)) {
            event.setCancelled(true);
        }
    }

    /** Explosionen zerstoeren keine geschuetzten Bereiche. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isExplosionProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isExplosionProtected);
    }

    private boolean isExplosionProtected(Block block) {
        Location location = block.getLocation();
        if (plugin.getWorldManager().isProtected(location.getWorld().getName())) {
            return true;
        }
        if (plugin.getConfigManager().bool("protection.spawn.block-explosions", true)
                && plugin.getSpawnManager().isInSpawnArea(location)) {
            return true;
        }
        return plugin.getConfigManager().bool("claims.block-explosions", true)
                && plugin.getClaimManager().getClaimAt(location) != null;
    }

    /** Feuer soll geschuetzte Bereiche nicht zerstoeren. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isExplosionProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Verhindert, dass Kolben Bloecke aus einem Claim herausschieben. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!plugin.getConfigManager().bool("claims.block-piston-grief", true)) {
            return;
        }
        Claim source = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        for (Block block : event.getBlocks()) {
            Claim target = plugin.getClaimManager().getClaimAt(block.getLocation());
            if (target != null && target != source) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!plugin.getConfigManager().bool("claims.block-piston-grief", true)) {
            return;
        }
        Claim source = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        for (Block block : event.getBlocks()) {
            Claim target = plugin.getClaimManager().getClaimAt(block.getLocation());
            if (target != null && target != source) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Meldet dem Spieler, in wessen Claim er sich befindet (optional). */
    public String describeClaim(Claim claim) {
        if (claim == null) {
            return plugin.getMessages().plain("claims.wilderness");
        }
        var data = plugin.getPlayerDataManager().get(claim.getOwner());
        return data == null ? claim.getOwner().toString().substring(0, 8) : data.getName();
    }

    /** Hilfsfunktion fuer Befehle: Weltname klein geschrieben. */
    public static String worldKey(Location location) {
        return location.getWorld() == null ? "" : location.getWorld().getName().toLowerCase(Locale.ROOT);
    }
}
