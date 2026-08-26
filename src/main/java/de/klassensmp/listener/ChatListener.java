package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Punishment;
import de.klassensmp.moderation.AntiSpamManager;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Chat- und Command-Verarbeitung.
 *
 * <p>Das Chat-Event wird abgebrochen und die Nachricht selbst zugestellt.
 * Nur so lassen sich Ignorierlisten sauber umsetzen und es kann kein
 * Format-String-Exploit ueber {@code %} entstehen.</p>
 */
public final class ChatListener implements Listener {

    private final KlassenSMP plugin;

    public ChatListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        Punishment mute = plugin.getModerationManager().getMute(player.getUniqueId());
        if (mute != null) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "moderation.you-are-muted",
                    "%reason%", mute.reason(),
                    "%time%", mute.isPermanent()
                            ? plugin.getMessages().plain("moderation.permanent")
                            : TimeUtil.formatDuration(mute.remaining()));
            return;
        }

        if (plugin.getChatManager().isChatMuted() && !player.hasPermission("klassensmp.chat.bypassmute")) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "chat.globally-muted");
            return;
        }

        AntiSpamManager.Result result = plugin.getAntiSpamManager().checkChat(player, event.getMessage());
        if (result != AntiSpamManager.Result.OK) {
            event.setCancelled(true);
            switch (result) {
                case COOLDOWN -> plugin.getMessages().send(player, "antispam.cooldown");
                case TOO_FAST -> plugin.getMessages().send(player, "antispam.too-fast");
                case DUPLICATE -> plugin.getMessages().send(player, "antispam.duplicate");
                case TOO_MANY_CAPS -> plugin.getMessages().send(player, "antispam.caps");
                default -> plugin.getMessages().send(player, "antispam.blocked");
            }
            return;
        }

        String line = plugin.getChatManager().formatChat(player, event.getMessage());
        event.setCancelled(true);

        Bukkit.getConsoleSender().sendMessage(line);
        for (Player recipient : event.getRecipients()) {
            if (plugin.getChatManager().isIgnoring(recipient.getUniqueId(), player.getUniqueId())) {
                continue;
            }
            recipient.sendMessage(line);
        }
    }

    /** Prueft Befehle auf Spam, Freeze und Mute (fuer Chat-Befehle). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase(Locale.ROOT);

        if (plugin.getFreezeManager().isFrozen(player) && !plugin.getFreezeManager().isCommandAllowed(command)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "moderation.frozen-command");
            return;
        }

        if (!plugin.getAntiSpamManager().checkCommand(player)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "antispam.command-cooldown");
            return;
        }

        // Chat-Befehle unterliegen ebenfalls der Stummschaltung.
        Punishment mute = plugin.getModerationManager().getMute(player.getUniqueId());
        if (mute != null && isChatCommand(command)) {
            event.setCancelled(true);
            plugin.getMessages().send(player, "moderation.you-are-muted",
                    "%reason%", mute.reason(),
                    "%time%", mute.isPermanent()
                            ? plugin.getMessages().plain("moderation.permanent")
                            : TimeUtil.formatDuration(mute.remaining()));
        }
    }

    private boolean isChatCommand(String command) {
        for (String entry : plugin.getConfigManager().get().getStringList("chat.muted-commands")) {
            if (command.startsWith(entry.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
