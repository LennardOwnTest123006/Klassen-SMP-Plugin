package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versteckt Teammitglieder vor anderen Spielern.
 *
 * <p>Es wird ausschliesslich {@code Player#hidePlayer(Plugin, Player)}
 * verwendet - eine reine Bukkit-API ohne Paketmanipulation.</p>
 */
public final class VanishManager {

    private final KlassenSMP plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return uuid != null && vanished.contains(uuid);
    }

    public int count() {
        return vanished.size();
    }

    /** @return {@code true}, wenn der Spieler jetzt unsichtbar ist. */
    public boolean toggle(Player player) {
        if (isVanished(player)) {
            show(player);
            return false;
        }
        hide(player);
        return true;
    }

    public void hide(Player player) {
        vanished.add(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && !other.hasPermission("klassensmp.vanish.see")) {
                other.hidePlayer(plugin, player);
            }
        }
        player.setCanPickupItems(false);
        plugin.getTabManager().requestUpdate();
    }

    public void show(Player player) {
        vanished.remove(player.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
        player.setCanPickupItems(true);
        plugin.getTabManager().requestUpdate();
    }

    /**
     * Wendet den Vanish-Zustand auf einen neu verbundenen Spieler an:
     * versteckte Spieler bleiben fuer ihn unsichtbar.
     */
    public void applyOnJoin(Player joining) {
        for (UUID uuid : vanished) {
            Player hidden = Bukkit.getPlayer(uuid);
            if (hidden != null && !hidden.equals(joining) && !joining.hasPermission("klassensmp.vanish.see")) {
                joining.hidePlayer(plugin, hidden);
            }
        }
    }

    public void handleQuit(Player player) {
        vanished.remove(player.getUniqueId());
    }
}
