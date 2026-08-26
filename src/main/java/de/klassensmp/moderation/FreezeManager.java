package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Haelt Spieler fuer eine Ueberpruefung fest.
 *
 * <p>Eingefrorene Spieler koennen sich nicht bewegen, nichts abbauen und keine
 * Befehle ausser den erlaubten nutzen. Der Zustand ist bewusst fluechtig: nach
 * einem Neustart ist niemand mehr eingefroren.</p>
 */
public final class FreezeManager {

    private final KlassenSMP plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public FreezeManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(Player player) {
        return player != null && frozen.contains(player.getUniqueId());
    }

    /** @return {@code true}, wenn der Spieler jetzt eingefroren ist. */
    public boolean toggle(Player player) {
        if (frozen.remove(player.getUniqueId())) {
            plugin.getMessages().send(player, "moderation.unfrozen");
            return false;
        }
        frozen.add(player.getUniqueId());
        plugin.getMessages().send(player, "moderation.frozen");
        return true;
    }

    public void unfreeze(Player player) {
        frozen.remove(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        frozen.remove(player.getUniqueId());
    }

    public int count() {
        return frozen.size();
    }

    /** Befehle, die auch im eingefrorenen Zustand erlaubt bleiben. */
    public boolean isCommandAllowed(String command) {
        if (command == null) {
            return false;
        }
        String normalized = command.toLowerCase(java.util.Locale.ROOT);
        for (String allowed : plugin.getConfigManager().get().getStringList("moderation.freeze-allowed-commands")) {
            if (normalized.startsWith(allowed.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
