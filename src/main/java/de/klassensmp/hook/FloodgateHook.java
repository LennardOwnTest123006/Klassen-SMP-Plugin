package de.klassensmp.hook;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock-Erkennung ueber Floodgate.
 *
 * <p>Die Anbindung erfolgt ausschliesslich per Reflection, damit weder eine
 * Compile-Abhaengigkeit noch ein zusaetzliches Maven-Repository benoetigt wird.
 * Fehlt Floodgate, meldet der Hook einfach, dass keine Bedrock-Erkennung
 * moeglich ist - das Plugin laeuft dann ganz normal weiter.</p>
 */
public final class FloodgateHook {

    private final KlassenSMP plugin;

    private boolean available;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private String prefix = "";

    public FloodgateHook(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        this.available = false;
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null
                && Bukkit.getPluginManager().getPlugin("Floodgate") == null) {
            plugin.getLogger().info("Floodgate nicht gefunden - Bedrock-Erkennung ist deaktiviert.");
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            this.floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            this.isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            try {
                Object rawPrefix = apiClass.getMethod("getPlayerPrefix").invoke(floodgateApi);
                this.prefix = rawPrefix == null ? "" : rawPrefix.toString();
            } catch (ReflectiveOperationException ignored) {
                this.prefix = "";
            }
            this.available = floodgateApi != null && isFloodgatePlayer != null;
            if (available) {
                plugin.getLogger().info("Floodgate erkannt - Bedrock-Spieler werden unterschieden.");
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Floodgate gefunden, aber die API ist nicht nutzbar: " + ex.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Prueft, ob ein Spieler ueber Bedrock verbunden ist.
     *
     * <p>Primaer wird die Floodgate-API befragt. Als Rueckfallebene dient der
     * konfigurierte Namens-Prefix (Standard {@code .}), den Floodgate
     * Bedrock-Spielern voranstellt.</p>
     */
    public boolean isBedrock(UUID uuid, String name) {
        if (uuid == null) {
            return false;
        }
        if (available) {
            try {
                Object result = isFloodgatePlayer.invoke(floodgateApi, uuid);
                if (result instanceof Boolean bedrock) {
                    return bedrock;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                // API nicht mehr nutzbar - dauerhaft abschalten statt jeden Join zu spammen
                this.available = false;
                plugin.getLogger().warning("Floodgate-API-Zugriff fehlgeschlagen, Bedrock-Erkennung deaktiviert.");
            }
        }
        return !prefix.isEmpty() && name != null && name.startsWith(prefix);
    }

    public boolean isBedrock(Player player) {
        return player != null && isBedrock(player.getUniqueId(), player.getName());
    }

    /** {@code true}, wenn Geyser auf diesem Server laeuft. */
    public boolean isGeyserPresent() {
        return Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null
                || Bukkit.getPluginManager().getPlugin("Geyser") != null;
    }
}
