package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Misst und begrenzt lastintensive Vorgaenge.
 *
 * <p>Die Zaehler liefern die Werte fuer {@code /performance}. Begrenzt wird
 * nur, wenn der Server-Booster das ausdruecklich vorsieht - im Modus
 * {@code NORMAL} greift keine dieser Regeln.</p>
 */
public final class PerformanceListener implements Listener {

    private final KlassenSMP plugin;

    public PerformanceListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRedstone(BlockRedstoneEvent event) {
        plugin.getPerformanceManager().countRedstone();

        Chunk chunk = event.getBlock().getChunk();
        if (!plugin.getServerBoostManager().allowRedstone(chunk)) {
            // Signal auf den alten Wert setzen = Aenderung wird verworfen.
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        plugin.getPerformanceManager().countHopperTransfer();

        var holder = event.getDestination().getHolder();
        if (!(holder instanceof org.bukkit.block.BlockState state)) {
            return;
        }
        if (!plugin.getServerBoostManager().allowHopperTransfer(state.getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Vom Spieler gesetzte oder gezuechtete Kreaturen werden nie blockiert.
        switch (event.getSpawnReason()) {
            case SPAWNER_EGG, BREEDING, CUSTOM, DISPENSE_EGG, BUILD_IRONGOLEM, BUILD_SNOWMAN, BUILD_WITHER -> {
                return;
            }
            default -> {
                // natuerliche Spawns werden geprueft
            }
        }
        if (!plugin.getServerBoostManager().allowMobSpawn(event.getLocation().getChunk())) {
            event.setCancelled(true);
        }
    }

    /**
     * Begrenzt die Anzahl liegender Items je Chunk.
     * Es werden nur <i>neue</i> Drops verhindert - bereits liegende Items
     * bleiben unangetastet.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        int max = plugin.getServerBoostManager().maxItemsPerChunk();
        if (max <= 0) {
            return;
        }
        Chunk chunk = event.getLocation().getChunk();
        int items = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item) {
                items++;
                if (items >= max) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
