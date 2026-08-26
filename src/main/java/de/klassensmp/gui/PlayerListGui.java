package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Spielerauswahl fuer das Moderationsmenue. */
public final class PlayerListGui extends PaginatedGui<Player> {

    public PlayerListGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("moderation.gui-list-title"));
    }

    @Override
    protected List<Player> entries(Player player) {
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    protected ItemStack render(Player viewer, Player target) {
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        int ping = Compat.ping(target);
        return new ItemBuilder(Material.PLAYER_HEAD)
                .name("&a" + target.getName())
                .lore(plugin.getMessages().list("moderation.gui-list-lore",
                        "%rank%", plugin.getRankManager().getRank(target).displayName(),
                        "%world%", target.getWorld().getName(),
                        "%ping%", ping < 0 ? "-" : String.valueOf(ping),
                        "%health%", String.valueOf(Math.round(target.getHealth())),
                        "%playtime%", data == null ? "-" : TimeUtil.formatPlaytime(data.getTotalPlaytime()),
                        "%vanished%", plugin.getVanishManager().isVanished(target)
                                ? plugin.getMessages().plain("common.yes")
                                : plugin.getMessages().plain("common.no")))
                .skullOwner(target)
                .build();
    }

    @Override
    protected void onEntryClick(Player viewer, Player target, boolean rightClick) {
        openLater(viewer, new ModerationGui(plugin, target));
    }
}
