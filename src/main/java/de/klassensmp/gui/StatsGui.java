package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Statistikuebersicht eines Spielers. */
public final class StatsGui extends Gui {

    private final PlayerData data;

    public StatsGui(KlassenSMP plugin, PlayerData data) {
        super(plugin, plugin.getMessages().plain("stats.gui-title", "%player%", data.getName()), 5);
        this.data = data;
    }

    @Override
    protected void build(Player player) {
        set(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a" + data.getName())
                .lore(plugin.getMessages().list("stats.gui-head-lore",
                        "%rank%", rankName(),
                        "%first%", TimeUtil.formatDate(data.getFirstJoin()),
                        "%last%", TimeUtil.formatDate(Math.max(data.getLastJoin(), data.getLastQuit())),
                        "%platform%", data.isBedrock()
                                ? plugin.getBoardManager().getPlaceholders().bedrockIcon()
                                : plugin.getBoardManager().getPlaceholders().javaIcon()))
                .skullOwner(Bukkit.getOfflinePlayer(data.getUuid()))
                .build());

        set(19, statItem(Material.DIAMOND_SWORD, "stats.gui-kills", String.valueOf(data.getKills())));
        set(20, statItem(Material.SKELETON_SKULL, "stats.gui-deaths", String.valueOf(data.getDeaths())));
        set(21, statItem(Material.ROTTEN_FLESH, "stats.gui-mobkills", String.valueOf(data.getMobKills())));
        set(22, statItem(Material.NETHER_STAR, "stats.gui-kdr",
                String.format(Locale.GERMANY, "%.2f", data.getKdr())));
        set(23, statItem(Material.DIAMOND_PICKAXE, "stats.gui-broken",
                NumberUtil.formatNumber(data.getBlocksBroken())));
        set(24, statItem(Material.BRICKS, "stats.gui-placed",
                NumberUtil.formatNumber(data.getBlocksPlaced())));
        set(25, statItem(Material.CLOCK, "stats.gui-playtime",
                TimeUtil.formatPlaytime(data.getTotalPlaytime())));

        set(30, statItem(Material.GOLD_INGOT, "stats.gui-money",
                plugin.getEconomyManager().format(data.getMoney())));
        set(31, statItem(Material.CHEST, "stats.gui-bank",
                plugin.getEconomyManager().format(data.getBank())));
        set(32, statItem(Material.EMERALD, "stats.gui-earned",
                plugin.getEconomyManager().format(data.getEarned())));

        set(40, closeButton(), event -> closeLater(player));
        fillEmpty();
    }

    private String rankName() {
        Player online = Bukkit.getPlayer(data.getUuid());
        return online == null
                ? plugin.getRankManager().getDefaultRank().displayName()
                : plugin.getRankManager().getRank(online).displayName();
    }

    private org.bukkit.inventory.ItemStack statItem(Material material, String key, String value) {
        return new ItemBuilder(material)
                .name(plugin.getMessages().plain(key))
                .lore("&f" + value)
                .hideAttributes()
                .build();
    }
}
