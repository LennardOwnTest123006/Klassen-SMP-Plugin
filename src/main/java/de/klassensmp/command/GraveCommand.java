package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Grave;
import de.klassensmp.util.LocationUtil;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Graeber verwalten ({@code /grave}). */
public final class GraveCommand extends BaseCommand {

    public GraveCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.graves", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        if (!plugin.getGraveManager().isEnabled()) {
            plugin.getMessages().send(player, "graves.disabled");
            return;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("alle") || args[0].equalsIgnoreCase("all"))) {
            if (!player.hasPermission("klassensmp.graves.others")) {
                plugin.getMessages().send(player, "common.no-permission");
                return;
            }
            listGraves(player, plugin.getGraveManager().allGraves(), true);
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("tp")) {
            teleport(player, args);
            return;
        }

        listGraves(player, plugin.getGraveManager().gravesOf(player.getUniqueId()), false);
    }

    private void listGraves(Player player, List<Grave> graves, boolean showOwner) {
        if (graves.isEmpty()) {
            plugin.getMessages().send(player, "graves.none");
            return;
        }
        plugin.getMessages().send(player, "graves.list-header", "%count%", String.valueOf(graves.size()));
        for (Grave grave : graves) {
            plugin.getMessages().sendPlain(player, showOwner ? "graves.list-entry-owner" : "graves.list-entry",
                    "%id%", String.valueOf(grave.getId()),
                    "%owner%", grave.getOwnerName(),
                    "%location%", LocationUtil.pretty(grave.getLocation()),
                    "%time%", grave.getExpires() > 0
                            ? TimeUtil.formatDuration(grave.getExpires() - System.currentTimeMillis())
                            : "-");
        }
    }

    private void teleport(Player player, String[] args) {
        if (!player.hasPermission("klassensmp.graves.teleport")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "graves.usage-tp");
            return;
        }
        int id = NumberUtil.parseInt(args[1], -1);
        Grave grave = plugin.getGraveManager().getGrave(id);
        if (grave == null) {
            plugin.getMessages().send(player, "graves.unknown", "%id%", args[1]);
            return;
        }
        if (!grave.getOwner().equals(player.getUniqueId()) && !player.hasPermission("klassensmp.graves.others")) {
            plugin.getMessages().send(player, "graves.not-yours", "%player%", grave.getOwnerName());
            return;
        }
        plugin.getTeleportManager().teleport(player, grave.getLocation(), "graves.teleported");
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            return sender.hasPermission("klassensmp.graves.others")
                    ? List.of("tp", "alle")
                    : List.of("tp");
        }
        if (args.length == 2 && args[0].toLowerCase(Locale.ROOT).equals("tp")) {
            Player player = asPlayer(sender);
            if (player != null) {
                List<String> ids = new java.util.ArrayList<>();
                for (Grave grave : plugin.getGraveManager().gravesOf(player.getUniqueId())) {
                    ids.add(String.valueOf(grave.getId()));
                }
                return ids;
            }
        }
        return List.of();
    }
}
