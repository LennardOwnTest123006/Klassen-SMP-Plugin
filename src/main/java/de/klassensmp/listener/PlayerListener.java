package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Bewegung, Weltwechsel, Respawn und Hunger. */
public final class PlayerListener implements Listener {

    private final KlassenSMP plugin;

    public PlayerListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Bewegungs-Events treten sehr haeufig auf. Deshalb wird zuerst geprueft,
     * ob sich der Block ueberhaupt geaendert hat - reine Kopfbewegungen werden
     * sofort verworfen.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        boolean blockChanged = from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();

        Player player = event.getPlayer();

        if (plugin.getFreezeManager().isFrozen(player)) {
            if (blockChanged) {
                event.setTo(from);
                plugin.getMessages().send(player, "moderation.frozen-move");
            }
            return;
        }

        if (!blockChanged) {
            return;
        }

        plugin.getTeleportManager().handleMove(player, from, to);
        plugin.getServerEventManager().handleMove(player, to);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfigManager().bool("spawn.respawn-at-spawn", false)) {
            return;
        }
        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World.Environment environment = player.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER) {
            plugin.getAchievementManager().checkNether(player);
        } else if (environment == World.Environment.THE_END) {
            plugin.getAchievementManager().checkEnd(player);
        }
        plugin.getTabManager().requestUpdate();
    }

    /** Im Moderationsmodus wird der Hunger eingefroren. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getStaffModeManager().isActive(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }
}
