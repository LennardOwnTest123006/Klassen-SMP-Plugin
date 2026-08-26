package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.event.EventDefinition;
import de.klassensmp.event.ServerEventManager;
import de.klassensmp.gui.EventGui;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Events starten, beitreten und beenden ({@code /event}). */
public final class EventCommand extends BaseCommand {

    public EventCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.event", false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        if (args.length == 0) {
            status(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "starten", "start" -> start(sender, args);
            case "stoppen", "stop" -> stop(sender);
            case "beitreten", "join" -> join(sender);
            case "verlassen", "leave" -> leave(sender);
            case "sieger", "winner" -> winner(sender, args);
            case "liste", "list" -> list(sender);
            default -> status(sender);
        }
    }

    private void status(CommandSender sender) {
        ServerEventManager manager = plugin.getServerEventManager();
        if (!manager.isRunning()) {
            plugin.getMessages().send(sender, "events.none-running");
            Player player = asPlayer(sender);
            if (player != null && player.hasPermission("klassensmp.event.manage")) {
                new EventGui(plugin).open(player);
            }
            return;
        }
        plugin.getMessages().send(sender, "events.status",
                "%event%", manager.getActive().displayName(),
                "%state%", manager.getState().name(),
                "%players%", String.valueOf(manager.participantCount()),
                "%countdown%", String.valueOf(Math.max(0, manager.getCountdown())));
    }

    private void start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.event.manage")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "events.usage-start");
            return;
        }
        switch (plugin.getServerEventManager().start(sender, args[1])) {
            case STARTED -> plugin.getMessages().send(sender, "events.start-ok", "%event%", args[1]);
            case ALREADY_RUNNING -> plugin.getMessages().send(sender, "events.already-running");
            case UNKNOWN_EVENT -> plugin.getMessages().send(sender, "events.unknown");
            case NO_LOCATION -> plugin.getMessages().send(sender, "events.no-location");
        }
    }

    private void stop(CommandSender sender) {
        if (!sender.hasPermission("klassensmp.event.manage")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (!plugin.getServerEventManager().isRunning()) {
            plugin.getMessages().send(sender, "events.none-running");
            return;
        }
        plugin.getServerEventManager().stopActiveEvent(sender, false);
    }

    private void join(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        switch (plugin.getServerEventManager().join(player)) {
            case SUCCESS -> plugin.getMessages().send(player, "events.join-ok");
            case NO_EVENT -> plugin.getMessages().send(player, "events.none-running");
            case ALREADY_STARTED -> plugin.getMessages().send(player, "events.already-started");
            case ALREADY_JOINED -> plugin.getMessages().send(player, "events.already-joined");
            case FULL -> plugin.getMessages().send(player, "events.full");
        }
    }

    private void leave(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!plugin.getServerEventManager().isParticipant(player)) {
            plugin.getMessages().send(player, "events.not-participant");
            return;
        }
        plugin.getServerEventManager().leave(player);
    }

    private void winner(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.event.manage")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "events.usage-winner");
            return;
        }
        Player winner = Bukkit.getPlayerExact(args[1]);
        if (winner == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[1]);
            return;
        }
        if (!plugin.getServerEventManager().declareWinner(winner)) {
            plugin.getMessages().send(sender, "events.none-running");
        }
    }

    private void list(CommandSender sender) {
        List<EventDefinition> events = plugin.getServerEventManager().all();
        if (events.isEmpty()) {
            plugin.getMessages().send(sender, "events.none-configured");
            return;
        }
        plugin.getMessages().send(sender, "events.list-header", "%count%", String.valueOf(events.size()));
        for (EventDefinition definition : events) {
            plugin.getMessages().sendPlain(sender, "events.list-entry",
                    "%event%", definition.id(),
                    "%display%", definition.displayName(),
                    "%type%", definition.type().name());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("beitreten", "verlassen", "liste"));
            if (sender.hasPermission("klassensmp.event.manage")) {
                options.add("starten");
                options.add("stoppen");
                options.add("sieger");
            }
            return options;
        }
        if (args.length == 2 && sender.hasPermission("klassensmp.event.manage")) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.startsWith("start")) {
                return plugin.getServerEventManager().nameList();
            }
            if (first.startsWith("sieger") || first.startsWith("winner")) {
                return plugin.getServerEventManager().participantNames();
            }
        }
        return List.of();
    }
}
