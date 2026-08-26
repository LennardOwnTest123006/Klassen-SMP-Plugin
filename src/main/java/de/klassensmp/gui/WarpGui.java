package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Warp;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Uebersicht aller nutzbaren Warps. */
public final class WarpGui extends PaginatedGui<Warp> {

    public WarpGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("warps.gui-title"));
    }

    @Override
    protected List<Warp> entries(Player player) {
        return plugin.getWarpManager().visibleFor(player);
    }

    @Override
    protected ItemStack render(Player player, Warp warp) {
        Material icon = Compat.material(warp.icon(), Material.ENDER_PEARL);
        return new ItemBuilder(icon)
                .name(plugin.getMessages().plain("warps.gui-entry", "%warp%", warp.name()))
                .lore(plugin.getMessages().list("warps.gui-entry-lore",
                        "%location%", LocationUtil.pretty(warp.location()),
                        "%world%", warp.location().getWorld() == null ? "-" : warp.location().getWorld().getName()))
                .build();
    }

    @Override
    protected void onEntryClick(Player player, Warp warp, boolean rightClick) {
        closeLater(player);
        if (!plugin.getWarpManager().canUse(player, warp)) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        plugin.getTeleportManager().teleport(player, warp.location(), "warps.teleported");
    }
}
