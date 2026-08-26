package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Grave;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Interaktion mit Graebern. */
public final class GraveListener implements Listener {

    private final KlassenSMP plugin;

    public GraveListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Grave grave = plugin.getGraveManager().getGraveAt(event.getClickedBlock().getLocation());
        if (grave == null) {
            return;
        }
        event.setCancelled(true);
        openGrave(event.getPlayer(), grave);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Grave grave = plugin.getGraveManager().getGraveAt(event.getBlock().getLocation());
        if (grave == null) {
            return;
        }
        // Ein Grab wird nie abgebaut, sondern geoeffnet.
        event.setCancelled(true);
        openGrave(event.getPlayer(), grave);
    }

    private void openGrave(Player player, Grave grave) {
        boolean owner = grave.getOwner().equals(player.getUniqueId());
        if (!owner && !player.hasPermission("klassensmp.graves.others")) {
            plugin.getMessages().send(player, "graves.not-yours", "%player%", grave.getOwnerName());
            return;
        }
        plugin.getGraveManager().open(player, grave);
    }
}
