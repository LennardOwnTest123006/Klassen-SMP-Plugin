package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Grave;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Oberflaeche eines Grabes.
 *
 * <p>Anders als die uebrigen Oberflaechen ist dies ein echter Container: der
 * Spieler darf Items frei entnehmen. Beim Schliessen wird der verbleibende
 * Inhalt zurueck in das Grab geschrieben - dadurch existiert jedes Item zu
 * jedem Zeitpunkt genau einmal.</p>
 */
public final class GraveGui extends Gui {

    private final Grave grave;

    public GraveGui(KlassenSMP plugin, Grave grave) {
        super(plugin, plugin.getMessages().plain("graves.gui-title", "%id%", String.valueOf(grave.getId())),
                rowsFor(grave));
        this.grave = grave;
    }

    private static int rowsFor(Grave grave) {
        int items = de.klassensmp.util.ItemSerializer.compact(grave.getContents()).size();
        return Math.max(1, Math.min(6, (int) Math.ceil(items / 9.0D)));
    }

    @Override
    protected void build(Player player) {
        int slot = 0;
        for (ItemStack item : de.klassensmp.util.ItemSerializer.compact(grave.getContents())) {
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot++, item);
        }
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Freier Zugriff: der Inhalt gehoert dem Spieler.
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        Player player = event.getPlayer() instanceof Player p ? p : null;
        plugin.getGraveManager().handleClose(player, grave, inventory.getContents());
    }

    public Grave getGrave() {
        return grave;
    }
}
