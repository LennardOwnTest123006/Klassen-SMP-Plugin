package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.PerformanceGui;
import de.klassensmp.performance.BoostMode;
import de.klassensmp.performance.PerformanceManager;
import de.klassensmp.performance.PerformanceSnapshot;
import de.klassensmp.util.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /tps}, {@code /lag}, {@code /performance} und {@code /serverboost}. */
public final class PerformanceCommands extends BaseCommand {

    public PerformanceCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "serverboost" -> serverBoost(sender, args);
            case "performance" -> performance(sender);
            default -> tps(sender, name.equals("lag"));
        }
    }

    private void tps(CommandSender sender, boolean detailed) {
        if (!sender.hasPermission("klassensmp.tps")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        PerformanceSnapshot snapshot = plugin.getPerformanceManager().snapshot(false);

        plugin.getMessages().send(sender, "performance.tps",
                "%tps%", NumberUtil.formatTps(snapshot.measuredTps()),
                "%status%", snapshot.status().getDisplay(),
                "%icon%", snapshot.status().getIcon());

        if (snapshot.hasServerTps()) {
            plugin.getMessages().send(sender, "performance.tps-server",
                    "%tps1%", NumberUtil.formatTps(snapshot.serverTps()[0]),
                    "%tps5%", NumberUtil.formatTps(snapshot.serverTps().length > 1
                            ? snapshot.serverTps()[1] : snapshot.serverTps()[0]),
                    "%tps15%", NumberUtil.formatTps(snapshot.serverTps().length > 2
                            ? snapshot.serverTps()[2] : snapshot.serverTps()[0]));
        } else {
            plugin.getMessages().send(sender, "performance.tps-server-unavailable");
        }

        if (!detailed || !sender.hasPermission("klassensmp.performance")) {
            return;
        }

        plugin.getMessages().send(sender, "performance.details",
                "%players%", String.valueOf(snapshot.players()),
                "%entities%", String.valueOf(snapshot.entities()),
                "%mobs%", String.valueOf(snapshot.livingEntities()),
                "%items%", String.valueOf(snapshot.items()),
                "%chunks%", String.valueOf(snapshot.chunks()),
                "%redstone%", String.valueOf(snapshot.redstonePerSecond()),
                "%hoppers%", String.valueOf(snapshot.hopperTransfersPerSecond()),
                "%memory%", snapshot.usedMemoryMb() + "/" + snapshot.maxMemoryMb() + " MB",
                "%boost%", plugin.getServerBoostManager().getMode().getDisplay());

        List<PerformanceManager.ChunkReport> reports = plugin.getPerformanceManager().topChunks(5);
        if (!reports.isEmpty()) {
            plugin.getMessages().send(sender, "performance.hotspots-header");
            for (PerformanceManager.ChunkReport report : reports) {
                plugin.getMessages().sendPlain(sender, "performance.hotspot-entry",
                        "%world%", report.world(),
                        "%x%", String.valueOf(report.x() * 16),
                        "%z%", String.valueOf(report.z() * 16),
                        "%entities%", String.valueOf(report.entities()));
            }
        }
    }

    private void performance(CommandSender sender) {
        if (!sender.hasPermission("klassensmp.performance")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            tps(sender, true);
            return;
        }
        new PerformanceGui(plugin).open(player);
    }

    private void serverBoost(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.serverboost")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(sender, "serverboost.current",
                    "%mode%", plugin.getServerBoostManager().getMode().getDisplay());
            plugin.getMessages().send(sender, "serverboost.usage");
            return;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("cleanup") || first.equals("aufraeumen")) {
            int items = plugin.getPerformanceManager().cleanupGround(false);
            int mobs = plugin.getPerformanceManager().limitMobs();
            plugin.getMessages().send(sender, "serverboost.cleanup",
                    "%items%", String.valueOf(items), "%mobs%", String.valueOf(mobs));
            return;
        }

        BoostMode mode = BoostMode.parse(first);
        if (mode == null) {
            plugin.getMessages().send(sender, "serverboost.unknown-mode");
            return;
        }
        if (mode == BoostMode.EXTREME && !sender.hasPermission("klassensmp.serverboost.extreme")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        plugin.getServerBoostManager().setMode(mode);
        plugin.getMessages().send(sender, "serverboost.set", "%mode%", mode.getDisplay());
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (name.equals("serverboost") && args.length == 1 && sender.hasPermission("klassensmp.serverboost")) {
            List<String> options = new ArrayList<>(List.of("normal", "performance", "aufraeumen"));
            if (sender.hasPermission("klassensmp.serverboost.extreme")) {
                options.add("extreme");
            }
            return options;
        }
        return List.of();
    }
}
