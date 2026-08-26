package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Rank;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat-Formatierung, private Nachrichten, Ignorieren und globaler Chat-Mute.
 */
public final class ChatManager {

    private final KlassenSMP plugin;

    private final Map<UUID, Set<UUID>> ignored = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMessage = new ConcurrentHashMap<>();
    private final Set<UUID> socialSpy = ConcurrentHashMap.newKeySet();

    /** Platzhalter, damit der Spielertext nicht mit eingefaerbt wird. */
    private static final String MESSAGE_TOKEN = "\u0000ks-message\u0000";

    private volatile boolean chatMuted;
    private String chatFormat = "%prefix%%name%&7: &f%message%";
    private String privateFormatOut = "&8[&7Du &8-> &7%target%&8] &f%message%";
    private String privateFormatIn = "&8[&7%sender% &8-> &7Dir&8] &f%message%";
    private String socialSpyFormat = "&8[&cSpy&8] &7%sender% &8-> &7%target%&8: &f%message%";

    public ChatManager(KlassenSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.chatFormat = plugin.getConfigManager().string("chat.format", chatFormat);
        this.privateFormatOut = plugin.getConfigManager().string("chat.private.format-out", privateFormatOut);
        this.privateFormatIn = plugin.getConfigManager().string("chat.private.format-in", privateFormatIn);
        this.socialSpyFormat = plugin.getConfigManager().string("chat.socialspy-format", socialSpyFormat);
    }

    // ------------------------------------------------------------------
    // Chat-Formatierung
    // ------------------------------------------------------------------

    /**
     * Baut die Chatzeile eines Spielers.
     *
     * <p>Wird aus dem asynchronen Chat-Event aufgerufen. Es werden bewusst nur
     * threadsichere Operationen (Permission-Abfragen, Config-Lesen) genutzt.</p>
     */
    public String formatChat(Player player, String message) {
        Rank rank = plugin.getRankManager().getRank(player);

        // Der Spielertext wird getrennt behandelt: nur mit Berechtigung sind
        // Farbcodes erlaubt, ansonsten werden sie entwertet. So kann niemand
        // ueber den Chat das Format oder fremde Namen faelschen.
        String body = player.hasPermission("klassensmp.chat.color")
                ? Text.color(message)
                : stripColorCodes(message);

        String line = Text.color(Text.replace(chatFormat,
                "%prefix%", rank.prefix(),
                "%suffix%", rank.suffix(),
                "%rank%", rank.displayName(),
                "%namecolor%", rank.nameColor(),
                "%name%", player.getName(),
                "%world%", player.getWorld().getName(),
                "%message%", MESSAGE_TOKEN));

        return line.replace(MESSAGE_TOKEN, body);
    }

    /** Entfernt alle Farb- und Formatcodes aus einem Spielertext. */
    private String stripColorCodes(String input) {
        return input == null ? "" : input.replaceAll("[&\u00A7]([0-9a-fk-orA-FK-OR])", "$1");
    }

    public boolean isChatMuted() {
        return chatMuted;
    }

    public void setChatMuted(boolean chatMuted) {
        this.chatMuted = chatMuted;
    }

    // ------------------------------------------------------------------
    // Private Nachrichten
    // ------------------------------------------------------------------

    /**
     * Sendet eine private Nachricht.
     *
     * @return {@code false}, wenn der Empfaenger den Absender ignoriert.
     */
    public boolean sendPrivate(Player sender, Player target, String message) {
        String clean = Text.truncate(message, 256);
        if (isIgnoring(target.getUniqueId(), sender.getUniqueId())
                && !sender.hasPermission("klassensmp.chat.bypassignore")) {
            return false;
        }

        String out = Text.color(Text.replace(privateFormatOut,
                "%sender%", sender.getName(), "%target%", target.getName(), "%message%", clean));
        String in = Text.color(Text.replace(privateFormatIn,
                "%sender%", sender.getName(), "%target%", target.getName(), "%message%", clean));

        sender.sendMessage(out);
        target.sendMessage(in);

        lastMessage.put(sender.getUniqueId(), target.getUniqueId());
        lastMessage.put(target.getUniqueId(), sender.getUniqueId());

        broadcastSpy(sender, target, clean);
        return true;
    }

