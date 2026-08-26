package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.AchievementGui;
import de.klassensmp.gui.QuestGui;
import de.klassensmp.gui.StatsGui;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /stats}, {@code /playtime}, {@code /achievements} und {@code /quests}. */
public final class StatsCommands extends BaseCommand {

    public StatsCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "playtime" -> playtime(sender, args);
            case "achievements" -> achievements(sender, args);
            case "quests" -> quests(sender);
            default -> stats(sender, args);
        }
    }

    /** Ermittelt das Ziel eines Befehls (eigener Spieler oder Angabe). */
    private PlayerData resolveTarget(CommandSender sender, String[] args, String permissionOthers) {
        if (args.length > 0) {
            if (!sender.hasPermission(permissionOthers)) {
                plugin.getMessages().send(sender, "common.no-permission");
                return null;
            }
            PlayerData data = plugin.getPlayerDataManager().findByName(args[0]);
            if (data == null) {
                plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            }
            return data;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return null;
        }
        return plugin.getPlayerDataManager().get(player.getUniqueId());
    }

    private void stats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.stats")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        PlayerData data = resolveTarget(sender, args, "klassensmp.stats.others");
        if (data == null) {
            return;
        }
        Player player = asPlayer(sender);
        if (player != null && plugin.getConfigManager().bool("stats.gui", true)) {
            new StatsGui(plugin, data).open(player);
            return;
        }
        for (String line : plugin.getMessages().list("stats.text",
                "%player%", data.getName(),
                "%kills%", String.valueOf(data.getKills()),
                "%deaths%", String.valueOf(data.getDeaths()),
                "%kdr%", String.format(java.util.Locale.GERMANY, "%.2f", data.getKdr()),
                "%mobkills%", String.valueOf(data.getMobKills()),
                "%broken%", String.valueOf(data.getBlocksBroken()),
                "%placed%", String.valueOf(data.getBlocksPlaced()),
                "%playtime%", TimeUtil.formatPlaytime(data.getTotalPlaytime()),
                "%money%", plugin.getEconomyManager().format(data.getMoney()),
                "%earned%", plugin.getEconomyManager().format(data.getEarned()),
                "%spent%", plugin.getEconomyManager().format(data.getSpent()),
                "%first%", TimeUtil.formatDate(data.getFirstJoin()))) {
            sender.sendMessage(line);
        }
    }

    private void playtime(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.playtime")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        PlayerData data = resolveTarget(sender, args, "klassensmp.stats.others");
        if (data == null) {
            return;
        }
        plugin.getMessages().send(sender, "stats.playtime",
                "%player%", data.getName(),
                "%playtime%", TimeUtil.formatDuration(data.getTotalPlaytime()));
    }

    private void achievements(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.achievements")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        PlayerData data = resolveTarget(sender, args, "klassensmp.stats.others");
        if (data == null) {
            return;
        }
        new AchievementGui(plugin, data.getUuid(), data.getName()).open(player);
    }

    private void quests(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.quests")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        new QuestGui(plugin).open(player);
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1 && sender.hasPermission("klassensmp.stats.others")
                && !name.equals("quests")) {
            return knownPlayerNames();
        }
        return List.of();
    }
}
