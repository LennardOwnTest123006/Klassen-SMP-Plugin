package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Allgemeine Informationen und Hilfe ({@code /smp}). */
public final class SmpCommand extends BaseCommand {

    public SmpCommand(KlassenSMP plugin) {
        super(plugin, "klassensmp.use", false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        if (args.length == 0) {
            for (String line : plugin.getMessages().list("smp.help")) {
                sender.sendMessage(line);
            }
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "info" -> sendInfo(sender);
            case "hilfe", "help" -> {
                for (String line : plugin.getMessages().list("smp.help")) {
                    sender.sendMessage(line);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("klassensmp.admin")) {
                    plugin.getMessages().send(sender, "common.no-permission");
                    return;
                }
                plugin.reloadEverything();
                plugin.getMessages().send(sender, "admin.reloaded");
            }
            default -> plugin.getMessages().send(sender, "smp.unknown-subcommand");
        }
    }

    private void sendInfo(CommandSender sender) {
        var placeholders = plugin.getBoardManager().getPlaceholders();
        var counts = placeholders.countPlatforms();
        for (String line : plugin.getMessages().list("smp.info",
                "%version%", plugin.getDescription().getVersion(),
                "%server%", Bukkit.getVersion(),
                "%online%", String.valueOf(counts.total()),
                "%java%", String.valueOf(counts.java()),
                "%bedrock%", String.valueOf(counts.bedrock()),
                "%tps%", NumberUtil.formatTps(plugin.getPerformanceManager().getTps()),
                "%database%", plugin.getDatabase().isMysql() ? "MySQL/MariaDB" : "SQLite",
                "%floodgate%", yesNo(plugin.getHooks().floodgate().isAvailable()),
                "%geyser%", yesNo(plugin.getHooks().floodgate().isGeyserPresent()),
                "%vault%", yesNo(plugin.getHooks().vault().isRegistered()),
                "%placeholderapi%", yesNo(plugin.getHooks().placeholders().isAvailable()))) {
            sender.sendMessage(line);
        }
    }

    private String yesNo(boolean value) {
        return value ? plugin.getMessages().plain("common.yes") : plugin.getMessages().plain("common.no");
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length == 1) {
            return sender.hasPermission("klassensmp.admin")
                    ? List.of("info", "hilfe", "reload")
                    : List.of("info", "hilfe");
        }
        return List.of();
    }
}
