package de.klassensmp.quest;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Quest;
import de.klassensmp.model.QuestPeriod;
import de.klassensmp.model.QuestProgress;
import de.klassensmp.model.QuestType;
import de.klassensmp.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Taegliche und woechentliche Aufgaben.
 *
 * <p>Welche Aufgaben ein Spieler in einem Zeitraum bekommt, wird deterministisch
 * aus seiner UUID und dem Zeitraum-Schluessel berechnet. Dadurch braucht es
 * keine zusaetzliche Zuweisungstabelle, und die Aufgaben bleiben ueber
 * Serverneustarts hinweg identisch - wechseln aber automatisch mit dem Tag
 * bzw. der Kalenderwoche.</p>
 */
public final class QuestManager {

    private static final String FILE = "quests.yml";

    private final KlassenSMP plugin;

    private final List<Quest> dailyPool = new ArrayList<>();
    private final List<Quest> weeklyPool = new ArrayList<>();
    private final Map<String, Quest> byId = new ConcurrentHashMap<>();

    /** UUID -> (QuestId -> Fortschritt) fuer den jeweils aktuellen Zeitraum. */
    private final Map<UUID, Map<String, QuestProgress>> progress = new ConcurrentHashMap<>();

    private int dailyCount = 3;
    private int weeklyCount = 2;

    public QuestManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Laden
    // ------------------------------------------------------------------

