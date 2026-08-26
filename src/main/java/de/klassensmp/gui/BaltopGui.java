package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Rangliste der reichsten Spieler. */
public final class BaltopGui extends PaginatedGui<PlayerData> {

    public BaltopGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("economy.gui-baltop-title"));
    }

    @Override
    protected List<PlayerData> entries(Player player) {
        return plugin.getPlayerDataManager().topBalances(200);
    }

    @Override
    protected ItemStack render(Player player, PlayerData data) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .name("&e" + data.getName())
                .lore(plugin.getMessages().list("economy.gui-baltop-lore",
                        "%money%", plugin.getEconomyManager().format(data.getMoney()),
                        "%bank%", plugin.getEconomyManager().format(data.getBank()),
                        "%total%", plugin.getEconomyManager().format(data.getMoney() + data.getBank()),
                        "%playtime%", TimeUtil.formatPlaytime(data.getTotalPlaytime())))
                .skullOwner(Bukkit.getOfflinePlayer(data.getUuid()))
                .build();
    }

    @Override
    protected void onEntryClick(Player player, PlayerData data, boolean rightClick) {
        if (player.hasPermission("klassensmp.stats.others")) {
            openLater(player, new StatsGui(plugin, data));
        }
    }

    @Override
    protected java.util.function.BiConsumer<Player, PaginatedGui<PlayerData>> backAction() {
        return (viewer, gui) -> {
            if (viewer.hasPermission("klassensmp.admin")) {
                openLater(viewer, new AdminGui(plugin));
            } else {
                closeLater(viewer);
            }
        };
    }
}
