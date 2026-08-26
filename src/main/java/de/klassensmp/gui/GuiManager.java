package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Kleine Hilfsklasse rund um die Oberflaechen. */
public final class GuiManager {

    private final KlassenSMP plugin;

    public GuiManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public KlassenSMP getPlugin() {
        return plugin;
    }

    /** @return die aktuell geoeffnete Plugin-Oberflaeche oder {@code null}. */
    public Gui openGui(Player player) {
        if (player == null) {
            return null;
        }
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof Gui gui ? gui : null;
    }

    /**
     * Schliesst alle Plugin-Oberflaechen.
     * Wird beim Deaktivieren aufgerufen, damit keine Items in einer
     * verwaisten Oberflaeche liegen bleiben.
     */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (openGui(player) != null) {
                player.closeInventory();
            }
        }
    }
}
