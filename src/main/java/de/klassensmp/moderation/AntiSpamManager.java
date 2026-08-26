package de.klassensmp.moderation;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.CooldownMap;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schutz gegen Chat- und Command-Spam.
 *
 * <p>Alle Schwellenwerte sind konfigurierbar. Das System verwarnt zuerst und
 * greift erst bei anhaltendem Missbrauch zu einer zeitlich begrenzten
 * Stummschaltung - es wird niemals automatisch gebannt.</p>
 */
public final class AntiSpamManager {

    private final KlassenSMP plugin;

    private final CooldownMap chatCooldown = new CooldownMap();
    private final CooldownMap commandCooldown = new CooldownMap();
    private final Map<UUID, Deque<Long>> chatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> commandTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private long chatCooldownMillis = 2000L;
    private long commandCooldownMillis = 500L;
    private int maxMessagesPerWindow = 5;
    private long windowMillis = 10_000L;
    private int maxCommandsPerWindow = 12;
    private int capsMinLength = 8;
    private int capsPercent = 70;
    private boolean blockDuplicates = true;
    private int violationsUntilMute = 5;
    private long muteSeconds = 300L;

    public AntiSpamManager(KlassenSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager();
        this.enabled = config.bool("antispam.enabled", true);
        this.chatCooldownMillis = Math.max(0L, config.duration("antispam.chat-cooldown-millis", 2000L));
        this.commandCooldownMillis = Math.max(0L, config.duration("antispam.command-cooldown-millis", 500L));
        this.maxMessagesPerWindow = Math.max(1, config.integer("antispam.max-messages", 5));
        this.windowMillis = Math.max(1000L, config.duration("antispam.window-seconds", 10L) * 1000L);
        this.maxCommandsPerWindow = Math.max(1, config.integer("antispam.max-commands", 12));
        this.capsMinLength = Math.max(3, config.integer("antispam.caps.min-length", 8));
        this.capsPercent = Math.min(100, Math.max(10, config.integer("antispam.caps.max-percent", 70)));
        this.blockDuplicates = config.bool("antispam.block-duplicates", true);
        this.violationsUntilMute = Math.max(0, config.integer("antispam.violations-until-mute", 5));
        this.muteSeconds = Math.max(10L, config.duration("antispam.mute-seconds", 300L));
    }

    /** Startet die regelmaessige Bereinigung der Zaehler. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                chatCooldown.cleanup();
                commandCooldown.cleanup();
                long limit = System.currentTimeMillis() - windowMillis;
                chatTimestamps.values().forEach(deque -> deque.removeIf(time -> time < limit));
                commandTimestamps.values().forEach(deque -> deque.removeIf(time -> time < limit));
                chatTimestamps.entrySet().removeIf(entry -> entry.getValue().isEmpty());
                commandTimestamps.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
    }

    /** Ergebnis einer Chat-Pruefung. */
    public enum Result {
        OK,
        COOLDOWN,
        TOO_FAST,
        DUPLICATE,
        TOO_MANY_CAPS
    }

    /**
     * Prueft eine Chatnachricht.
     *
     * @return das Ergebnis; bei {@link Result#OK} darf die Nachricht gesendet werden.
     */
    public Result checkChat(Player player, String message) {
        if (!enabled || player.hasPermission("klassensmp.antispam.bypass")) {
            return Result.OK;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (chatCooldown.isActive(uuid)) {
            return violation(player, Result.COOLDOWN);
        }

        Deque<Long> history = chatTimestamps.computeIfAbsent(uuid, id -> new ArrayDeque<>());
        synchronized (history) {
            history.removeIf(time -> time < now - windowMillis);
            if (history.size() >= maxMessagesPerWindow) {
                return violation(player, Result.TOO_FAST);
            }
            history.addLast(now);
        }

        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (blockDuplicates && normalized.equals(lastMessages.get(uuid))) {
            return violation(player, Result.DUPLICATE);
        }
        lastMessages.put(uuid, normalized);

        if (isTooManyCaps(message)) {
            return violation(player, Result.TOO_MANY_CAPS);
        }

        chatCooldown.set(uuid, chatCooldownMillis);
        return Result.OK;
    }

    private boolean isTooManyCaps(String message) {
        String letters = message.replaceAll("[^A-Za-z]", "");
        if (letters.length() < capsMinLength) {
            return false;
        }
        int upper = 0;
        for (char c : letters.toCharArray()) {
            if (Character.isUpperCase(c)) {
                upper++;
            }
        }
        return upper * 100 / letters.length() >= capsPercent;
    }

    /**
     * Prueft die Ausfuehrung eines Befehls.
     *
     * @return {@code true}, wenn der Befehl ausgefuehrt werden darf.
     */
    public boolean checkCommand(Player player) {
        if (!enabled || player.hasPermission("klassensmp.antispam.bypass")) {
            return true;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (commandCooldown.isActive(uuid)) {
            return false;
        }
        Deque<Long> history = commandTimestamps.computeIfAbsent(uuid, id -> new ArrayDeque<>());
        synchronized (history) {
            history.removeIf(time -> time < now - windowMillis);
            if (history.size() >= maxCommandsPerWindow) {
                violation(player, Result.TOO_FAST);
                return false;
            }
            history.addLast(now);
        }
        commandCooldown.set(uuid, commandCooldownMillis);
        return true;
    }

    /**
     * Zaehlt einen Verstoss und stummt den Spieler bei anhaltendem Spam
     * zeitlich begrenzt.
     */
    private Result violation(Player player, Result result) {
        if (violationsUntilMute <= 0) {
            return result;
        }
        int count = violations.merge(player.getUniqueId(), 1, Integer::sum);
        if (count >= violationsUntilMute) {
            violations.remove(player.getUniqueId());
            plugin.getModerationManager().mute(player.getUniqueId(), player.getName(),
                    plugin.getMessages().plain("antispam.mute-reason"),
                    "KlassenSMP", muteSeconds * 1000L);
        }
        return result;
    }

    public void resetViolations(UUID uuid) {
        violations.remove(uuid);
    }

    public void handleQuit(UUID uuid) {
        chatCooldown.clear(uuid);
        commandCooldown.clear(uuid);
        chatTimestamps.remove(uuid);
        commandTimestamps.remove(uuid);
        lastMessages.remove(uuid);
        violations.remove(uuid);
    }
}
