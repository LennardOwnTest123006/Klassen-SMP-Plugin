package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Chatbefehle: {@code /msg}, {@code /reply}, {@code /ignore}, {@code /chat},
 * {@code /mutechat}, {@code /socialspy} und {@code /staffchat}.
 */
public final class ChatCommands extends BaseCommand {

    public ChatCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "msg" -> privateMessage(sender, args);
            case "reply" -> reply(sender, args);
            case "ignore" -> ignore(sender, args);
            case "mutechat" -> muteChat(sender);
            case "socialspy" -> socialSpy(sender);
            case "staffchat" -> staffChat(sender, args);
            default -> chatInfo(sender);
        }
    }

    private void privateMessage(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.chat.msg")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessages().send(player, "chat.usage-msg");
            return;
        }
        Player target = findOnline(player, args[0]);
        if (target == null) {
            return;
        }
        if (target.equals(player)) {
            plugin.getMessages().send(player, "chat.msg-self");
            return;
        }
        if (plugin.getModerationManager().isMuted(player.getUniqueId())) {
            plugin.getMessages().send(player, "moderation.you-are-muted-short");
            return;
        }
        if (!plugin.getChatManager().sendPrivate(player, target, join(args, 1))) {
            plugin.getMessages().send(player, "chat.ignored-by", "%player%", target.getName());
        }
    }

    private void reply(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.chat.msg")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(player, "chat.usage-reply");
            return;
        }
        Player target = plugin.getChatManager().getReplyTarget(player);
        if (target == null || !target.isOnline()) {
            plugin.getMessages().send(player, "chat.no-reply-target");
            return;
        }
        if (plugin.getModerationManager().isMuted(player.getUniqueId())) {
            plugin.getMessages().send(player, "moderation.you-are-muted-short");
            return;
        }
        if (!plugin.getChatManager().sendPrivate(player, target, join(args, 0))) {
            plugin.getMessages().send(player, "chat.ignored-by", "%player%", target.getName());
        }
    }

    private void ignore(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.chat.ignore")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            List<String> ignored = plugin.getChatManager().ignoredNames(player.getUniqueId());
            if (ignored.isEmpty()) {
                plugin.getMessages().send(player, "chat.ignore-none");
            } else {
                plugin.getMessages().send(player, "chat.ignore-list", "%players%", String.join(", ", ignored));
            }
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(player, "common.player-not-found", "%player%", args[0]);
            return;
        }
        if (target.equals(player.getUniqueId())) {
            plugin.getMessages().send(player, "chat.ignore-self");
            return;
        }
        boolean nowIgnored = plugin.getChatManager().toggleIgnore(player.getUniqueId(), target);
        plugin.getMessages().send(player, nowIgnored ? "chat.ignore-added" : "chat.ignore-removed",
                "%player%", args[0]);
    }

    private void muteChat(CommandSender sender) {
        if (!sender.hasPermission("klassensmp.chat.mute")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        boolean muted = !plugin.getChatManager().isChatMuted();
        plugin.getChatManager().setChatMuted(muted);
        Bukkit.broadcastMessage(plugin.getMessages().get(muted ? "chat.mute-on" : "chat.mute-off",
                "%staff%", sender.getName()));
    }

    private void socialSpy(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!player.hasPermission("klassensmp.socialspy")) {
            plugin.getMessages().send(player, "common.no-permission");
            return;
        }
        boolean enabled = plugin.getChatManager().toggleSocialSpy(player);
        plugin.getMessages().send(player, enabled ? "chat.socialspy-on" : "chat.socialspy-off");
    }

    private void staffChat(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.staffchat")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(sender, "chat.usage-staffchat");
            return;
        }
        plugin.getChatManager().sendStaffMessage(sender, join(args, 0));
    }

    private void chatInfo(CommandSender sender) {
        if (!sender.hasPermission("klassensmp.chat")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        for (String line : plugin.getMessages().list("chat.info",
                "%muted%", plugin.getChatManager().isChatMuted()
                        ? plugin.getMessages().plain("common.yes")
                        : plugin.getMessages().plain("common.no"),
                "%cooldown%", String.valueOf(
                        plugin.getConfigManager().duration("antispam.chat-cooldown-millis", 2000L) / 1000L))) {
            sender.sendMessage(line);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "msg" -> visiblePlayerNames(sender);
            case "ignore" -> knownPlayerNames();
            default -> List.of();
        };
    }
}
