package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.performance.PerformanceManager;
import de.klassensmp.performance.PerformanceSnapshot;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performance-Uebersicht ({@code /performance}).
 *
 * <p>Alle Werte stammen aus tatsaechlich verfuegbaren Bukkit/Spigot-Daten
 * bzw. aus der eigenen TPS-Messung. Nicht verfuegbare Werte werden als
 * "nicht verfuegbar" ausgewiesen und nicht geschaetzt.</p>
 */
public final class PerformanceGui extends Gui {

    public PerformanceGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("performance.gui-title"), 6);
    }

    @Override
    protected void build(Player player) {
        PerformanceSnapshot snapshot = plugin.getPerformanceManager().snapshot(true);

        List<String> tpsLore = new ArrayList<>();
        tpsLore.add(plugin.getMessages().plain("performance.gui-tps-measured",
                "%tps%", NumberUtil.formatTps(snapshot.measuredTps())));
        if (snapshot.hasServerTps()) {
            tpsLore.add(plugin.getMessages().plain("performance.gui-tps-server",
                    "%tps%", NumberUtil.formatTps(snapshot.serverTps()[0])));
        } else {
            tpsLore.add(plugin.getMessages().plain("performance.gui-tps-unavailable"));
        }
        tpsLore.add(plugin.getMessages().plain("performance.gui-status",
                "%status%", snapshot.status().getDisplay(), "%icon%", snapshot.status().getIcon()));

        set(10, new ItemBuilder(Material.CLOCK)
                .name(plugin.getMessages().plain("performance.gui-tps"))
                .lore(tpsLore)
                .build());

        set(11, info(Material.PLAYER_HEAD, "performance.gui-players", String.valueOf(snapshot.players())));
        set(12, info(Material.ZOMBIE_HEAD, "performance.gui-entities",
                snapshot.entities() + " (" + snapshot.livingEntities() + " Mobs)"));
        set(13, info(Material.DROPPER, "performance.gui-items", String.valueOf(snapshot.items())));
        set(14, info(Material.GRASS_BLOCK, "performance.gui-chunks", String.valueOf(snapshot.chunks())));
        set(15, info(Material.HOPPER, "performance.gui-hoppers",
                snapshot.hoppers() < 0 ? "-" : String.valueOf(snapshot.hoppers())));
        set(16, info(Material.REDSTONE, "performance.gui-redstone",
                snapshot.redstonePerSecond() + "/s"));

        set(19, info(Material.COMPARATOR, "performance.gui-hopper-transfers",
                snapshot.hopperTransfersPerSecond() + "/s"));
        set(20, info(Material.IRON_BLOCK, "performance.gui-memory",
                snapshot.usedMemoryMb() + " / " + snapshot.maxMemoryMb() + " MB ("
                        + Math.round(snapshot.memoryPercent()) + "%)"));
        set(21, info(Material.BEACON, "performance.gui-boost",
                plugin.getServerBoostManager().getMode().getDisplay()));

        // Auffaellige Chunks
        List<PerformanceManager.ChunkReport> reports = plugin.getPerformanceManager().topChunks(5);
        List<String> chunkLore = new ArrayList<>();
        if (reports.isEmpty()) {
            chunkLore.add(plugin.getMessages().plain("performance.gui-no-hotspots"));
        } else {
            for (PerformanceManager.ChunkReport report : reports) {
                chunkLore.add(plugin.getMessages().plain("performance.gui-hotspot",
                        "%world%", report.world(),
                        "%x%", String.valueOf(report.x() * 16),
                        "%z%", String.valueOf(report.z() * 16),
                        "%entities%", String.valueOf(report.entities())));
            }
        }
        set(29, new ItemBuilder(Material.MAP)
                .name(plugin.getMessages().plain("performance.gui-hotspots"))
                .lore(chunkLore)
                .build());

        // Haeufigste Entity-Typen
        List<String> typeLore = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : snapshot.entitiesByType().entrySet()) {
            if (shown++ >= 8) {
                break;
            }
            typeLore.add(plugin.getMessages().plain("performance.gui-entity-type",
                    "%type%", entry.getKey(), "%amount%", String.valueOf(entry.getValue())));
        }
        set(31, new ItemBuilder(Material.SPAWNER)
                .name(plugin.getMessages().plain("performance.gui-entity-types"))
                .lore(typeLore)
                .build());

        // Aktionen
        if (player.hasPermission("klassensmp.performance.cleanup")) {
            set(33, new ItemBuilder(Material.LAVA_BUCKET)
                    .name(plugin.getMessages().plain("performance.gui-cleanup"))
                    .lore(plugin.getMessages().list("performance.gui-cleanup-lore"))
                    .build(), event -> {
                int removed = plugin.getPerformanceManager().cleanupGround(false);
                plugin.getMessages().send(player, "performance.cleanup-manual", "%amount%", String.valueOf(removed));
                refresh(player);
            });
        }

        set(49, closeButton(), event -> closeLater(player));
        set(48, new ItemBuilder(Material.SUNFLOWER)
                .name(plugin.getMessages().plain("performance.gui-refresh"))
                .build(), event -> refresh(player));

        fillEmpty();
    }

    private ItemStack info(Material material, String key, String value) {
        return new ItemBuilder(material)
                .name(plugin.getMessages().plain(key))
                .lore("&f" + value)
                .hideAttributes()
                .build();
    }
}
