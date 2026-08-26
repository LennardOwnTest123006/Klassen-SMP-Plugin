package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Leitet Klicks an die jeweilige Oberflaeche weiter.
 *
 * <p>Erkannt wird eine Plugin-Oberflaeche ausschliesslich am
 * {@link InventoryHolder} - Titel oder Slot-Positionen lassen sich faelschen,
 * der Holder nicht. Klicks ausserhalb der eigentlichen Oberflaeche
 * (Spielerinventar) werden ebenfalls gesperrt, damit keine Items
 * hineingeschoben werden koennen.</p>
 */
public final class GuiListener implements Listener {

    private final KlassenSMP plugin;

    public GuiListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Gui gui)) {
            return;
        }
        if (gui.isContainer()) {
            return; // echter Container (Grab) - freier Zugriff
        }

        // Shift-Klicks und Zahlentasten aus dem Spielerinventar ebenfalls sperren.
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            event.setCancelled(true);
            return;
        }
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui && !gui.isContainer()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui) {
            gui.handleClose(event);
        }
        if (event.getPlayer() instanceof Player player && plugin.getStaffModeManager().isActive(player)) {
            // Moderationsitems nach dem Schliessen sicherstellen.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.getStaffModeManager().isActive(player)) {
                    plugin.getStaffModeManager().giveItems(player);
                }
            });
        }
    }
}
