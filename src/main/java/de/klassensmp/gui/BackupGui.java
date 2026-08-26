package de.klassensmp.gui;

import de.klassensmp.KlassenSMP;
import de.klassensmp.manager.BackupManager;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Uebersicht und Start von Backups. */
public final class BackupGui extends Gui {

    public BackupGui(KlassenSMP plugin) {
        super(plugin, plugin.getMessages().plain("backup.gui-title"), 6);
    }

    @Override
    protected void build(Player player) {
        List<File> backups = plugin.getBackupManager().listBackups();
        int slot = 0;
        for (File file : backups) {
            if (slot >= 45) {
                break;
            }
            set(slot++, new ItemBuilder(Material.CHEST)
                    .name("&a" + file.getName())
                    .lore(plugin.getMessages().list("backup.gui-entry-lore",
                            "%size%", BackupManager.formatSize(file.length()),
                            "%date%", TimeUtil.formatDate(file.lastModified())))
                    .build());
        }
        if (backups.isEmpty()) {
            set(22, new ItemBuilder(Material.BARRIER)
                    .name(plugin.getMessages().plain("backup.gui-empty"))
                    .build());
        }

        List<String> lore = new ArrayList<>(plugin.getMessages().list("backup.gui-start-lore"));
        if (plugin.getBackupManager().isRunning()) {
            lore.add(plugin.getMessages().plain("backup.gui-running"));
        }
        set(49, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(plugin.getMessages().plain("backup.gui-start"))
                .lore(lore)
                .build(), event -> {
            if (!player.hasPermission("klassensmp.backup")) {
                plugin.getMessages().send(player, "common.no-permission");
                return;
            }
            switch (plugin.getBackupManager().start(player)) {
                case STARTED -> closeLater(player);
                case ALREADY_RUNNING -> plugin.getMessages().send(player, "backup.already-running");
                case DISABLED -> plugin.getMessages().send(player, "backup.disabled");
            }
        });

        set(48, backButton(), event -> openLater(player, new AdminGui(plugin)));
        set(50, closeButton(), event -> closeLater(player));
        fillEmpty();
    }
}
