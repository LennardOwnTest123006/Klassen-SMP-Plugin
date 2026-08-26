package de.klassensmp.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versionstolerante Zugriffe auf Bukkit/Spigot-Typen, die sich zwischen
 * Minecraft-Versionen strukturell veraendert haben (Enum -> Registry).
 *
 * <p>Alle Zugriffe sind rein Bukkit/Spigot-basiert. Es wird kein NMS und keine
 * Paper-API verwendet. Findet ein Lookup nichts, liefert er {@code null} bzw.
 * einen definierten Standardwert, statt eine Exception zu werfen.</p>
 */
public final class Compat {

    private static final Map<String, Enchantment> ENCHANT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object> PARTICLE_CACHE = new ConcurrentHashMap<>();
    private static final Object NULL_MARKER = new Object();

    private static Method serverGetTps;
    private static boolean serverGetTpsResolved;
    private static Method playerGetPing;
    private static boolean playerGetPingResolved;

    private Compat() {
    }

    /**
     * Sucht eine Verzauberung anhand ihres Namens ("sharpness", "unbreaking", ...).
     * Funktioniert sowohl mit Enum-basierten als auch mit Registry-basierten
     * Enchantment-Implementierungen.
     */
    public static Enchantment enchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        Enchantment cached = ENCHANT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Enchantment found = lookupEnchantment(key);
        if (found != null) {
            ENCHANT_CACHE.put(key, found);
        }
        return found;
    }

    private static Enchantment lookupEnchantment(String key) {
        // 1) Statisches Feld auf Enchantment (SHARPNESS, UNBREAKING, ... bzw. Enum-Konstante)
        try {
            Field field = Enchantment.class.getField(key.toUpperCase(Locale.ROOT));
            Object value = field.get(null);
            if (value instanceof Enchantment enchantment) {
                return enchantment;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // naechster Versuch
        }
        // 2) Registry-Lookup ueber den NamespacedKey
        try {
            Method byKey = Enchantment.class.getMethod("getByKey", org.bukkit.NamespacedKey.class);
            Object value = byKey.invoke(null, org.bukkit.NamespacedKey.minecraft(key));
            if (value instanceof Enchantment enchantment) {
                return enchantment;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nicht verfuegbar
        }
        return null;
    }

    /** Sucht ein Material anhand seines Namens; liefert {@code fallback}, wenn unbekannt. */
    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material == null ? fallback : material;
    }

    /**
     * Spielt einen Sound ab. Es wird bewusst die String-Variante der Bukkit-API
     * genutzt, damit neue oder umbenannte Sounds ohne Code-Aenderung funktionieren.
     */
    public static void playSound(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), soundKey.trim().toLowerCase(Locale.ROOT), volume, pitch);
        } catch (RuntimeException ignored) {
            // ungueltiger Sound-Name in der Config - bewusst still ignorieren
        }
    }

    /** Spawnt Partikel, falls der Partikeltyp auf dieser Serverversion existiert. */
    public static void spawnParticle(World world, String particleName, Location location, int count,
                                     double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || location == null || particleName == null || particleName.isBlank() || count <= 0) {
            return;
        }
        Object particle = particle(particleName);
        if (particle instanceof Particle typed) {
            try {
                world.spawnParticle(typed, location, count, offsetX, offsetY, offsetZ, extra);
            } catch (RuntimeException ignored) {
                // Partikel benoetigt zusaetzliche Daten - ueberspringen statt zu crashen
            }
        }
    }

    private static Object particle(String name) {
        String key = name.trim().toUpperCase(Locale.ROOT);
        Object cached = PARTICLE_CACHE.get(key);
        if (cached != null) {
            return cached == NULL_MARKER ? null : cached;
        }
        Object resolved = NULL_MARKER;
        try {
            Field field = Particle.class.getField(key);
            Object value = field.get(null);
            if (value instanceof Particle) {
                resolved = value;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            resolved = NULL_MARKER;
        }
        PARTICLE_CACHE.put(key, resolved);
        return resolved == NULL_MARKER ? null : resolved;
    }

    /**
     * Liefert die vom Server gemeldeten TPS-Werte, sofern die laufende
     * Spigot-Version diese API bereitstellt.
     *
     * @return TPS-Array (1m, 5m, 15m) oder {@code null}, wenn nicht verfuegbar.
     */
    public static double[] serverTps() {
        if (!serverGetTpsResolved) {
            serverGetTpsResolved = true;
            try {
                serverGetTps = Bukkit.getServer().getClass().getMethod("getTPS");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                serverGetTps = null;
            }
        }
        if (serverGetTps == null) {
            return null;
        }
        try {
            Object result = serverGetTps.invoke(Bukkit.getServer());
            if (result instanceof double[] values && values.length > 0) {
                return values;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            serverGetTps = null;
        }
        return null;
    }

    /**
     * Liefert den Ping eines Spielers. Ist {@code Player#getPing()} auf der
     * laufenden Version nicht vorhanden, wird -1 zurueckgegeben.
     */
    public static int ping(Player player) {
        if (player == null) {
            return -1;
        }
        if (!playerGetPingResolved) {
            playerGetPingResolved = true;
            try {
                playerGetPing = Player.class.getMethod("getPing");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                playerGetPing = null;
            }
        }
        if (playerGetPing == null) {
            return -1;
        }
        try {
            Object value = playerGetPing.invoke(player);
            if (value instanceof Integer ping) {
                return Math.max(0, ping);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            playerGetPing = null;
        }
        return -1;
    }
}
