package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grundschutz gegen Verbindungsfluten.
 *
 * <p>Bei ungewoehnlich vielen Verbindungen in kurzer Zeit geht der Server in
 * einen zeitlich begrenzten Schutzmodus: neue Verbindungen werden freundlich
 * abgewiesen, bereits bekannte Spieler und Spieler mit Bypass-Recht kommen
 * weiterhin durch. Es wird niemals automatisch gebannt und es werden keine
 * IP-Adressen protokolliert.</p>
 */
public final class AntiBotManager {

    private final KlassenSMP plugin;

    private final Deque<Long> recentJoins = new ArrayDeque<>();
    /** Gehashte Adresse -> Zeitpunkte der letzten Verbindungsversuche. */
    private final Map<Integer, Deque<Long>> attemptsPerAddress = new ConcurrentHashMap<>();

    private volatile long lockdownUntil;

    public AntiBotManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long limit = System.currentTimeMillis() - 60_000L;
                synchronized (recentJoins) {
                    recentJoins.removeIf(time -> time < limit);
                }
                attemptsPerAddress.values().forEach(deque -> {
                    synchronized (deque) {
                        deque.removeIf(time -> time < limit);
                    }
                });
                attemptsPerAddress.entrySet().removeIf(entry -> {
                    synchronized (entry.getValue()) {
                        return entry.getValue().isEmpty();
                    }
                });
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
    }

    public boolean isLockdownActive() {
        return System.currentTimeMillis() < lockdownUntil;
    }

    public long lockdownRemaining() {
        return Math.max(0L, lockdownUntil - System.currentTimeMillis());
    }

    /** Hebt den Schutzmodus vorzeitig auf. */
    public void clearLockdown() {
        this.lockdownUntil = 0L;
    }

    /**
     * Prueft eine eingehende Verbindung.
     *
     * <p>Wird aus {@code AsyncPlayerPreLoginEvent} aufgerufen und muss deshalb
     * threadsicher sein.</p>
     *
     * @return {@code null}, wenn die Verbindung erlaubt ist, sonst der Ablehnungsgrund
     */
    public String checkConnection(java.util.UUID uuid, InetAddress address) {
        if (!plugin.getConfigManager().bool("antibot.enabled", true)) {
            return null;
        }
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, plugin.getConfigManager().duration("antibot.window-seconds", 8L) * 1000L);
        int maxJoins = Math.max(2, plugin.getConfigManager().integer("antibot.max-joins", 8));
        int maxPerAddress = Math.max(1, plugin.getConfigManager().integer("antibot.max-attempts-per-address", 4));

        // Bekannte Spieler duerfen auch im Schutzmodus verbinden.
        boolean known = plugin.getPlayerDataManager().get(uuid) != null;

        if (address != null) {
            // Es wird nur ein Hash gespeichert, nie die Adresse selbst.
            int key = address.getHostAddress().hashCode();
            Deque<Long> attempts = attemptsPerAddress.computeIfAbsent(key, id -> new ArrayDeque<>());
            synchronized (attempts) {
                attempts.removeIf(time -> time < now - windowMillis);
                attempts.addLast(now);
                if (attempts.size() > maxPerAddress && !known) {
                    return plugin.getMessages().plain("antibot.too-many-attempts");
                }
            }
        }

        synchronized (recentJoins) {
            recentJoins.removeIf(time -> time < now - windowMillis);
            recentJoins.addLast(now);
            if (recentJoins.size() > maxJoins && !isLockdownActive()) {
                long seconds = Math.max(5L, plugin.getConfigManager().duration("antibot.lockdown-seconds", 30L));
                this.lockdownUntil = now + seconds * 1000L;
                notifyStaff(seconds);
            }
        }

        if (isLockdownActive() && !known) {
            return plugin.getMessages().plain("antibot.lockdown");
        }
        return null;
    }

    private void notifyStaff(long seconds) {
        plugin.getLogger().warning("Ungewoehnlich viele Verbindungen - Schutzmodus fuer "
                + seconds + " Sekunden aktiv.");
        // Nachrichten an Spieler muessen auf dem Main Thread laufen.
        Bukkit.getScheduler().runTask(plugin, () -> {
            String message = plugin.getMessages().get("antibot.staff-alert", "%seconds%", String.valueOf(seconds));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("klassensmp.admin")) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
