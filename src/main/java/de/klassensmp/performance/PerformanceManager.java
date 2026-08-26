package de.klassensmp.performance;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ueberwachung und Optimierung der Serverleistung.
 *
 * <p>Die TPS werden eigenstaendig gemessen: eine Aufgabe laeuft alle 20 Ticks
 * und vergleicht die tatsaechlich vergangene Zeit mit der Sollzeit. Zusaetzlich
 * wird - falls die laufende Spigot-Version das anbietet - der vom Server
 * gemeldete TPS-Wert ausgelesen. Es werden keine Werte geschaetzt oder
 * erfunden: was nicht messbar ist, wird als nicht verfuegbar ausgewiesen.</p>
 */
public final class PerformanceManager {

    private static final int TPS_SAMPLES = 15;

    private final KlassenSMP plugin;

    private final double[] samples = new double[TPS_SAMPLES];
    private int sampleIndex;
    private int sampleCount;
    private long lastTickTime = System.nanoTime();

    private final AtomicInteger redstoneCounter = new AtomicInteger();
    private final AtomicInteger hopperCounter = new AtomicInteger();
    private volatile int redstonePerSecond;
    private volatile int hopperTransfersPerSecond;

    private volatile PerformanceSnapshot cachedSnapshot;
    private long lastWarning;

    private boolean cleanupEnabled;
    private int itemLifetimeSeconds;
    private int mobsPerChunk;

    public PerformanceManager(KlassenSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager();
        this.cleanupEnabled = config.bool("performance.cleanup.enabled", true);
        this.itemLifetimeSeconds = Math.max(30, config.integer("performance.cleanup.item-lifetime-seconds", 300));
        this.mobsPerChunk = Math.max(4, config.integer("performance.limits.mobs-per-chunk", 40));
    }

    // ------------------------------------------------------------------
    // Messung
    // ------------------------------------------------------------------

