package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.ModerationGui;
import de.klassensmp.gui.PlayerListGui;
import de.klassensmp.moderation.StaffModeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Verhalten des Moderationsmodus.
 *
 * <p>Im Moderationsmodus kann der Moderator nichts bauen, nichts abbauen und
 * keine Items verlieren. Die Moderationsitems reagieren auf Rechtsklick.</p>
 */
public final class StaffModeListener implements Listener {

    private final KlassenSMP plugin;

    public StaffModeListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getStaffModeManager().isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getStaffModeManager().isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getStaffModeManager().isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getStaffModeManager().isActive(player)) {
            event.setCancelled(true);
        }
    }

    /** Das Moderationsinventar darf nicht umsortiert werden. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && plugin.getStaffModeManager().isActive(player)
                && event.getInventory().equals(player.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getStaffModeManager().isActive(player)) {
            return;
        }
        if (!event.getAction().name().startsWith("RIGHT_CLICK")) {
            return;
        }
        event.setCancelled(true);

        switch (player.getInventory().getHeldItemSlot()) {
            case StaffModeManager.SLOT_PLAYERS, StaffModeManager.SLOT_INSPECT ->
                    new PlayerListGui(plugin).open(player);
            case StaffModeManager.SLOT_TELEPORT -> teleportRandom(player);
            case StaffModeManager.SLOT_VANISH -> {
                boolean vanished = plugin.getVanishManager().toggle(player);
                plugin.getMessages().send(player, vanished ? "vanish.enabled" : "vanish.disabled");
                plugin.getStaffModeManager().giveItems(player);
            }
            case StaffModeManager.SLOT_EXIT -> {
                plugin.getStaffModeManager().disable(player);
                plugin.getMessages().send(player, "staffmode.disabled");
            }
            default -> {
                // Freeze wird per Rechtsklick auf einen Spieler ausgeloest.
            }
        }
    }

    /** Springt zu einem zufaelligen anderen Spieler. */
    private void teleportRandom(Player player) {
        List<Player> targets = new ArrayList<>();
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) {
                targets.add(other);
            }
        }
        if (targets.isEmpty()) {
            plugin.getMessages().send(player, "staffmode.no-targets");
            return;
        }
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        plugin.getTeleportManager().teleportInstant(player, target.getLocation(), null);
        plugin.getMessages().send(player, "staffmode.teleported", "%player%", target.getName());
    }

    /** Rechtsklick auf einen Spieler oeffnet das Moderationsmenue bzw. friert ein. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractPlayer(PlayerInteractEntityEvent event) {
        Player staff = event.getPlayer();
        if (!plugin.getStaffModeManager().isActive(staff) || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        event.setCancelled(true);

        if (staff.getInventory().getHeldItemSlot() == StaffModeManager.SLOT_FREEZE) {
            if (!staff.hasPermission("klassensmp.freeze")) {
                plugin.getMessages().send(staff, "common.no-permission");
                return;
            }
            boolean frozen = plugin.getFreezeManager().toggle(target);
            plugin.getMessages().send(staff, frozen ? "moderation.freeze-on" : "moderation.freeze-off",
                    "%player%", target.getName());
            return;
        }
        new ModerationGui(plugin, target).open(staff);
    }
}