    public void load() {
        dailyPool.clear();
        weeklyPool.clear();
        byId.clear();

        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        this.dailyCount = Math.max(0, config.getInt("settings.daily-count", 3));
        this.weeklyCount = Math.max(0, config.getInt("settings.weekly-count", 2));

        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section == null) {
            plugin.getLogger().info("Keine Aufgaben konfiguriert.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            QuestType type = QuestType.parse(entry.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("Aufgabe '" + key + "' hat einen unbekannten Typ und wird uebersprungen.");
                continue;
            }
            QuestPeriod period = QuestPeriod.parse(entry.getString("period", "DAILY"));
            Quest quest = new Quest(key.toLowerCase(Locale.ROOT),
                    entry.getString("display", key),
                    entry.getString("description", ""),
                    entry.getString("icon", "PAPER"),
                    period,
                    type,
                    entry.getString("target", ""),
                    Math.max(1, entry.getInt("amount", 1)),
                    Math.max(0.0D, entry.getDouble("reward-money", 0.0D)),
                    Math.max(0, entry.getInt("reward-experience", 0)),
                    entry.getStringList("reward-commands"));
            byId.put(quest.id(), quest);
            if (period == QuestPeriod.WEEKLY) {
                weeklyPool.add(quest);
            } else {
                dailyPool.add(quest);
            }
        }
        plugin.getLogger().info((dailyPool.size() + weeklyPool.size()) + " Aufgaben geladen.");
    }

    /** Die Aufgaben, die ein Spieler im aktuellen Zeitraum hat. */
    public List<Quest> activeQuests(UUID uuid) {
        List<Quest> quests = new ArrayList<>();
        quests.addAll(pick(uuid, dailyPool, QuestPeriod.DAILY, dailyCount));
        quests.addAll(pick(uuid, weeklyPool, QuestPeriod.WEEKLY, weeklyCount));
        return quests;
    }

    /** Waehlt reproduzierbar {@code count} Aufgaben aus einem Pool. */
    private List<Quest> pick(UUID uuid, List<Quest> pool, QuestPeriod period, int count) {
        if (pool.isEmpty() || count <= 0) {
            return List.of();
        }
        List<Quest> copy = new ArrayList<>(pool);
        long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()
                ^ period.currentKey().hashCode();
        Collections.shuffle(copy, new Random(seed));
        return copy.subList(0, Math.min(count, copy.size()));
    }

    public Quest getQuest(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Fortschritt
    // ------------------------------------------------------------------

    public void loadFor(UUID uuid) {
        List<Quest> active = activeQuests(uuid);
        plugin.getDatabase().asyncQuery(connection -> {
            Map<String, QuestProgress> loaded = new ConcurrentHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quest_id, period, period_key, progress, target, claimed FROM ks_quests WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        QuestPeriod period = QuestPeriod.parse(rs.getString("period"));
                        String periodKey = rs.getString("period_key");
                        if (!period.currentKey().equals(periodKey)) {
                            continue; // abgelaufener Zeitraum
                        }
                        loaded.put(rs.getString("quest_id").toLowerCase(Locale.ROOT),
                                new QuestProgress(rs.getString("quest_id"), period, periodKey,
                                        rs.getInt("target"), rs.getInt("progress"), rs.getInt("claimed") != 0));
                    }
                }
            }
            return loaded;
        }, loaded -> {
            Map<String, QuestProgress> map = loaded == null ? new ConcurrentHashMap<>() : loaded;
            for (Quest quest : active) {
                map.computeIfAbsent(quest.id(), id -> new QuestProgress(
                        quest.id(), quest.period(), quest.period().currentKey(), quest.amount(), 0, false));
            }
            progress.put(uuid, map);
        });
    }

    public void unload(UUID uuid) {
        Map<String, QuestProgress> map = progress.remove(uuid);
        if (map != null) {
            saveDirty(uuid, map);
        }
    }

    public QuestProgress getProgress(UUID uuid, Quest quest) {
        Map<String, QuestProgress> map = progress.get(uuid);
        return map == null ? null : map.get(quest.id());
    }

    /**
     * Meldet Fortschritt fuer alle passenden Aufgaben eines Spielers.
     *
     * @param target optionaler Filterwert (Material- oder EntityType-Name)
     */
    public void addProgress(Player player, QuestType type, String target, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Map<String, QuestProgress> map = progress.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        for (Quest quest : activeQuests(player.getUniqueId())) {
            if (quest.type() != type || !quest.matchesTarget(target)) {
                continue;
            }
            QuestProgress current = map.get(quest.id());
            if (current == null || current.isClaimed()) {
                continue;
            }
            boolean completedNow = current.addProgress(amount);
            if (completedNow) {
                plugin.getMessages().send(player, "quests.completed", "%quest%", quest.displayName());
                if (plugin.getConfigManager().bool("sounds.enabled", true)) {
                    Compat.playSound(player,
                            plugin.getConfigManager().string("sounds.quest-complete", "entity.player.levelup"),
                            0.7F, 1.6F);
                }
            }
        }
    }

    /** Bequemer Zugriff fuer die Economy. */
    public void handleMoneyEarned(UUID uuid, double amount) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && amount >= 1) {
            addProgress(player, QuestType.EARN_MONEY, "", (int) Math.floor(amount));
        }
    }

    /** Ergebnis eines Belohnungsversuchs. */
    public enum ClaimResult {
        SUCCESS,
        NOT_COMPLETE,
        ALREADY_CLAIMED,
        UNKNOWN
    }

    public ClaimResult claim(Player player, Quest quest) {
        Map<String, QuestProgress> map = progress.get(player.getUniqueId());
        if (map == null || quest == null) {
            return ClaimResult.UNKNOWN;
        }
        QuestProgress current = map.get(quest.id());
        if (current == null) {
            return ClaimResult.UNKNOWN;
        }
        if (current.isClaimed()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (!current.isComplete()) {
            return ClaimResult.NOT_COMPLETE;
        }

        current.setClaimed(true);
        if (quest.moneyReward() > 0) {
            plugin.getEconomyManager().deposit(player.getUniqueId(), quest.moneyReward());
        }
        if (quest.experienceReward() > 0) {
            player.giveExp(quest.experienceReward());
        }
        for (String command : quest.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }
        saveOne(player.getUniqueId(), current);
        return ClaimResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Persistenz
    // ------------------------------------------------------------------

    private static final String UPSERT = """
            REPLACE INTO ks_quests (uuid, quest_id, period, period_key, progress, target, claimed)
            VALUES (?,?,?,?,?,?,?)
            """;

    private void saveOne(UUID uuid, QuestProgress entry) {
        String questId = entry.getQuestId();
        String period = entry.getPeriod().name();
        String periodKey = entry.getPeriodKey();
        int value = entry.getProgress();
        int target = entry.getTarget();
        int claimed = entry.isClaimed() ? 1 : 0;
        entry.clearDirty();

        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, questId);
                statement.setString(3, period);
                statement.setString(4, periodKey);
                statement.setInt(5, value);
                statement.setInt(6, target);
                statement.setInt(7, claimed);
                statement.executeUpdate();
            }
        });
    }

    private void saveDirty(UUID uuid, Map<String, QuestProgress> map) {
        for (QuestProgress entry : map.values()) {
            if (entry.isDirty()) {
                saveOne(uuid, entry);
            }
        }
    }

    /** Speichert regelmaessig alle geaenderten Fortschritte. */
    public void startAutoSave() {
        long seconds = Math.max(60L, plugin.getConfigManager().duration("quests.autosave-seconds", 180L));
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Map<String, QuestProgress>> entry : progress.entrySet()) {
                    saveDirty(entry.getKey(), entry.getValue());
                }
            }
        }.runTaskTimer(plugin, seconds * 20L, seconds * 20L);
    }

    public void saveAllBlocking() {
        for (Map.Entry<UUID, Map<String, QuestProgress>> entry : progress.entrySet()) {
            saveDirty(entry.getKey(), entry.getValue());
        }
    }
}