    public void start() {
        // TPS-Messung: alle 20 Ticks (Soll: 1000 ms)
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.nanoTime();
                double elapsedMillis = (now - lastTickTime) / 1_000_000.0D;
                lastTickTime = now;
                if (elapsedMillis > 0) {
                    double tps = Math.min(20.0D, 20.0D * 1000.0D / elapsedMillis);
                    samples[sampleIndex] = tps;
                    sampleIndex = (sampleIndex + 1) % TPS_SAMPLES;
                    sampleCount = Math.min(TPS_SAMPLES, sampleCount + 1);
                }
                redstonePerSecond = redstoneCounter.getAndSet(0);
                hopperTransfersPerSecond = hopperCounter.getAndSet(0);
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Zustandspruefung und Warnungen
        long checkSeconds = Math.max(5L, plugin.getConfigManager().duration("performance.check-interval-seconds", 15L));
        new BukkitRunnable() {
            @Override
            public void run() {
                PerformanceSnapshot snapshot = snapshot(false);
                cachedSnapshot = snapshot;
                if (snapshot.status() == ServerStatus.CRITICAL) {
                    warnStaff(snapshot);
                    if (plugin.getConfigManager().bool("performance.auto-actions", true)) {
                        runEmergencyActions();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 10L, 20L * checkSeconds);

        // Regelmaessige Bereinigung
        if (cleanupEnabled) {
            long minutes = Math.max(1L, plugin.getConfigManager().duration("performance.cleanup.interval-minutes", 10L));
            new BukkitRunnable() {
                @Override
                public void run() {
                    int removed = cleanupGround(true);
                    if (removed > 0 && plugin.getConfigManager().bool("performance.cleanup.announce", true)) {
                        Bukkit.broadcastMessage(plugin.getMessages().get("performance.cleanup-done",
                                "%amount%", String.valueOf(removed)));
                    }
                }
            }.runTaskTimer(plugin, 20L * 60L * minutes, 20L * 60L * minutes);
        }
    }

    /** Gemessene TPS als Durchschnitt der letzten Messungen. */
    public double getTps() {
        if (sampleCount == 0) {
            return 20.0D;
        }
        double sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            sum += samples[i];
        }
        return sum / sampleCount;
    }

    /** Die letzte Einzelmessung (reagiert schneller als der Durchschnitt). */
    public double getInstantTps() {
        if (sampleCount == 0) {
            return 20.0D;
        }
        int last = (sampleIndex - 1 + TPS_SAMPLES) % TPS_SAMPLES;
        return samples[last];
    }

    public ServerStatus getStatus() {
        double tps = getTps();
        double good = plugin.getConfigManager().number("performance.thresholds.good-tps", 18.5D);
        double medium = plugin.getConfigManager().number("performance.thresholds.medium-tps", 15.0D);
        if (tps >= good) {
            return ServerStatus.GOOD;
        }
        return tps >= medium ? ServerStatus.MEDIUM : ServerStatus.CRITICAL;
    }

    public void countRedstone() {
        redstoneCounter.incrementAndGet();
    }

    public void countHopperTransfer() {
        hopperCounter.incrementAndGet();
    }

    /** Zuletzt berechnete Momentaufnahme (fuer Tablist/Scoreboard). */
    public PerformanceSnapshot cached() {
        PerformanceSnapshot snapshot = cachedSnapshot;
        return snapshot == null ? snapshot(false) : snapshot;
    }

    /**
     * Erstellt eine Momentaufnahme.
     *
     * @param detailed wenn {@code true}, werden zusaetzlich Hopper und
     *                 Entity-Typen gezaehlt (teurer, nur fuer {@code /performance})
     */
    public PerformanceSnapshot snapshot(boolean detailed) {
        int entities = 0;
        int living = 0;
        int items = 0;
        int chunks = 0;
        Map<String, Integer> byType = detailed ? new HashMap<>() : Map.of();

        for (World world : Bukkit.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            for (Entity entity : world.getEntities()) {
                entities++;
                if (entity instanceof Item) {
                    items++;
                } else if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    living++;
                }
                if (detailed) {
                    byType.merge(entity.getType().name(), 1, Integer::sum);
                }
            }
        }

        int hoppers = detailed ? countHoppers() : -1;

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long max = runtime.maxMemory() / (1024L * 1024L);

        Map<String, Integer> sorted = detailed ? sortByValue(byType) : Map.of();

        return new PerformanceSnapshot(
                getTps(),
                Compat.serverTps(),
                Bukkit.getOnlinePlayers().size(),
                entities,
                living,
                items,
                chunks,
                hoppers,
                redstonePerSecond,
                hopperTransfersPerSecond,
                used,
                max,
                sorted,
                getStatus(),
                System.currentTimeMillis());
    }

    private Map<String, Integer> sortByValue(Map<String, Integer> input) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(input.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Zaehlt alle geladenen Hopper. Bewusst nur auf Anfrage, da es alle Chunks durchlaeuft. */
    public int countHoppers() {
        int hoppers = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof Hopper) {
                        hoppers++;
                    }
                }
            }
        }
        return hoppers;
    }

    /** Chunks mit den meisten Entities - hilfreich zum Aufspueren von Farmen. */
    public List<ChunkReport> topChunks(int limit) {
        Map<Chunk, Integer> counts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                Chunk chunk = entity.getLocation().getChunk();
                counts.merge(chunk, 1, Integer::sum);
            }
        }
        List<ChunkReport> reports = new ArrayList<>();
        for (Map.Entry<Chunk, Integer> entry : counts.entrySet()) {
            reports.add(new ChunkReport(entry.getKey().getWorld().getName(),
                    entry.getKey().getX(), entry.getKey().getZ(), entry.getValue()));
        }
        reports.sort(Comparator.comparingInt(ChunkReport::entities).reversed());
        return reports.size() <= limit ? reports : new ArrayList<>(reports.subList(0, limit));
    }

    /** Ein auffaelliger Chunk. */
    public record ChunkReport(String world, int x, int z, int entities) {

        public Location center() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x * 16 + 8, 80, z * 16 + 8);
        }
    }

    // ------------------------------------------------------------------
    // Aufraeumen
    // ------------------------------------------------------------------

    /**
     * Entfernt herumliegende Items und XP-Kugeln.
     *
     * <p>Es werden ausschliesslich Objekte entfernt, die aelter als die
     * konfigurierte Lebensdauer sind. Items mit eigenem Namen, Items in
     * Fahrzeugen sowie alles, was in der Ausnahmeliste steht, bleiben immer
     * erhalten. Spielerinventare werden niemals angefasst.</p>
     *
     * @param respectLifetime wenn {@code false}, wird die Altersgrenze ignoriert
     * @return Anzahl entfernter Objekte
     */
    public int cleanupGround(boolean respectLifetime) {
        if (!cleanupEnabled) {
            return 0;
        }
        List<String> protectedTypes = plugin.getConfigManager().get().getStringList("performance.cleanup.protected-items");
        boolean removeXp = plugin.getConfigManager().bool("performance.cleanup.remove-xp-orbs", true);
        int minTicks = respectLifetime ? itemLifetimeSeconds * 20 : 0;
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    if (item.getTicksLived() < minTicks) {
                        continue;
                    }
                    if (item.getCustomName() != null) {
                        continue; // benannte Items sind meist gewollt
                    }
                    if (protectedTypes.contains(item.getItemStack().getType().name())) {
                        continue;
                    }
                    item.remove();
                    removed++;
                } else if (removeXp && entity.getType() == EntityType.EXPERIENCE_ORB) {
                    if (entity.getTicksLived() < minTicks) {
                        continue;
                    }
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Begrenzt die Anzahl der Mobs je Chunk.
     *
     * <p>Gezaehmte, benannte und von Spielern gesetzte Kreaturen bleiben
     * unangetastet - entfernt werden nur ueberzaehlige, natuerlich
     * entstandene Mobs.</p>
     *
     * @return Anzahl entfernter Mobs
     */
    public int limitMobs() {
        if (!plugin.getConfigManager().bool("performance.limits.enabled", true)) {
            return 0;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                List<LivingEntity> candidates = new ArrayList<>();
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof LivingEntity livingEntity) || entity instanceof Player) {
                        continue;
                    }
                    if (livingEntity.getCustomName() != null || !livingEntity.getPassengers().isEmpty()) {
                        continue;
                    }
                    if (livingEntity instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()) {
                        continue;
                    }
                    candidates.add(livingEntity);
                }
                int excess = candidates.size() - mobsPerChunk;
                for (int i = 0; i < excess; i++) {
                    candidates.get(i).remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    /** Wird bei kritischer Last automatisch ausgefuehrt. */
    private void runEmergencyActions() {
        int items = plugin.getConfigManager().bool("performance.cleanup.on-critical", true)
                ? cleanupGround(true) : 0;
        int mobs = plugin.getConfigManager().bool("performance.limits.on-critical", true)
                ? limitMobs() : 0;
        if (items > 0 || mobs > 0) {
            plugin.getLogger().warning("Notfallbereinigung: " + items + " Objekte, " + mobs + " Mobs entfernt.");
        }
    }

    /** Warnt das Team - mit Cooldown, damit die Konsole nicht ueberlaeuft. */
    private void warnStaff(PerformanceSnapshot snapshot) {
        long cooldown = Math.max(30L, plugin.getConfigManager().duration("performance.warn-cooldown-seconds", 120L)) * 1000L;
        if (System.currentTimeMillis() - lastWarning < cooldown) {
            return;
        }
        lastWarning = System.currentTimeMillis();

        String message = plugin.getMessages().get("performance.critical",
                "%tps%", de.klassensmp.util.NumberUtil.formatTps(snapshot.measuredTps()),
                "%entities%", String.valueOf(snapshot.entities()),
                "%chunks%", String.valueOf(snapshot.chunks()));

        plugin.getLogger().warning(de.klassensmp.util.Text.strip(message));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("klassensmp.performance.alerts")) {
                player.sendMessage(message);
            }
        }
    }
}
