package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /spawn} und {@code /setspawn}. */
public final class SpawnCommands extends BaseCommand {

    public SpawnCommands(KlassenSMP plugin) {
        super(plugin, null, true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (name.equals("setspawn")) {
            if (!player.hasPermission("klassensmp.setspawn")) {
                plugin.getMessages().send(player, "common.no-permission");
                return;
            }
            plugin.getSpawnManager().setSpawn(player.getLocation());
            plugin.getMessages().send(player, "spawn.set");
            return;
        }

        if (!player.hasPermission("klassensmp.spawn")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (plugin.getPvpManager().isTagged(player)
                && !plugin.getConfigManager().bool("pvp.combat-tag.allow-teleport", false)) {
            plugin.getMessages().send(player, "pvp.combat-blocked",
                    "%time%", plugin.getPvpManager().remainingTagFormatted(player));
            return;
        }
        var spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            plugin.getMessages().send(player, "spawn.not-set");
            return;
        }
        plugin.getTeleportManager().teleport(player, spawn, "spawn.teleported");
    }
}
