package de.klassensmp.hook;

import de.klassensmp.KlassenSMP;
import org.bukkit.entity.Player;

/** Buendelt alle optionalen Plugin-Anbindungen. */
public final class HookManager {

    private final FloodgateHook floodgate;
    private final PlaceholderHook placeholders;
    private final VaultHook vault;

    public HookManager(KlassenSMP plugin) {
        this.floodgate = new FloodgateHook(plugin);
        this.placeholders = new PlaceholderHook(plugin);
        this.vault = new VaultHook(plugin);
    }

    /** Erkennt alle optionalen Abhaengigkeiten. Fehlende werden nur protokolliert. */
    public void setup() {
        floodgate.setup();
        placeholders.setup();
        vault.setup();
    }

    public void shutdown() {
        vault.shutdown();
    }

    public FloodgateHook floodgate() {
        return floodgate;
    }

    public PlaceholderHook placeholders() {
        return placeholders;
    }

    public VaultHook vault() {
        return vault;
    }

    /** Loest externe Platzhalter auf, sofern PlaceholderAPI vorhanden ist. */
    public String applyPlaceholders(Player player, String text) {
        return placeholders.apply(player, text);
    }
}
