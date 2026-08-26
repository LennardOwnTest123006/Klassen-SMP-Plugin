package de.klassensmp.achievement;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Achievement;
import de.klassensmp.model.AchievementTrigger;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Erfolge des Klassen-SMP.
 *
 * <p>Die Freischaltungen liegen pro Spieler im Speicher; geprueft wird nur an
 * den Stellen, an denen sich der jeweilige Zaehler tatsaechlich aendert. Es
 * laufen keine periodischen Scans ueber alle Erfolge.</p>
 */
public final class AchievementManager {

    private static final String FILE = "achievements.yml";

    private final KlassenSMP plugin;
    private final Map<String, Achievement> achievements = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new ConcurrentHashMap<>();

    public AchievementManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        achievements.clear();
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        ConfigurationSection section = config.getConfigurationSection("achievements");
        if (section == null) {
            plugin.getLogger().info("Keine Erfolge konfiguriert.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            AchievementTrigger trigger = AchievementTrigger.parse(entry.getString("trigger"));
            if (trigger == null) {
                plugin.getLogger().warning("Erfolg '" + key + "' hat einen unbekannten Ausloeser.");
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            achievements.put(id, new Achievement(id,
                    entry.getString("display", key),
                    entry.getString("description", ""),
                    entry.getString("icon", "PAPER"),
                    trigger,
                    Math.max(1L, entry.getLong("amount", 1L)),
                    Math.max(0.0D, entry.getDouble("reward-money", 0.0D)),
                    Math.max(0, entry.getInt("reward-experience", 0)),
                    entry.getStringList("reward-commands")));
        }
        plugin.getLogger().info(achievements.size() + " Erfolge geladen.");
    }

    public List<Achievement> all() {
        List<Achievement> list = new ArrayList<>(achievements.values());
        list.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return list;
    }

    public Achievement get(String id) {
        return id == null ? null : achievements.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean hasUnlocked(UUID uuid, String achievementId) {
        Set<String> set = unlocked.get(uuid);
        return set != null && set.contains(achievementId);
    }

    public int unlockedCount(UUID uuid) {
        Set<String> set = unlocked.get(uuid);
        return set == null ? 0 : set.size();
    }

    public int total() {
        return achievements.size();
    }

    // ------------------------------------------------------------------
    // Laden pro Spieler
    // ------------------------------------------------------------------

    public void loadFor(UUID uuid) {
        plugin.getDatabase().asyncQuery(connection -> {
            Set<String> result = new HashSet<>();
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT achievement FROM ks_achievements WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("achievement"));
                    }
                }
            }
            return result;
        }, result -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            if (result != null) {
                set.addAll(result);
            }
            unlocked.put(uuid, set);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                checkFirstJoin(player);
            }
        });
    }

    public void unload(UUID uuid) {
        unlocked.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Pruefungen
    // ------------------------------------------------------------------

    public void checkFirstJoin(Player player) {
        unlockByTrigger(player, AchievementTrigger.FIRST_JOIN, 1L);
    }

    public void checkBlocksBroken(Player player, long total) {
        unlockByTrigger(player, AchievementTrigger.BLOCKS_BROKEN, total);
    }

    public void checkBlocksPlaced(Player player, long total) {
        unlockByTrigger(player, AchievementTrigger.BLOCKS_PLACED, total);
    }

    public void checkKills(Player player, long total) {
        unlockByTrigger(player, AchievementTrigger.PLAYER_KILLS, total);
    }

    public void checkMobKills(Player player, long total) {
        unlockByTrigger(player, AchievementTrigger.MOB_KILLS, total);
    }

    public void checkHomes(Player player, long total) {
        unlockByTrigger(player, AchievementTrigger.HOMES_SET, total);
    }

    public void checkDiamond(Player player) {
        unlockByTrigger(player, AchievementTrigger.MINE_DIAMOND, 1L);
    }

    public void checkNether(Player player) {
        unlockByTrigger(player, AchievementTrigger.ENTER_NETHER, 1L);
    }

    public void checkEnd(Player player) {
        unlockByTrigger(player, AchievementTrigger.ENTER_END, 1L);
    }

    public void checkDragon(Player player) {
        unlockByTrigger(player, AchievementTrigger.KILL_DRAGON, 1L);
    }

    public void checkMoney(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (player != null && data != null) {
            unlockByTrigger(player, AchievementTrigger.MONEY_EARNED, (long) data.getEarned());
        }
    }

    public void checkPlaytime(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data != null) {
            unlockByTrigger(player, AchievementTrigger.PLAYTIME_MINUTES,
                    TimeUnit.MILLISECONDS.toMinutes(data.getTotalPlaytime()));
        }
    }

    private void unlockByTrigger(Player player, AchievementTrigger trigger, long value) {
        Set<String> set = unlocked.get(player.getUniqueId());
        if (set == null) {
            return; // Daten noch nicht geladen
        }
        for (Achievement achievement : achievements.values()) {
            if (achievement.trigger() != trigger || set.contains(achievement.id())) {
                continue;
            }
            if (value >= achievement.amount()) {
                unlock(player, achievement);
            }
        }
    }

    /** Schaltet einen Erfolg frei und vergibt die Belohnung. */
    public void unlock(Player player, Achievement achievement) {
        Set<String> set = unlocked.computeIfAbsent(player.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        if (!set.add(achievement.id())) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO ks_achievements (uuid, achievement, unlocked) VALUES (?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, achievement.id());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        });

        if (achievement.moneyReward() > 0) {
            plugin.getEconomyManager().deposit(uuid, achievement.moneyReward());
        }
        if (achievement.experienceReward() > 0) {
            player.giveExp(achievement.experienceReward());
        }
        for (String command : achievement.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }

        plugin.getMessages().send(player, "achievements.unlocked",
                "%achievement%", achievement.displayName(),
                "%description%", achievement.description());

        if (plugin.getConfigManager().bool("achievements.title", true)) {
            player.sendTitle(
                    plugin.getMessages().plain("achievements.title-main"),
                    plugin.getMessages().plain("achievements.title-sub", "%achievement%", achievement.displayName()),
                    10, 50, 20);
        }
        if (plugin.getConfigManager().bool("sounds.enabled", true)) {
            Compat.playSound(player,
                    plugin.getConfigManager().string("sounds.achievement", "ui.toast.challenge_complete"), 0.8F, 1.0F);
        }
        if (plugin.getConfigManager().bool("achievements.broadcast", true)) {
            Bukkit.broadcastMessage(plugin.getMessages().get("achievements.broadcast",
                    "%player%", player.getName(),
                    "%achievement%", achievement.displayName()));
        }
    }
}
