package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Punishment;
import de.klassensmp.model.PunishmentType;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Bans, Mutes, Verwarnungen und das Moderationslog.
 *
 * <p>Aktive Strafen werden im Speicher gehalten, damit die Pruefung beim
 * Login und bei jeder Chatnachricht ohne Datenbankzugriff auskommt. Jede
 * Aktion wird zusaetzlich in {@code moderation.log} protokolliert.</p>
 */
public final class ModerationManager {

    private final KlassenSMP plugin;

    private final Map<UUID, Punishment> activeBans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> activeMutes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> warningCounts = new ConcurrentHashMap<>();

    public ModerationManager(KlassenSMP plugin) {
        this.plugin = plugin;
        loadActive();
    }

    /** Laedt alle aktiven Strafen in den Speicher. */
    private void loadActive() {
        plugin.getDatabase().asyncQuery(connection -> {
            List<Punishment> list = new ArrayList<>();
            String sql = "SELECT * FROM ks_punishments WHERE active = 1 AND type IN ('BAN','MUTE','WARN')";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Punishment punishment = read(rs);
                    if (punishment != null) {
                        list.add(punishment);
                    }
                }
            }
            return list;
        }, list -> {
            if (list == null) {
                return;
            }
            for (Punishment punishment : list) {
                if (punishment.type() == PunishmentType.WARN) {
                    warningCounts.merge(punishment.target(), 1, Integer::sum);
                    continue;
                }
                if (punishment.isExpired()) {
                    deactivate(punishment.id());
                    continue;
                }
                if (punishment.type() == PunishmentType.BAN) {
                    activeBans.put(punishment.target(), punishment);
                } else {
                    activeMutes.put(punishment.target(), punishment);
                }
            }
            plugin.getLogger().info(activeBans.size() + " aktive Banns, " + activeMutes.size() + " aktive Mutes geladen.");
        });
    }

    private Punishment read(ResultSet rs) throws java.sql.SQLException {
        UUID uuid;
        try {
            uuid = UUID.fromString(rs.getString("target_uuid"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        PunishmentType type = PunishmentType.parse(rs.getString("type"));
        if (type == null) {
            return null;
        }
        return new Punishment(rs.getInt("id"), uuid, rs.getString("target_name"), type,
                rs.getString("reason"), rs.getString("staff"),
                rs.getLong("created"), rs.getLong("expires"), rs.getInt("active") != 0);
    }

    /** Startet die regelmaessige Pruefung auf abgelaufene Strafen. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                expireAll();
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
    }

    private void expireAll() {
        for (Map.Entry<UUID, Punishment> entry : activeBans.entrySet()) {
            if (entry.getValue().isExpired()) {
                activeBans.remove(entry.getKey());
                deactivate(entry.getValue().id());
            }
        }
        for (Map.Entry<UUID, Punishment> entry : activeMutes.entrySet()) {
            if (entry.getValue().isExpired()) {
                activeMutes.remove(entry.getKey());
                deactivate(entry.getValue().id());
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    plugin.getMessages().send(player, "moderation.mute-expired");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Abfragen
    // ------------------------------------------------------------------

    public Punishment getBan(UUID uuid) {
        Punishment punishment = activeBans.get(uuid);
        if (punishment == null) {
            return null;
        }
        if (punishment.isExpired()) {
            activeBans.remove(uuid);
            deactivate(punishment.id());
            return null;
        }
        return punishment;
    }

    public Punishment getMute(UUID uuid) {
        Punishment punishment = activeMutes.get(uuid);
        if (punishment == null) {
            return null;
        }
        if (punishment.isExpired()) {
            activeMutes.remove(uuid);
            deactivate(punishment.id());
            return null;
        }
        return punishment;
    }

    public boolean isBanned(UUID uuid) {
        return getBan(uuid) != null;
    }

    public boolean isMuted(UUID uuid) {
        return getMute(uuid) != null;
    }

    public int warningCount(UUID uuid) {
        return warningCounts.getOrDefault(uuid, 0);
    }

    /** Laedt die Strafhistorie eines Spielers asynchron. */
    public void history(UUID uuid, Consumer<List<Punishment>> callback) {
        plugin.getDatabase().asyncQuery(connection -> {
            List<Punishment> list = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM ks_punishments WHERE target_uuid = ? ORDER BY created DESC LIMIT 50")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        Punishment punishment = read(rs);
                        if (punishment != null) {
                            list.add(punishment);
                        }
                    }
                }
            }
            return list;
        }, list -> callback.accept(list == null ? List.of() : list));
    }

    // ------------------------------------------------------------------
    // Aktionen
    // ------------------------------------------------------------------

    /**
     * Sperrt einen Spieler.
     *
     * @param durationMillis 0 oder kleiner = dauerhaft
     */
    public void ban(UUID target, String targetName, String reason, String staff, long durationMillis) {
        long now = System.currentTimeMillis();
        long expires = durationMillis > 0 ? now + durationMillis : 0L;
        String cleanReason = cleanReason(reason);

        store(target, targetName, PunishmentType.BAN, cleanReason, staff, now, expires, id -> {
            Punishment punishment = new Punishment(id, target, targetName, PunishmentType.BAN,
                    cleanReason, staff, now, expires, true);
            activeBans.put(target, punishment);

            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                player.kickPlayer(buildBanScreen(punishment));
            }
        });

        log("BAN", staff, targetName, cleanReason, expires);
        broadcast("moderation.broadcast-ban", targetName, staff, cleanReason,
                durationMillis > 0 ? TimeUtil.formatDuration(durationMillis) : plugin.getMessages().plain("moderation.permanent"));
    }

    public boolean unban(UUID target, String staff) {
        Punishment punishment = activeBans.remove(target);
        if (punishment == null) {
            return false;
        }
        deactivate(punishment.id());
        log("UNBAN", staff, punishment.targetName(), "-", 0L);
        return true;
    }

    public void mute(UUID target, String targetName, String reason, String staff, long durationMillis) {
        long now = System.currentTimeMillis();
        long expires = durationMillis > 0 ? now + durationMillis : 0L;
        String cleanReason = cleanReason(reason);

        store(target, targetName, PunishmentType.MUTE, cleanReason, staff, now, expires, id -> {
            Punishment punishment = new Punishment(id, target, targetName, PunishmentType.MUTE,
                    cleanReason, staff, now, expires, true);
            activeMutes.put(target, punishment);

            Player player = Bukkit.getPlayer(target);
            if (player != null) {
                plugin.getMessages().send(player, "moderation.muted",
                        "%reason%", cleanReason,
                        "%time%", durationMillis > 0
                                ? TimeUtil.formatDuration(durationMillis)
                                : plugin.getMessages().plain("moderation.permanent"));
            }
        });

        log("MUTE", staff, targetName, cleanReason, expires);
        broadcast("moderation.broadcast-mute", targetName, staff, cleanReason,
                durationMillis > 0 ? TimeUtil.formatDuration(durationMillis) : plugin.getMessages().plain("moderation.permanent"));
    }

    public boolean unmute(UUID target, String staff) {
        Punishment punishment = activeMutes.remove(target);
        if (punishment == null) {
            return false;
        }
        deactivate(punishment.id());
        log("UNMUTE", staff, punishment.targetName(), "-", 0L);
        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            plugin.getMessages().send(player, "moderation.unmuted");
        }
        return true;
    }

    /**
     * Verwarnt einen Spieler und fuehrt die in der Config hinterlegte
     * Folgemassnahme aus, wenn eine Schwelle erreicht wird.
     */
    public int warn(UUID target, String targetName, String reason, String staff) {
        long now = System.currentTimeMillis();
        String cleanReason = cleanReason(reason);
        store(target, targetName, PunishmentType.WARN, cleanReason, staff, now, 0L, id -> {
        });

        int count = warningCounts.merge(target, 1, Integer::sum);
        log("WARN", staff, targetName, cleanReason, 0L);

        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            plugin.getMessages().send(player, "moderation.warned",
                    "%reason%", cleanReason, "%count%", String.valueOf(count));
        }
        broadcast("moderation.broadcast-warn", targetName, staff, cleanReason, String.valueOf(count));

        applyWarningThreshold(target, targetName, count, staff);
        return count;
    }

    /**
     * Fuehrt bei Erreichen einer Schwelle die konfigurierte Massnahme aus.
     * Standard ist eine zeitlich begrenzte Stummschaltung - es wird bewusst
     * niemals automatisch dauerhaft gebannt.
     */
    private void applyWarningThreshold(UUID target, String targetName, int count, String staff) {
        var section = plugin.getConfigManager().get().getConfigurationSection("moderation.warn-actions");
        if (section == null) {
            return;
        }
        String action = section.getString(String.valueOf(count));
        if (action == null || action.isBlank()) {
            return;
        }
        String[] parts = action.trim().split("\\s+", 2);
        String type = parts[0].toUpperCase(java.util.Locale.ROOT);
        long duration = parts.length > 1 ? TimeUtil.parseDuration(parts[1]) : -1;

        switch (type) {
            case "MUTE" -> mute(target, targetName,
                    plugin.getMessages().plain("moderation.auto-warn-reason", "%count%", String.valueOf(count)),
                    "KlassenSMP", duration > 0 ? duration : 3_600_000L);
            case "KICK" -> {
                Player player = Bukkit.getPlayer(target);
                if (player != null) {
                    kick(player, plugin.getMessages().plain("moderation.auto-warn-reason",
                            "%count%", String.valueOf(count)), staff);
                }
            }
            case "BAN" -> ban(target, targetName,
                    plugin.getMessages().plain("moderation.auto-warn-reason", "%count%", String.valueOf(count)),
                    "KlassenSMP", duration > 0 ? duration : 86_400_000L);
            default -> plugin.getLogger().warning("Unbekannte Verwarnungsaktion in der Config: " + action);
        }
    }

    public void kick(Player player, String reason, String staff) {
        String cleanReason = cleanReason(reason);
        store(player.getUniqueId(), player.getName(), PunishmentType.KICK, cleanReason, staff,
                System.currentTimeMillis(), 0L, id -> {
                });
        log("KICK", staff, player.getName(), cleanReason, 0L);
        broadcast("moderation.broadcast-kick", player.getName(), staff, cleanReason, "-");
        player.kickPlayer(plugin.getMessages().plain("moderation.kick-screen",
                "%reason%", cleanReason, "%staff%", staff));
    }

    /** Baut den Text, den ein gebannter Spieler beim Loginversuch sieht. */
    public String buildBanScreen(Punishment punishment) {
        String remaining = punishment.isPermanent()
                ? plugin.getMessages().plain("moderation.permanent")
                : TimeUtil.formatDuration(punishment.remaining());
        return plugin.getMessages().plain("moderation.ban-screen",
                "%reason%", punishment.reason(),
                "%staff%", punishment.staff(),
                "%time%", remaining,
                "%date%", TimeUtil.formatDate(punishment.created()));
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private String cleanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return plugin.getMessages().plain("moderation.default-reason");
        }
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }

    private void store(UUID target, String targetName, PunishmentType type, String reason,
                       String staff, long created, long expires, Consumer<Integer> callback) {
        plugin.getDatabase().asyncQuery(connection -> {
            String sql = """
                    INSERT INTO ks_punishments
                    (target_uuid, target_name, type, reason, staff, created, expires, active)
                    VALUES (?,?,?,?,?,?,?,1)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, target.toString());
                statement.setString(2, targetName);
                statement.setString(3, type.name());
                statement.setString(4, reason);
                statement.setString(5, staff);
                statement.setLong(6, created);
                statement.setLong(7, expires);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        }, id -> callback.accept(id == null ? -1 : id));
    }

    private void deactivate(int id) {
        if (id <= 0) {
            return;
        }
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("UPDATE ks_punishments SET active = 0 WHERE id = ?")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        });
    }

    private void broadcast(String key, String target, String staff, String reason, String time) {
        if (!plugin.getConfigManager().bool("moderation.broadcast", true)) {
            return;
        }
        String message = plugin.getMessages().get(key,
                "%player%", target, "%staff%", staff, "%reason%", reason, "%time%", time);
        Bukkit.broadcastMessage(message);
    }

    /** Schreibt eine Zeile in das Moderationslog. */
    private void log(String action, String staff, String target, String reason, long expires) {
        String line = TimeUtil.formatDate(System.currentTimeMillis())
                + " | " + action
                + " | Staff: " + staff
                + " | Ziel: " + target
                + " | Grund: " + reason
                + " | Bis: " + (expires > 0 ? TimeUtil.formatDate(expires) : "dauerhaft")
                + System.lineSeparator();

        plugin.getLogger().info("[Moderation] " + action + " " + target + " durch " + staff);

        if (!plugin.getConfigManager().bool("moderation.file-log", true)) {
            return;
        }
        Path path = plugin.getDataFolder().toPath().resolve("moderation.log");
        // Dateizugriff bewusst asynchron, damit der Main Thread nicht blockiert.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Moderationslog konnte nicht geschrieben werden: " + ex.getMessage());
            }
        });
    }
}
