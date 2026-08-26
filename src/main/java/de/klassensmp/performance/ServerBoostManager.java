package de.klassensmp.performance;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-Booster ({@code /serverboost}).
 *
 * <p>Ein Bukkit-Plugin kann die FPS eines Clients nicht erhoehen. Der Booster
 * optimiert deshalb ausschliesslich die <i>Serverleistung</i>: er begrenzt
 * Mob-Spawns, Hopper-Transfers und Redstone-Aktivitaet pro Chunk und
 * reduziert Partikel des Plugins.</p>
 *
 * <p>Alle Grenzwerte kommen aus der Config. {@link BoostMode#EXTREME} ist
 * niemals der Standard und muss bewusst aktiviert werden.</p>
 */
public final class ServerBoostManager {

    private final KlassenSMP plugin;

    private volatile BoostMode mode = BoostMode.NORMAL;

    /** Chunk-Schluessel -> Redstone-Ereignisse im aktuellen Zeitfenster. */
    private final Map<Long, AtomicInteger> redstonePerChunk = new ConcurrentHashMap<>();
    /** Chunk-Schluessel -> Hopper-Transfers im aktuellen Zeitfenster. */
    private final Map<Long, AtomicInteger> hopperPerChunk = new ConcurrentHashMap<>();

    public ServerBoostManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        BoostMode configured = BoostMode.parse(plugin.getConfigManager().string("serverboost.default-mode", "NORMAL"));
        // EXTREME wird bewusst nie automatisch uebernommen.
        this.mode = configured == null || configured == BoostMode.EXTREME ? BoostMode.NORMAL : configured;

        new BukkitRunnable() {
            @Override
            public void run() {
                redstonePerChunk.clear();
                hopperPerChunk.clear();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public BoostMode getMode() {
        return mode;
    }

    /** Setzt den Modus und meldet die Umstellung an das Team. */
    public void setMode(BoostMode newMode) {
        if (newMode == null || newMode == mode) {
            return;
        }
        this.mode = newMode;
        redstonePerChunk.clear();
        hopperPerChunk.clear();

        if (newMode == BoostMode.EXTREME) {
            int removed = plugin.getPerformanceManager().cleanupGround(false);
            int mobs = plugin.getPerformanceManager().limitMobs();
            plugin.getLogger().info("EXTREME-Modus: " + removed + " Objekte und " + mobs + " Mobs entfernt.");
        }

        String message = plugin.getMessages().get("serverboost.changed", "%mode%", newMode.getDisplay());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("klassensmp.serverboost")) {
                player.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private String path(String key) {
        return "serverboost.modes." + mode.name().toLowerCase(java.util.Locale.ROOT) + "." + key;
    }

    private int limit(String key, int fallback) {
        return plugin.getConfigManager().integer(path(key), fallback);
    }

    /** Partikel des Plugins werden im Boost-Modus reduziert. */
    public boolean allowParticles() {
        return plugin.getConfigManager().bool(path("particles"), mode == BoostMode.NORMAL);
    }

    /** Anzahl Partikel nach Modus-Faktor. */
    public int scaleParticles(int amount) {
        if (!allowParticles()) {
            return 0;
        }
        double factor = plugin.getConfigManager().number(path("particle-factor"), 1.0D);
        return Math.max(1, (int) Math.round(amount * Math.max(0.1D, Math.min(1.0D, factor))));
    }

    private static long chunkKey(Chunk chunk) {
        return ((long) chunk.getX() << 32) | (chunk.getZ() & 0xFFFFFFFFL);
    }

    /**
     * Prueft, ob in diesem Chunk noch ein Mob spawnen darf.
     *
     * @return {@code true}, wenn der Spawn erlaubt ist
     */
    public boolean allowMobSpawn(Chunk chunk) {
        int max = limit("mobs-per-chunk", -1);
        if (max <= 0) {
            return true;
        }
        int living = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                living++;
                if (living >= max) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Prueft, ob in diesem Chunk noch ein Hopper-Transfer erlaubt ist.
     * Ohne Begrenzung ({@code -1}) gibt die Methode immer {@code true} zurueck.
     */
    public boolean allowHopperTransfer(Chunk chunk) {
        int max = limit("hopper-transfers-per-second", -1);
        if (max <= 0) {
            return true;
        }
        AtomicInteger counter = hopperPerChunk.computeIfAbsent(chunkKey(chunk), key -> new AtomicInteger());
        return counter.incrementAndGet() <= max;
    }

    /** Prueft, ob in diesem Chunk noch Redstone-Aktivitaet erlaubt ist. */
    public boolean allowRedstone(Chunk chunk) {
        int max = limit("redstone-per-second", -1);
        if (max <= 0) {
            return true;
        }
        AtomicInteger counter = redstonePerChunk.computeIfAbsent(chunkKey(chunk), key -> new AtomicInteger());
        return counter.incrementAndGet() <= max;
    }

    /** Maximale Anzahl Item-Entities je Chunk; darueber werden neue Drops zusammengefasst. */
    public int maxItemsPerChunk() {
        return limit("items-per-chunk", -1);
    }
}
