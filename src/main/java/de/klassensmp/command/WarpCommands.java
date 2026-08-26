package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.WarpGui;
import de.klassensmp.model.Warp;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** {@code /warp}, {@code /setwarp} und {@code /delwarp}. */
public final class WarpCommands extends BaseCommand {

    public WarpCommands(KlassenSMP plugin) {
        super(plugin, null, true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        switch (name) {
            case "setwarp" -> setWarp(player, args);
            case "delwarp" -> deleteWarp(player, args);
            default -> useWarp(player, args);
        }
    }

    private void useWarp(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.warp")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            new WarpGui(plugin).open(player);
            return;
        }
        Warp warp = plugin.getWarpManager().get(args[0]);
        if (warp == null) {
            plugin.getMessages().send(player, "warps.unknown", "%warp%", args[0]);
            return;
        }
        if (!plugin.getWarpManager().canUse(player, warp)) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (plugin.getPvpManager().isTagged(player)
                && !plugin.getConfigManager().bool("pvp.combat-tag.allow-teleport", false)) {
            plugin.getMessages().send(player, "pvp.combat-blocked",
                    "%time%", plugin.getPvpManager().remainingTagFormatted(player));
            return;
        }
        plugin.getTeleportManager().teleport(player, warp.location(), "warps.teleported");
    }

    private void setWarp(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.setwarp")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(player, "warps.usage-set");
            return;
        }
        String permission = args.length > 1 ? args[1] : "";
        String icon = args.length > 2 ? args[2] : "";
        if (plugin.getWarpManager().setWarp(args[0], player.getLocation(), permission, icon, player.getUniqueId())) {
            plugin.getMessages().send(player, "warps.set", "%warp%", args[0]);
        } else {
            plugin.getMessages().send(player, "warps.invalid-name");
        }
    }

    private void deleteWarp(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.delwarp")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(player, "warps.usage-delete");
            return;
        }
        if (plugin.getWarpManager().deleteWarp(args[0])) {
            plugin.getMessages().send(player, "warps.deleted", "%warp%", args[0]);
        } else {
            plugin.getMessages().send(player, "warps.unknown", "%warp%", args[0]);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length != 1) {
            return List.of();
        }
        if (name.equals("warp")) {
            List<String> names = new ArrayList<>();
            for (Warp warp : plugin.getWarpManager().visibleFor(player)) {
                names.add(warp.name());
            }
            return names;
        }
        if (name.equals("delwarp") && player.hasPermission("klassensmp.delwarp")) {
            List<String> names = new ArrayList<>();
            for (Warp warp : plugin.getWarpManager().all()) {
                names.add(warp.name());
            }
            return names;
        }
        return List.of();
    }
}
