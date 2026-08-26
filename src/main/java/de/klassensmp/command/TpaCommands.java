package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.manager.TpaManager;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Teleportanfragen: {@code /tpa}, {@code /tpahere}, {@code /tpaccept}, {@code /tpdeny}. */
public final class TpaCommands extends BaseCommand {

    public TpaCommands(KlassenSMP plugin) {
        super(plugin, "klassensmp.tpa", true);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return;
        }
        switch (name) {
            case "tpa" -> request(player, args, false);
            case "tpahere" -> request(player, args, true);
            case "tpaccept" -> answer(player, args, true);
            case "tpdeny" -> answer(player, args, false);
            default -> plugin.getMessages().send(player, "common.command-error");
        }
    }

    private void request(Player player, String[] args, boolean here) {
        if (args.length == 0) {
            plugin.getMessages().send(player, here ? "tpa.usage-here" : "tpa.usage");
            return;
        }
        Player target = findOnline(player, args[0]);
        if (target == null) {
            return;
        }

        TpaManager.SendResult result = plugin.getTpaManager().send(player, target, here);
        switch (result) {
            case SUCCESS -> {
                long timeout = plugin.getConfigManager().duration("tpa.timeout-seconds", 60L);
                plugin.getMessages().send(player, here ? "tpa.sent-here" : "tpa.sent",
                        "%player%", target.getName(), "%seconds%", String.valueOf(timeout));
                plugin.getMessages().send(target, here ? "tpa.received-here" : "tpa.received",
                        "%player%", player.getName(), "%seconds%", String.valueOf(timeout));
            }
            case SELF -> plugin.getMessages().send(player, "tpa.self");
            case COOLDOWN -> plugin.getMessages().send(player, "tpa.cooldown",
                    "%time%", TimeUtil.formatDuration(plugin.getTpaManager().cooldownRemaining(player.getUniqueId())));
            case ALREADY_PENDING -> plugin.getMessages().send(player, "tpa.already-pending",
                    "%player%", target.getName());
        }
    }

    private void answer(Player player, String[] args, boolean accept) {
        TpaManager.Request request;
        if (args.length > 0) {
            Player from = Bukkit.getPlayerExact(args[0]);
            request = from == null ? null : plugin.getTpaManager().find(player.getUniqueId(), from.getUniqueId());
        } else {
            request = plugin.getTpaManager().oldestFor(player.getUniqueId());
        }
        if (request == null) {
            plugin.getMessages().send(player, "tpa.no-request");
            return;
        }

        Player other = Bukkit.getPlayer(request.sender());
        if (!accept) {
            plugin.getTpaManager().remove(player.getUniqueId(), request.sender());
            plugin.getMessages().send(player, "tpa.denied");
            if (other != null) {
                plugin.getMessages().send(other, "tpa.denied-sender", "%player%", player.getName());
            }
            return;
        }

        if (!plugin.getTpaManager().accept(player, request)) {
            plugin.getMessages().send(player, "tpa.sender-offline");
            return;
        }
        plugin.getMessages().send(player, "tpa.accepted");
        if (other != null) {
            plugin.getMessages().send(other, "tpa.accepted-sender", "%player%", player.getName());
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        Player player = asPlayer(sender);
        if (player == null || args.length != 1) {
            return List.of();
        }
        if (name.equals("tpa") || name.equals("tpahere")) {
            List<String> names = visiblePlayerNames(sender);
            names.remove(player.getName());
            return names;
        }
        return plugin.getTpaManager().pendingSenderNames(player.getUniqueId());
    }
}
