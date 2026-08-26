package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Basis aller Oberflaechen.
 *
 * <p>Die GUI ist selbst der {@link InventoryHolder}. Dadurch laesst sich in den
 * Listenern zuverlaessig erkennen, ob ein Klick zu einer Plugin-Oberflaeche
 * gehoert - ohne Titel- oder Slot-Vergleiche, die sich faelschen liessen.</p>
 */
public abstract class Gui implements InventoryHolder {

    protected final KlassenSMP plugin;
    protected final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Gui(KlassenSMP plugin, String title, int rows) {
        this.plugin = plugin;
        int size = NumberUtil.clamp(rows, 1, 6) * 9;
        this.inventory = Bukkit.createInventory(this, size, Text.color(title));
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    /** Wird beim Oeffnen und bei jeder Aktualisierung aufgerufen. */
    protected abstract void build(Player player);

    public void open(Player player) {
        actions.clear();
        inventory.clear();
        build(player);
        player.openInventory(inventory);
    }

    /** Baut die Oberflaeche neu, ohne sie zu schliessen. */
    public void refresh(Player player) {
        actions.clear();
        inventory.clear();
        build(player);
        player.updateInventory();
    }

    /** Setzt ein Item mit zugehoeriger Aktion. */
    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** Fuellt alle leeren Slots mit einer dekorativen Scheibe. */
    protected void fillEmpty() {
        Material material = de.klassensmp.util.Compat.material(
                plugin.getConfigManager().string("gui.filler-material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
        ItemStack filler = new ItemBuilder(material).name("&r").build();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    /**
     * Verarbeitet einen Klick. Standardmaessig sind alle Klicks in der
     * Oberflaeche gesperrt; nur hinterlegte Aktionen werden ausgefuehrt.
     */
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        // Standardmaessig nichts zu tun.
    }

    /**
     * {@code true}, wenn der Spieler Items in dieser Oberflaeche frei bewegen
     * darf (nur fuer echte Container wie Graeber).
     */
    public boolean isContainer() {
        return false;
    }

    /** Schliesst die Oberflaeche im naechsten Tick (sicher innerhalb eines Events). */
    protected void closeLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
    }

    /**
     * Oeffnet eine andere Oberflaeche im naechsten Tick.
     *
     * <p>Innerhalb eines {@code InventoryClickEvent} darf nicht direkt ein
     * neues Inventar geoeffnet werden - Bukkit schliesst danach sonst die
     * frisch geoeffnete Oberflaeche wieder.</p>
     */
    protected void openLater(Player player, Gui gui) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                gui.open(player);
            }
        });
    }

    /** Oeffnet ein beliebiges Inventar im naechsten Tick (z.B. fuer /invsee). */
    protected void openLater(Player player, org.bukkit.inventory.Inventory target) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(target);
            }
        });
    }

    protected ItemStack backButton() {
        return new ItemBuilder(Material.ARROW)
                .name(plugin.getMessages().plain("gui.back"))
                .build();
    }

    protected ItemStack closeButton() {
        return new ItemBuilder(Material.BARRIER)
                .name(plugin.getMessages().plain("gui.close"))
                .build();
    }
}
