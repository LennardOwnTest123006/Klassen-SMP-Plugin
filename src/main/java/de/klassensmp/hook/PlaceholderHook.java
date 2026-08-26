package de.klassensmp.hook;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Anbindung an PlaceholderAPI.
 *
 * <p>KlassenSMP <i>verwendet</i> PlaceholderAPI: Alle konfigurierbaren Texte
 * (Tablist, Scoreboard, Chat) werden zusaetzlich durch PlaceholderAPI
 * geschickt, sofern das Plugin vorhanden ist. Die Anbindung laeuft ueber
 * Reflection, damit keine Compile-Abhaengigkeit noetig ist.</p>
 */
public final class PlaceholderHook {

    private final KlassenSMP plugin;

    private boolean available;
    private Method setPlaceholders;

    public PlaceholderHook(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        this.available = false;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.setPlaceholders = apiClass.getMethod("setPlaceholders",
                    Class.forName("org.bukkit.OfflinePlayer"), String.class);
            this.available = true;
            plugin.getLogger().info("PlaceholderAPI erkannt - externe Platzhalter werden aufgeloest.");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("PlaceholderAPI gefunden, aber nicht nutzbar: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Loest externe Platzhalter auf; ohne PlaceholderAPI bleibt der Text unveraendert. */
    public String apply(Player player, String text) {
        if (!available || player == null || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            Object result = setPlaceholders.invoke(null, player, text);
            return result instanceof String string ? string : text;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            this.available = false;
            plugin.getLogger().warning("PlaceholderAPI-Aufruf fehlgeschlagen, Integration deaktiviert.");
            return text;
        }
    }
}
