package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Uebersicht aller geladenen Welten. */
public final class WorldGui extends PaginatedGui<World> {

    public WorldGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("worlds.gui-title"));
    }

    @Override
    protected List<World> entries(Player player) {
        return plugin.getWorldManager().worlds();
    }

    @Override
    protected ItemStack render(Player player, World world) {
        Material icon = switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
        return new ItemBuilder(icon)
                .name("&a" + world.getName())
                .lore(plugin.getMessages().list("worlds.gui-entry-lore",
                        "%environment%", world.getEnvironment().name(),
                        "%players%", String.valueOf(world.getPlayers().size()),
                        "%chunks%", String.valueOf(world.getLoadedChunks().length),
                        "%entities%", String.valueOf(world.getEntities().size()),
                        "%difficulty%", world.getDifficulty().name(),
                        "%pvp%", world.getPVP()
                                ? plugin.getMessages().plain("common.yes")
                                : plugin.getMessages().plain("common.no"),
                        "%time%", String.valueOf(world.getTime())))
                .build();
    }

    @Override
    protected void onEntryClick(Player player, World world, boolean rightClick) {
        if (!player.hasPermission("klassensmp.admin")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        closeLater(player);
        plugin.getTeleportManager().teleportInstant(player, world.getSpawnLocation(), "worlds.teleported");
    }

    @Override
    protected java.util.function.BiConsumer<Player, PaginatedGui<World>> backAction() {
        return (player, gui) -> openLater(player, new AdminGui(plugin));
    }
}
