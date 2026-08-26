package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.performance.BoostMode;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Hauptmenue der Serververwaltung ({@code /smpadmin}). */
public final class AdminGui extends Gui {

    public AdminGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("admin.gui-title"), 5);
    }

    @Override
    protected void build(Player player) {
        set(10, new ItemBuilder(Material.PLAYER_HEAD)
                .name(plugin.getMessages().plain("admin.gui-players"))
                .lore(plugin.getMessages().list("admin.gui-players-lore",
                        "%online%", String.valueOf(Bukkit.getOnlinePlayers().size()),
                        "%known%", String.valueOf(plugin.getPlayerDataManager().size())))
                .build(), event -> openLater(player, new PlayerListGui(plugin)));

        set(11, new ItemBuilder(Material.CLOCK)
                .name(plugin.getMessages().plain("admin.gui-performance"))
                .lore(plugin.getMessages().list("admin.gui-performance-lore",
                        "%tps%", NumberUtil.formatTps(plugin.getPerformanceManager().getTps()),
                        "%status%", plugin.getPerformanceManager().getStatus().getDisplay()))
                .build(), event -> openLater(player, new PerformanceGui(plugin)));

        set(12, new ItemBuilder(Material.BEACON)
                .name(plugin.getMessages().plain("admin.gui-boost"))
                .lore(plugin.getMessages().list("admin.gui-boost-lore",
                        "%mode%", plugin.getServerBoostManager().getMode().getDisplay()))
                .build(), event -> openLater(player, new BoostGui(plugin)));

        set(13, new ItemBuilder(Material.GRASS_BLOCK)
                .name(plugin.getMessages().plain("admin.gui-worlds"))
                .lore(plugin.getMessages().list("admin.gui-worlds-lore",
                        "%worlds%", String.valueOf(Bukkit.getWorlds().size()),
                        "%chunks%", String.valueOf(plugin.getWorldManager().loadedChunks())))
                .build(), event -> openLater(player, new WorldGui(plugin)));

        set(14, new ItemBuilder(Material.IRON_SWORD)
                .name(plugin.getMessages().plain("admin.gui-moderation"))
                .lore(plugin.getMessages().list("admin.gui-moderation-lore",
                        "%vanished%", String.valueOf(plugin.getVanishManager().count()),
                        "%frozen%", String.valueOf(plugin.getFreezeManager().count())))
                .hideAttributes()
                .build(), event -> openLater(player, new PlayerListGui(plugin)));

        set(15, new ItemBuilder(Material.CHEST)
                .name(plugin.getMessages().plain("admin.gui-backups"))
                .lore(plugin.getMessages().list("admin.gui-backups-lore",
                        "%count%", String.valueOf(plugin.getBackupManager().listBackups().size())))
                .build(), event -> openLater(player, new BackupGui(plugin)));

        set(16, new ItemBuilder(Material.FIREWORK_ROCKET)
                .name(plugin.getMessages().plain("admin.gui-events"))
                .lore(plugin.getMessages().list("admin.gui-events-lore",
                        "%active%", plugin.getServerEventManager().isRunning()
                                ? plugin.getServerEventManager().getActive().displayName()
                                : plugin.getMessages().plain("common.none")))
                .build(), event -> openLater(player, new EventGui(plugin)));

        set(21, new ItemBuilder(Material.GOLD_INGOT)
                .name(plugin.getMessages().plain("admin.gui-economy"))
                .lore(economyLore())
                .build(), event -> openLater(player, new BaltopGui(plugin)));

        set(22, new ItemBuilder(Material.BOOK)
                .name(plugin.getMessages().plain("admin.gui-config"))
                .lore(plugin.getMessages().list("admin.gui-config-lore",
                        "%database%", plugin.getDatabase().isMysql() ? "MySQL" : "SQLite",
                        "%floodgate%", plugin.getHooks().floodgate().isAvailable()
                                ? plugin.getMessages().plain("common.yes")
                                : plugin.getMessages().plain("common.no"),
                        "%vault%", plugin.getHooks().vault().isRegistered()
                                ? plugin.getMessages().plain("common.yes")
                                : plugin.getMessages().plain("common.no"),
                        "%papi%", plugin.getHooks().placeholders().isAvailable()
                                ? plugin.getMessages().plain("common.yes")
                                : plugin.getMessages().plain("common.no")))
                .build());

        set(23, new ItemBuilder(Material.SUNFLOWER)
                .name(plugin.getMessages().plain("admin.gui-reload"))
                .lore(plugin.getMessages().list("admin.gui-reload-lore"))
                .build(), event -> openLater(player, new ConfirmGui(plugin,
                plugin.getMessages().plain("admin.gui-reload-confirm"),
                plugin.getMessages().list("admin.gui-reload-lore"),
                confirmed -> {
                    plugin.reloadEverything();
                    plugin.getMessages().send(confirmed, "admin.reloaded");
                },
                cancelled -> new AdminGui(plugin).open(cancelled))));

        set(40, closeButton(), event -> closeLater(player));
        fillEmpty();
    }

    private List<String> economyLore() {
        List<String> lore = new ArrayList<>();
        double total = 0;
        for (var data : plugin.getPlayerDataManager().topBalances(Integer.MAX_VALUE)) {
            total += data.getMoney() + data.getBank();
        }
        lore.addAll(plugin.getMessages().list("admin.gui-economy-lore",
                "%total%", plugin.getEconomyManager().format(total),
                "%currency%", plugin.getEconomyManager().getCurrencyPlural()));
        return lore;
    }

    /** Untermenue fuer den Server-Booster. */
    public static final class BoostGui extends Gui {

        public BoostGui(KlassenSMP plugin) {
            super(plugin, plugin.getMessages().plain("serverboost.gui-title"), 3);
        }

        @Override
        protected void build(Player player) {
            option(player, 11, BoostMode.NORMAL, Material.LIME_WOOL);
            option(player, 13, BoostMode.PERFORMANCE, Material.YELLOW_WOOL);
            option(player, 15, BoostMode.EXTREME, Material.RED_WOOL);
            set(22, backButton(), event -> openLater(player, new AdminGui(plugin)));
            fillEmpty();
        }

        private void option(Player player, int slot, BoostMode target, Material material) {
            boolean current = plugin.getServerBoostManager().getMode() == target;
            ItemBuilder builder = new ItemBuilder(material)
                    .name(plugin.getMessages().plain("serverboost.gui-" + target.name().toLowerCase(java.util.Locale.ROOT)))
                    .lore(plugin.getMessages().list("serverboost.gui-" + target.name().toLowerCase(java.util.Locale.ROOT) + "-lore"));
            if (current) {
                builder.glow();
            }
            set(slot, builder.build(), event -> {
                if (!player.hasPermission("klassensmp.serverboost")) {
                    plugin.getMessages().send(player, "common.no-permission");
                    return;
                }
                if (target == BoostMode.EXTREME) {
                    openLater(player, new ConfirmGui(plugin,
                            plugin.getMessages().plain("serverboost.confirm-extreme"),
                            plugin.getMessages().list("serverboost.confirm-extreme-lore"),
                            confirmed -> plugin.getServerBoostManager().setMode(BoostMode.EXTREME),
                            cancelled -> new BoostGui(plugin).open(cancelled)));
                    return;
                }
                plugin.getServerBoostManager().setMode(target);
                refresh(player);
            });
        }
    }
}
