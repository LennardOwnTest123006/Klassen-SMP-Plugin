package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.List;

/**
 * Registriert alle Listener an genau einer Stelle.
 *
 * <p>Beim Deaktivieren des Plugins hebt Bukkit die Registrierung automatisch
 * auf, sodass keine verwaisten Listener zurueckbleiben.</p>
 */
public final class ListenerRegistry {

    private final KlassenSMP plugin;

    public ListenerRegistry(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        List<Listener> listeners = List.of(
                new ConnectionListener(plugin),
                new ChatListener(plugin),
                new PlayerListener(plugin),
                new CombatListener(plugin),
                new ProtectionListener(plugin),
                new StatsListener(plugin),
                new GuiListener(plugin),
                new PerformanceListener(plugin),
                new StaffModeListener(plugin),
                new GraveListener(plugin));

        PluginManager manager = plugin.getServer().getPluginManager();
        for (Listener listener : listeners) {
            manager.registerEvents(listener, plugin);
        }
        plugin.getLogger().info(listeners.size() + " Listener registriert.");
    }
}
