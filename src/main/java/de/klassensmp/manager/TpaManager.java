package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.CooldownMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teleportanfragen zwischen Spielern ({@code /tpa}, {@code /tpahere}).
 *
 * <p>Anfragen laufen nach einer konfigurierbaren Zeit automatisch ab. Ein
 * Spieler kann pro Ziel nur eine offene Anfrage haben.</p>
 */
public final class TpaManager {

    private final KlassenSMP plugin;

    /** Ziel-UUID -> offene Anfragen. */
    private final Map<UUID, Map<UUID, Request>> requests = new ConcurrentHashMap<>();
    private final CooldownMap cooldowns = new CooldownMap();

    public TpaManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Eine offene Teleportanfrage.
     *
     * @param here {@code true} = der Anfragende moechte, dass das Ziel zu ihm kommt
     */
    public record Request(UUID sender, UUID target, boolean here, long expires) {

        public boolean isExpired() {
            return System.currentTimeMillis() >= expires;
        }
    }

    /** Startet die regelmaessige Bereinigung abgelaufener Anfragen. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
                cooldowns.cleanup();
            }
        }.runTaskTimer(plugin, 20L * 30L, 20L * 30L);
    }

    private void cleanup() {
        for (Map.Entry<UUID, Map<UUID, Request>> entry : requests.entrySet()) {
            entry.getValue().entrySet().removeIf(e -> e.getValue().isExpired());
            if (entry.getValue().isEmpty()) {
                requests.remove(entry.getKey());
            }
        }
    }

    /** Ergebnis eines {@code /tpa}. */
    public enum SendResult {
        SUCCESS,
        SELF,
        COOLDOWN,
        ALREADY_PENDING
    }

    public SendResult send(Player sender, Player target, boolean here) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            return SendResult.SELF;
        }
        long cooldown = cooldowns.remaining(sender.getUniqueId());
        if (cooldown > 0 && !sender.hasPermission("klassensmp.tpa.nocooldown")) {
            return SendResult.COOLDOWN;
        }

        Map<UUID, Request> incoming = requests.computeIfAbsent(target.getUniqueId(), id -> new ConcurrentHashMap<>());
        Request existing = incoming.get(sender.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            return SendResult.ALREADY_PENDING;
        }

        long timeout = Math.max(5L, plugin.getConfigManager().duration("tpa.timeout-seconds", 60L));
        incoming.put(sender.getUniqueId(),
                new Request(sender.getUniqueId(), target.getUniqueId(), here,
                        System.currentTimeMillis() + timeout * 1000L));

        long cooldownSeconds = Math.max(0L, plugin.getConfigManager().duration("tpa.cooldown-seconds", 15L));
        cooldowns.set(sender.getUniqueId(), cooldownSeconds * 1000L);
        return SendResult.SUCCESS;
    }

    public long cooldownRemaining(UUID uuid) {
        return cooldowns.remaining(uuid);
    }

    /** Aelteste noch gueltige Anfrage an einen Spieler. */
    public Request oldestFor(UUID target) {
        Map<UUID, Request> incoming = requests.get(target);
        if (incoming == null) {
            return null;
        }
        Request best = null;
        for (Request request : incoming.values()) {
            if (request.isExpired()) {
                continue;
            }
            if (best == null || request.expires() < best.expires()) {
                best = request;
            }
        }
        return best;
    }

    public Request find(UUID target, UUID sender) {
        Map<UUID, Request> incoming = requests.get(target);
        if (incoming == null) {
            return null;
        }
        Request request = incoming.get(sender);
        return request == null || request.isExpired() ? null : request;
    }

    public void remove(UUID target, UUID sender) {
        Map<UUID, Request> incoming = requests.get(target);
        if (incoming != null) {
            incoming.remove(sender);
            if (incoming.isEmpty()) {
                requests.remove(target);
            }
        }
    }

    /** Namen aller Spieler mit offener Anfrage an {@code target} (fuer Tab-Completion). */
    public List<String> pendingSenderNames(UUID target) {
        Map<UUID, Request> incoming = requests.get(target);
        if (incoming == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Request request : incoming.values()) {
            if (request.isExpired()) {
                continue;
            }
            Player player = Bukkit.getPlayer(request.sender());
            if (player != null) {
                names.add(player.getName());
            }
        }
        return names;
    }

    /** Fuehrt eine akzeptierte Anfrage aus. */
    public boolean accept(Player target, Request request) {
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            return false;
        }
        remove(target.getUniqueId(), request.sender());

        if (request.here()) {
            plugin.getTeleportManager().teleport(target, sender.getLocation(), "teleport.success");
        } else {
            plugin.getTeleportManager().teleport(sender, target.getLocation(), "teleport.success");
        }
        return true;
    }

    public void handleQuit(UUID uuid) {
        requests.remove(uuid);
        for (Map<UUID, Request> incoming : requests.values()) {
            incoming.remove(uuid);
        }
        cooldowns.clear(uuid);
    }
}
