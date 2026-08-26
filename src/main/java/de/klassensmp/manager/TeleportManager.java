package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.Compat;
import de.klassensmp.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fuehrt verzoegerte und sichere Teleportationen aus.
 *
 * <p>Waehrend der Verzoegerung darf sich der Spieler nicht bewegen und keinen
 * Schaden nehmen. Vor dem Teleport wird geprueft, ob das Ziel gefahrlos
 * betreten werden kann; andernfalls wird in der Umgebung nach einer sicheren
 * Position gesucht.</p>
 */
public final class TeleportManager {

    private final KlassenSMP plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public TeleportManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Ein laufender Teleportvorgang. */
    private record Pending(Location target, Location origin, BukkitTask task, String messageKey) {
    }

    public boolean hasPending(Player player) {
        return player != null && pending.containsKey(player.getUniqueId());
    }

    /**
     * Startet eine Teleportation.
     *
     * @param messageKey Nachrichtenschluessel fuer die Erfolgsmeldung
     */
    public void teleport(Player player, Location target, String messageKey) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }
        int delay = plugin.getConfigManager().integer("teleport.delay-seconds", 3);
        if (delay <= 0 || player.hasPermission("klassensmp.teleport.bypassdelay")) {
            execute(player, target, messageKey);
            return;
        }

        cancel(player, false);
        plugin.getMessages().send(player, "teleport.warmup", "%seconds%", String.valueOf(delay));

        Location origin = player.getLocation().clone();
        final int[] remaining = {delay};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel(player, false);
                    return;
                }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    Pending current = pending.remove(player.getUniqueId());
                    if (current != null) {
                        current.task().cancel();
                    }
                    execute(player, target, messageKey);
                }
            }
        }, 20L, 20L);

        pending.put(player.getUniqueId(), new Pending(target, origin, task, messageKey));
    }

    /** Teleportiert sofort, ohne Verzoegerung (z.B. fuer Moderationsbefehle). */
    public void teleportInstant(Player player, Location target, String messageKey) {
        execute(player, target, messageKey);
    }

    private void execute(Player player, Location target, String messageKey) {
        if (player == null || !player.isOnline() || target == null || target.getWorld() == null) {
            return;
        }
        Location destination = target;
        if (plugin.getConfigManager().bool("teleport.safe-teleport", true) && !LocationUtil.isSafe(target)) {
            Location safe = LocationUtil.findSafe(target,
                    plugin.getConfigManager().integer("teleport.safe-search-radius", 4),
                    plugin.getConfigManager().integer("teleport.safe-search-height", 6));
            if (safe == null) {
                plugin.getMessages().send(player, "teleport.unsafe");
                return;
            }
            destination = safe;
        }

        // Chunk vorab laden, damit der Teleport nicht mitten im Tick generiert.
        destination.getWorld().getChunkAt(destination);

        if (player.teleport(destination)) {
            if (messageKey != null) {
                plugin.getMessages().send(player, messageKey);
            }
            playEffects(player);
        } else {
            plugin.getMessages().send(player, "teleport.failed");
        }
    }

    private void playEffects(Player player) {
        if (plugin.getConfigManager().bool("sounds.enabled", true)) {
            Compat.playSound(player,
                    plugin.getConfigManager().string("sounds.teleport", "entity.enderman.teleport"),
                    0.7F, 1.2F);
        }
        if (plugin.getConfigManager().bool("particles.enabled", true)) {
            Compat.spawnParticle(player.getWorld(),
                    plugin.getConfigManager().string("particles.teleport", "PORTAL"),
                    player.getLocation().add(0, 1, 0), 25, 0.4D, 0.6D, 0.4D, 0.05D);
        }
    }

    /**
     * Bricht einen laufenden Teleport ab.
     *
     * @param notify ob der Spieler eine Meldung erhalten soll
     */
    public void cancel(Player player, boolean notify) {
        if (player == null) {
            return;
        }
        Pending current = pending.remove(player.getUniqueId());
        if (current == null) {
            return;
        }
        current.task().cancel();
        if (notify) {
            plugin.getMessages().send(player, "teleport.cancelled");
        }
    }

    /** Vom Bewegungs-Listener aufgerufen: bricht bei echter Positionsaenderung ab. */
    public void handleMove(Player player, Location from, Location to) {
        Pending current = pending.get(player.getUniqueId());
        if (current == null || to == null) {
            return;
        }
        if (!plugin.getConfigManager().bool("teleport.cancel-on-move", true)) {
            return;
        }
        // Reine Kopfbewegungen sollen den Teleport nicht abbrechen.
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            cancel(player, true);
        }
    }

    /** Vom Schadens-Listener aufgerufen. */
    public void handleDamage(Player player) {
        if (plugin.getConfigManager().bool("teleport.cancel-on-damage", true)) {
            cancel(player, true);
        }
    }

    public void handleQuit(Player player) {
        cancel(player, false);
    }
}
