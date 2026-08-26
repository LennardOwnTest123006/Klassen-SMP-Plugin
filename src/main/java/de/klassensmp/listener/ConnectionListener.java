package de.klassensmp.listener;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.model.Punishment;
import de.klassensmp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Verbindungsaufbau, Beitritt und Verlassen. */
public final class ConnectionListener implements Listener {

    private final KlassenSMP plugin;

    public ConnectionListener(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Prueft Banns und den Anti-Bot-Schutz noch vor dem eigentlichen Login.
     * Laeuft asynchron - es werden ausschliesslich threadsichere Caches genutzt.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Punishment ban = plugin.getModerationManager().getBan(event.getUniqueId());
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    Text.color(plugin.getModerationManager().buildBanScreen(ban)));
            return;
        }
        String denyReason = plugin.getAntiBotManager().checkConnection(event.getUniqueId(), event.getAddress());
        if (denyReason != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.color(denyReason));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getPlayerDataManager().handleJoin(player);
        plugin.getHomeManager().loadFor(player.getUniqueId());
        plugin.getKitManager().loadUses(player.getUniqueId());
        plugin.getQuestManager().loadFor(player.getUniqueId());
        plugin.getAchievementManager().loadFor(player.getUniqueId());
        plugin.getChatManager().loadIgnores(player.getUniqueId());

        plugin.getBoardManager().handleJoin(player);
        plugin.getTabManager().handleJoin(player);
        plugin.getVanishManager().applyOnJoin(player);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean firstJoin = data != null && !player.hasPlayedBefore();

        String joinKey = firstJoin ? "join.first-message" : "join.message";
        if (plugin.getConfigManager().bool("join.custom-messages", true)) {
            String message = plugin.getMessages().plain(joinKey,
                    "%player%", player.getName(),
                    "%rank%", plugin.getRankManager().getRank(player).displayName(),
                    "%platform%", data != null && data.isBedrock()
                            ? plugin.getBoardManager().getPlaceholders().bedrockIcon()
                            : plugin.getBoardManager().getPlaceholders().javaIcon());
            event.setJoinMessage(message.isBlank() ? null : message);
        }

        if (plugin.getVanishManager().isVanished(player)) {
            event.setJoinMessage(null);
        }

        // Willkommensnachricht und Spawn-Teleport
        if (firstJoin) {
            for (String line : plugin.getMessages().list("join.welcome", "%player%", player.getName())) {
                player.sendMessage(line);
            }
            if (plugin.getConfigManager().bool("spawn.teleport-on-first-join", true)) {
                var spawn = plugin.getSpawnManager().getSpawn();
                if (spawn != null) {
                    plugin.getTeleportManager().teleportInstant(player, spawn, null);
                }
            }
        } else if (plugin.getConfigManager().bool("spawn.teleport-on-join", false)) {
            var spawn = plugin.getSpawnManager().getSpawn();
            if (spawn != null) {
                plugin.getTeleportManager().teleportInstant(player, spawn, null);
            }
        }

        if (player.hasPermission("klassensmp.admin") && plugin.getAntiBotManager().isLockdownActive()) {
            plugin.getMessages().send(player, "antibot.staff-active");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getVanishManager().isVanished(player)) {
            event.setQuitMessage(null);
        } else if (plugin.getConfigManager().bool("join.custom-messages", true)) {
            String message = plugin.getMessages().plain("join.quit-message", "%player%", player.getName());
            event.setQuitMessage(message.isBlank() ? null : message);
        }

        plugin.getPvpManager().handleQuit(player);
        plugin.getStaffModeManager().handleQuit(player);
        plugin.getVanishManager().handleQuit(player);
        plugin.getFreezeManager().handleQuit(player);
        plugin.getTeleportManager().handleQuit(player);
        plugin.getTpaManager().handleQuit(player.getUniqueId());
        plugin.getServerEventManager().handleQuit(player);
        plugin.getAntiSpamManager().handleQuit(player.getUniqueId());

        plugin.getPlayerDataManager().handleQuit(player);
        plugin.getQuestManager().unload(player.getUniqueId());
        plugin.getAchievementManager().unload(player.getUniqueId());
        plugin.getHomeManager().unload(player.getUniqueId());
        plugin.getKitManager().unload(player.getUniqueId());
        plugin.getChatManager().unload(player.getUniqueId());

        plugin.getTabManager().handleQuit(player);
        plugin.getBoardManager().handleQuit(player);
    }
}
