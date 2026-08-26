package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.model.Punishment;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Moderationsbefehle.
 *
 * <p>Jeder Befehl prueft seine eigene Permission. Ziel-Spieler mit
 * {@code klassensmp.moderation.exempt} koennen nicht bestraft werden - so
 * kann sich das Team nicht gegenseitig aussperren.</p>
 */
public final class ModerationCommands extends BaseCommand {

    public ModerationCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        switch (name) {
            case "kick" -> kick(sender, args);
            case "ban" -> ban(sender, args, false);
            case "tempban" -> ban(sender, args, true);
            case "unban" -> unban(sender, args);
            case "mute" -> mute(sender, args, false);
            case "tempmute" -> mute(sender, args, true);
            case "unmute" -> unmute(sender, args);
            case "warn" -> warn(sender, args);
            case "warnings" -> warnings(sender, args);
            case "freeze" -> freeze(sender, args);
            case "vanish" -> vanish(sender, args);
            case "invsee" -> invsee(sender, args);
            case "endersee" -> endersee(sender, args);
            case "teleport" -> teleport(sender, args);
            default -> plugin.getMessages().send(sender, "common.command-error");
        }
    }

    /** Prueft Permission und Schutzstatus des Ziels. */
    private boolean canModerate(CommandSender sender, String permission, UUID targetId, String targetName) {
        if (!sender.hasPermission(permission)) {
            plugin.getMessages().send(sender, "common.no-permission");
            return false;
        }
        Player online = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (online != null && online.hasPermission("klassensmp.moderation.exempt")
                && !sender.hasPermission("klassensmp.moderation.overrideexempt")) {
            plugin.getMessages().send(sender, "moderation.target-exempt", "%player%", targetName);
            return false;
        }
        if (sender instanceof Player player && player.getUniqueId().equals(targetId)) {
            plugin.getMessages().send(sender, "moderation.not-yourself");
            return false;
        }
        return true;
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-kick");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        if (!canModerate(sender, "klassensmp.kick", target.getUniqueId(), target.getName())) {
            return;
        }
        plugin.getModerationManager().kick(target, join(args, 1), sender.getName());
        plugin.getMessages().send(sender, "moderation.kick-done", "%player%", target.getName());
    }

    private void ban(CommandSender sender, String[] args, boolean temporary) {
        int reasonIndex = temporary ? 2 : 1;
        if (args.length < reasonIndex) {
            plugin.getMessages().send(sender, temporary ? "moderation.usage-tempban" : "moderation.usage-ban");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        String targetName = data == null ? args[0] : data.getName();

        if (!canModerate(sender, temporary ? "klassensmp.ban.temp" : "klassensmp.ban", target, targetName)) {
            return;
        }
        if (plugin.getModerationManager().isBanned(target)) {
            plugin.getMessages().send(sender, "moderation.already-banned", "%player%", targetName);
            return;
        }

        long duration = 0L;
        if (temporary) {
            if (args.length < 2) {
                plugin.getMessages().send(sender, "moderation.usage-tempban");
                return;
            }
            duration = TimeUtil.parseDuration(args[1]);
            if (duration <= 0) {
                plugin.getMessages().send(sender, "moderation.invalid-duration");
                return;
            }
        }

        plugin.getModerationManager().ban(target, targetName, join(args, reasonIndex), sender.getName(), duration);
        plugin.getMessages().send(sender, "moderation.ban-done",
                "%player%", targetName,
                "%time%", temporary ? TimeUtil.formatDuration(duration)
                        : plugin.getMessages().plain("moderation.permanent"));
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-unban");
            return;
        }
        if (!sender.hasPermission("klassensmp.unban")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        if (plugin.getModerationManager().unban(target, sender.getName())) {
            plugin.getMessages().send(sender, "moderation.unban-done", "%player%", args[0]);
        } else {
            plugin.getMessages().send(sender, "moderation.not-banned", "%player%", args[0]);
        }
    }

    private void mute(CommandSender sender, String[] args, boolean temporary) {
        int reasonIndex = temporary ? 2 : 1;
        if (args.length == 0) {
            plugin.getMessages().send(sender, temporary ? "moderation.usage-tempmute" : "moderation.usage-mute");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        String targetName = data == null ? args[0] : data.getName();

        if (!canModerate(sender, "klassensmp.mute", target, targetName)) {
            return;
        }
        if (plugin.getModerationManager().isMuted(target)) {
            plugin.getMessages().send(sender, "moderation.already-muted", "%player%", targetName);
            return;
        }

        long duration = 0L;
        if (temporary) {
            if (args.length < 2) {
                plugin.getMessages().send(sender, "moderation.usage-tempmute");
                return;
            }
            duration = TimeUtil.parseDuration(args[1]);
            if (duration <= 0) {
                plugin.getMessages().send(sender, "moderation.invalid-duration");
                return;
            }
        }

        plugin.getModerationManager().mute(target, targetName, join(args, reasonIndex), sender.getName(), duration);
        plugin.getMessages().send(sender, "moderation.mute-done",
                "%player%", targetName,
                "%time%", temporary ? TimeUtil.formatDuration(duration)
                        : plugin.getMessages().plain("moderation.permanent"));
    }

    private void unmute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-unmute");
            return;
        }
        if (!sender.hasPermission("klassensmp.mute")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        if (plugin.getModerationManager().unmute(target, sender.getName())) {
            plugin.getMessages().send(sender, "moderation.unmute-done", "%player%", args[0]);
        } else {
            plugin.getMessages().send(sender, "moderation.not-muted", "%player%", args[0]);
        }
    }

    private void warn(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-warn");
            return;
        }
        UUID target = plugin.getPlayerDataManager().findUuidByName(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        String targetName = data == null ? args[0] : data.getName();

        if (!canModerate(sender, "klassensmp.warn", target, targetName)) {
            return;
        }
        int count = plugin.getModerationManager().warn(target, targetName, join(args, 1), sender.getName());
        plugin.getMessages().send(sender, "moderation.warn-done",
                "%player%", targetName, "%count%", String.valueOf(count));
    }

    private void warnings(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.warn")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        String name = args.length > 0 ? args[0] : sender.getName();
        UUID target = plugin.getPlayerDataManager().findUuidByName(name);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", name);
            return;
        }
        plugin.getModerationManager().history(target, history -> {
            if (history.isEmpty()) {
                plugin.getMessages().send(sender, "moderation.history-empty", "%player%", name);
                return;
            }
            plugin.getMessages().send(sender, "moderation.history-header",
                    "%player%", name, "%count%", String.valueOf(history.size()));
            for (Punishment punishment : history) {
                plugin.getMessages().sendPlain(sender, "moderation.history-entry",
                        "%type%", punishment.type().getDisplayName(),
                        "%reason%", punishment.reason(),
                        "%staff%", punishment.staff(),
                        "%date%", TimeUtil.formatDate(punishment.created()),
                        "%status%", punishment.active() && !punishment.isExpired()
                                ? plugin.getMessages().plain("moderation.status-active")
                                : plugin.getMessages().plain("moderation.status-expired"));
            }
        });
    }

    private void freeze(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-freeze");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }
        if (!canModerate(sender, "klassensmp.freeze", target.getUniqueId(), target.getName())) {
            return;
        }
        boolean frozen = plugin.getFreezeManager().toggle(target);
        plugin.getMessages().send(sender, frozen ? "moderation.freeze-on" : "moderation.freeze-off",
                "%player%", target.getName());
    }

    private void vanish(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.vanish")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("klassensmp.vanish.others")) {
                plugin.getMessages().send(sender, "common.no-permission");
                return;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
                return;
            }
        } else {
            target = asPlayer(sender);
            if (target == null) {
                plugin.getMessages().send(sender, "common.player-only");
                return;
            }
        }
        boolean vanished = plugin.getVanishManager().toggle(target);
        plugin.getMessages().send(target, vanished ? "vanish.enabled" : "vanish.disabled");
        if (!target.equals(sender)) {
            plugin.getMessages().send(sender, vanished ? "moderation.vanish-on" : "moderation.vanish-off",
                    "%player%", target.getName());
        }
    }

    private void invsee(CommandSender sender, String[] args) {
        Player staff = asPlayer(sender);
        if (staff == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!staff.hasPermission("klassensmp.invsee")) {
            plugin.getMessages().send(staff, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(staff, "moderation.usage-invsee");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessages().send(staff, "common.player-not-found", "%player%", args[0]);
            return;
        }
        staff.openInventory(target.getInventory());
    }

    private void endersee(CommandSender sender, String[] args) {
        Player staff = asPlayer(sender);
        if (staff == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        if (!staff.hasPermission("klassensmp.endersee")) {
            plugin.getMessages().send(staff, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(staff, "moderation.usage-endersee");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.getMessages().send(staff, "common.player-not-found", "%player%", args[0]);
            return;
        }
        staff.openInventory(target.getEnderChest());
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.teleport")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length == 0) {
            plugin.getMessages().send(sender, "moderation.usage-teleport");
            return;
        }
        Player first = Bukkit.getPlayerExact(args[0]);
        if (first == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[0]);
            return;
        }

        if (args.length == 1) {
            Player staff = asPlayer(sender);
            if (staff == null) {
                plugin.getMessages().send(sender, "common.player-only");
                return;
            }
            plugin.getTeleportManager().teleportInstant(staff, first.getLocation(), "moderation.teleported");
            return;
        }

        Player second = Bukkit.getPlayerExact(args[1]);
        if (second == null) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", args[1]);
            return;
        }
        plugin.getTeleportManager().teleportInstant(first, second.getLocation(), null);
        plugin.getMessages().send(sender, "moderation.teleport-done",
                "%player%", first.getName(), "%target%", second.getName());
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            return switch (name) {
                case "unban", "unmute", "warnings", "ban", "tempban", "mute", "tempmute", "warn" ->
                        knownPlayerNames();
                default -> visiblePlayerNames(sender);
            };
        }
        if (args.length == 2) {
            if (name.equals("tempban") || name.equals("tempmute")) {
                return List.of("10m", "1h", "6h", "1d", "7d", "30d");
            }
            if (name.equals("teleport")) {
                return visiblePlayerNames(sender);
            }
        }
        return List.of();
    }
}
