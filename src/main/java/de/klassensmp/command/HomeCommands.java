package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.manager.HomeManager;
import de.klassensmp.model.Home;
import de.klassensmp.util.LocationUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /home}, {@code /sethome}, {@code /delhome} und {@code /homes}. */
public final class HomeCommands extends BaseCommand {

    public HomeCommands(KlassenSMP plugin) {
        super(plugin, "klassensmp.home", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        switch (name) {
            case "sethome" -> setHome(player, args);
            case "delhome" -> deleteHome(player, args);
            case "homes" -> listHomes(player);
            default -> teleportHome(player, args);
        }
    }

    private void setHome(Player player, String[] args) {
        String homeName = args.length == 0
                ? plugin.getConfigManager().string("homes.default-name", "home")
                : args[0];
        int max = plugin.getRankManager().getMaxHomes(player);

        HomeManager.SetResult result = plugin.getHomeManager().setHome(player, homeName);
        switch (result) {
            case SUCCESS -> plugin.getMessages().send(player, "homes.set",
                    "%home%", homeName,
                    "%count%", String.valueOf(plugin.getHomeManager().count(player.getUniqueId())),
                    "%max%", max < 0 ? "∞" : String.valueOf(max));
            case LIMIT_REACHED -> plugin.getMessages().send(player, "homes.limit-reached",
                    "%max%", String.valueOf(max));
            case INVALID_NAME -> plugin.getMessages().send(player, "homes.invalid-name");
            case WORLD_DISABLED -> plugin.getMessages().send(player, "homes.world-disabled");
        }
    }

    private void deleteHome(Player player, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(player, "homes.usage-delete");
            return;
        }
        if (plugin.getHomeManager().deleteHome(player.getUniqueId(), args[0])) {
            plugin.getMessages().send(player, "homes.deleted", "%home%", args[0]);
        } else {
            plugin.getMessages().send(player, "homes.unknown", "%home%", args[0]);
        }
    }

    private void listHomes(Player player) {
        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.getMessages().send(player, "homes.none");
            return;
        }
        int max = plugin.getRankManager().getMaxHomes(player);
        plugin.getMessages().send(player, "homes.list-header",
                "%count%", String.valueOf(homes.size()),
                "%max%", max < 0 ? "∞" : String.valueOf(max));
        for (Home home : homes) {
            plugin.getMessages().sendPlain(player, "homes.list-entry",
                    "%home%", home.name(),
                    "%location%", LocationUtil.pretty(home.location()));
        }
    }

    private void teleportHome(Player player, String[] args) {
        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.getMessages().send(player, "homes.none");
            return;
        }
        if (plugin.getPvpManager().isTagged(player)
                && !plugin.getConfigManager().bool("pvp.combat-tag.allow-teleport", false)) {
            plugin.getMessages().send(player, "pvp.combat-blocked",
                    "%time%", plugin.getPvpManager().remainingTagFormatted(player));
            return;
        }

        Home home;
        if (args.length == 0) {
            home = homes.size() == 1 ? homes.get(0)
                    : plugin.getHomeManager().getHome(player.getUniqueId(),
                    plugin.getConfigManager().string("homes.default-name", "home"));
            if (home == null) {
                listHomes(player);
                return;
            }
        } else {
            home = plugin.getHomeManager().getHome(player.getUniqueId(), args[0]);
            if (home == null) {
                plugin.getMessages().send(player, "homes.unknown", "%home%", args[0]);
                return;
            }
        }
        plugin.getTeleportManager().teleport(player, home.location(), "homes.teleported");
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length != 1) {
            return List.of();
        }
        if (name.equals("home") || name.equals("delhome")) {
            return plugin.getHomeManager().getHomeNames(player.getUniqueId());
        }
        return List.of();
    }
}
