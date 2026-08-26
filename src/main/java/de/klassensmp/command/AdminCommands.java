package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.AdminGui;
import de.klassensmp.manager.BackupManager;
import de.klassensmp.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** {@code /smpadmin} und {@code /backup}. */
public final class AdminCommands extends BaseCommand {

    public AdminCommands(KlassenSMP plugin) {
        super(plugin, null, false);
    }

    @Override
    protected void execute(CommandSender sender, String name, String[] args) {
        if (name.equals("backup")) {
            backup(sender, args);
            return;
        }
        admin(sender, args);
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.admin")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadEverything();
            plugin.getMessages().send(sender, "admin.reloaded");
            return;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            plugin.getMessages().send(sender, "common.player-only");
            return;
        }
        new AdminGui(plugin).open(player);
    }

    private void backup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("klassensmp.backup")) {
            plugin.getMessages().send(sender, "common.no-permission");
            return;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("liste") || args[0].equalsIgnoreCase("list"))) {
            List<File> backups = plugin.getBackupManager().listBackups();
            if (backups.isEmpty()) {
                plugin.getMessages().send(sender, "backup.list-empty");
                return;
            }
            plugin.getMessages().send(sender, "backup.list-header", "%count%", String.valueOf(backups.size()));
            for (File file : backups) {
                plugin.getMessages().sendPlain(sender, "backup.list-entry",
                        "%file%", file.getName(),
                        "%size%", BackupManager.formatSize(file.length()),
                        "%date%", TimeUtil.formatDate(file.lastModified()));
            }
            return;
        }

        switch (plugin.getBackupManager().start(sender)) {
            case STARTED -> {
                // Start- und Abschlussmeldung kommen aus dem BackupManager.
            }
            case ALREADY_RUNNING -> plugin.getMessages().send(sender, "backup.already-running");
            case DISABLED -> plugin.getMessages().send(sender, "backup.disabled");
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "backup" -> sender.hasPermission("klassensmp.backup") ? List.of("liste") : List.of();
            case "smpadmin" -> sender.hasPermission("klassensmp.admin") ? List.of("reload") : List.of();
            default -> List.of();
        };
    }
}