    private void broadcastSpy(Player sender, Player target, String message) {
        if (socialSpy.isEmpty()) {
            return;
        }
        String line = Text.color(Text.replace(socialSpyFormat,
                "%sender%", sender.getName(), "%target%", target.getName(), "%message%", message));
        for (UUID uuid : socialSpy) {
            if (uuid.equals(sender.getUniqueId()) || uuid.equals(target.getUniqueId())) {
                continue;
            }
            Player spy = Bukkit.getPlayer(uuid);
            if (spy != null && spy.hasPermission("klassensmp.socialspy")) {
                spy.sendMessage(line);
            }
        }
    }

    public Player getReplyTarget(Player player) {
        UUID uuid = lastMessage.get(player.getUniqueId());
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    public boolean toggleSocialSpy(Player player) {
        if (socialSpy.remove(player.getUniqueId())) {
            return false;
        }
        socialSpy.add(player.getUniqueId());
        return true;
    }

    public boolean hasSocialSpy(Player player) {
        return socialSpy.contains(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Ignorieren
    // ------------------------------------------------------------------

    public void loadIgnores(UUID uuid) {
        plugin.getDatabase().asyncQuery(connection -> {
            Set<String> result = new HashSet<>();
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT ignored FROM ks_ignores WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("ignored"));
                    }
                }
            }
            return result;
        }, result -> {
            Set<UUID> set = ConcurrentHashMap.newKeySet();
            if (result != null) {
                for (String raw : result) {
                    try {
                        set.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                        // fehlerhafter Eintrag wird uebergangen
                    }
                }
            }
            ignored.put(uuid, set);
        });
    }

    public void unload(UUID uuid) {
        ignored.remove(uuid);
        lastMessage.remove(uuid);
        socialSpy.remove(uuid);
        for (Map.Entry<UUID, UUID> entry : lastMessage.entrySet()) {
            if (uuid.equals(entry.getValue())) {
                lastMessage.remove(entry.getKey());
            }
        }
    }

    public boolean isIgnoring(UUID owner, UUID other) {
        Set<UUID> set = ignored.get(owner);
        return set != null && set.contains(other);
    }

    /** @return {@code true}, wenn der Spieler jetzt ignoriert wird. */
    public boolean toggleIgnore(UUID owner, UUID other) {
        Set<UUID> set = ignored.computeIfAbsent(owner, id -> ConcurrentHashMap.newKeySet());
        boolean nowIgnored;
        if (set.remove(other)) {
            nowIgnored = false;
            plugin.getDatabase().async(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM ks_ignores WHERE uuid = ? AND ignored = ?")) {
                    statement.setString(1, owner.toString());
                    statement.setString(2, other.toString());
                    statement.executeUpdate();
                }
            });
        } else {
            set.add(other);
            nowIgnored = true;
            plugin.getDatabase().async(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "REPLACE INTO ks_ignores (uuid, ignored) VALUES (?,?)")) {
                    statement.setString(1, owner.toString());
                    statement.setString(2, other.toString());
                    statement.executeUpdate();
                }
            });
        }
        return nowIgnored;
    }

    public List<String> ignoredNames(UUID owner) {
        Set<UUID> set = ignored.get(owner);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(set.size());
        for (UUID uuid : set) {
            var data = plugin.getPlayerDataManager().get(uuid);
            names.add(data == null ? uuid.toString() : data.getName());
        }
        return names;
    }

    /** Sendet eine Nachricht an alle Spieler mit Staff-Berechtigung. */
    public void sendStaffMessage(CommandSender sender, String message) {
        String format = plugin.getConfigManager().string("chat.staffchat-format",
                "&8[&cStaff&8] &7%name%&8: &f%message%");
        String line = Text.color(Text.replace(format,
                "%name%", sender.getName(), "%message%", Text.truncate(message, 256)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("klassensmp.staffchat")) {
                player.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }
}
