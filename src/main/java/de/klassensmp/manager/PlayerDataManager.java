package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentraler Cache und Persistenz aller Spielerdaten.
 *
 * <p>Beim Start werden alle bekannten Spieler einmalig in den Speicher
 * geladen. Fuer ein Klassen-SMP ist das guenstig (wenige hundert Datensaetze)
 * und erspart im laufenden Betrieb saemtliche Datenbankzugriffe - insbesondere
 * fuer {@code /baltop}, Offline-Zahlungen und die Vault-Anbindung.</p>
 */
public final class PlayerDataManager {

    private final KlassenSMP plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    private volatile boolean loaded;

    public PlayerDataManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Laedt alle bekannten Spieler asynchron in den Cache. */
    public void load() {
        int limit = Math.max(100, plugin.getConfigManager().integer("database.cache-limit", 10000));
        plugin.getDatabase().asyncQuery(connection -> {
            List<PlayerData> result = new ArrayList<>();
            String sql = "SELECT * FROM ks_players ORDER BY last_join DESC LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        result.add(read(rs));
                    }
                }
            }
            return result;
        }, list -> {
            if (list != null) {
                for (PlayerData data : list) {
                    cache.put(data.getUuid(), data);
                    nameIndex.put(data.getName().toLowerCase(Locale.ROOT), data.getUuid());
                }
                plugin.getLogger().info(list.size() + " Spielerprofile geladen.");
            }
            loaded = true;
            // Spieler, die waehrend des Ladens verbunden haben, nachziehen.
            for (Player player : Bukkit.getOnlinePlayers()) {
                handleJoin(player);
            }
        });
    }

    private PlayerData read(ResultSet rs) throws java.sql.SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        PlayerData data = new PlayerData(uuid, rs.getString("name"));
        data.setFirstJoin(rs.getLong("first_join"));
        data.setLastJoin(rs.getLong("last_join"));
        data.setLastQuit(rs.getLong("last_quit"));
        data.setStoredPlaytime(rs.getLong("playtime"));
        data.setMoney(rs.getDouble("money"));
        data.setBank(rs.getDouble("bank"));
        data.setEarned(rs.getDouble("earned"));
        data.setSpent(rs.getDouble("spent"));
        data.setKills(rs.getInt("kills"));
        data.setDeaths(rs.getInt("deaths"));
        data.setMobKills(rs.getInt("mob_kills"));
        data.setBlocksBroken(rs.getLong("blocks_broken"));
        data.setBlocksPlaced(rs.getLong("blocks_placed"));
        data.setPvpEnabled(rs.getInt("pvp_enabled") != 0);
        data.setBedrock(rs.getInt("bedrock") != 0);
        data.clearDirty();
        return data;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Liefert die Daten eines Spielers, legt sie bei Bedarf an. */
    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData data = cache.computeIfAbsent(uuid, id -> {
            PlayerData created = new PlayerData(id, name);
            long now = System.currentTimeMillis();
            created.setFirstJoin(now);
            created.setStoredPlaytime(0L);
            created.setMoney(plugin.getConfigManager().number("economy.start-balance", 250.0D));
            created.markDirty();
            return created;
        });
        if (name != null) {
            data.setName(name);
            nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
        }
        return data;
    }

    public PlayerData get(UUID uuid) {
        return uuid == null ? null : cache.get(uuid);
    }

    public PlayerData get(Player player) {
        return player == null ? null : getOrCreate(player.getUniqueId(), player.getName());
    }

    public UUID findUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        return nameIndex.get(name.toLowerCase(Locale.ROOT));
    }

    public PlayerData findByName(String name) {
        UUID uuid = findUuidByName(name);
        return uuid == null ? null : cache.get(uuid);
    }

    /** Alle bekannten Spielernamen - Basis fuer Tab-Completion. */
    public List<String> knownNames() {
        List<PlayerData> values = new ArrayList<>(cache.values());
        List<String> names = new ArrayList<>(values.size());
        for (PlayerData data : values) {
            names.add(data.getName());
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Join / Quit
    // ------------------------------------------------------------------

    public void handleJoin(Player player) {
        PlayerData data = getOrCreate(player.getUniqueId(), player.getName());
        data.setLastJoin(System.currentTimeMillis());
        data.startSession();
        data.setBedrock(plugin.getHooks().floodgate().isBedrock(player));
        data.markDirty();
        save(data);
    }

    public void handleQuit(Player player) {
        PlayerData data = cache.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.endSession();
        data.setLastQuit(System.currentTimeMillis());
        data.markDirty();
        save(data);
    }

    // ------------------------------------------------------------------
    // Persistenz
    // ------------------------------------------------------------------

    private static final String UPSERT = """
            REPLACE INTO ks_players
            (uuid, name, first_join, last_join, last_quit, playtime, money, bank, earned, spent,
             kills, deaths, mob_kills, blocks_broken, blocks_placed, pvp_enabled, bedrock)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    /** Schreibt einen Datensatz asynchron. */
    public void save(PlayerData data) {
        if (data == null) {
            return;
        }
        Object[] values = snapshot(data);
        data.clearDirty();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                bind(statement, values);
                statement.executeUpdate();
            }
        });
    }

    /**
     * Erstellt eine Momentaufnahme aller Werte auf dem Main Thread.
     * Der Datenbank-Thread arbeitet danach nur noch mit unveraenderlichen Daten.
     */
    private Object[] snapshot(PlayerData data) {
        return new Object[]{
                data.getUuid().toString(),
                data.getName(),
                data.getFirstJoin(),
                data.getLastJoin(),
                data.getLastQuit(),
                data.getTotalPlaytime(),
                data.getMoney(),
                data.getBank(),
                data.getEarned(),
                data.getSpent(),
                data.getKills(),
                data.getDeaths(),
                data.getMobKills(),
                data.getBlocksBroken(),
                data.getBlocksPlaced(),
                data.isPvpEnabled() ? 1 : 0,
                data.isBedrock() ? 1 : 0
        };
    }

    private void bind(PreparedStatement statement, Object[] values) throws java.sql.SQLException {
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    /** Startet den periodischen Speichervorgang fuer geaenderte Datensaetze. */
    public void startAutoSave() {
        long intervalSeconds = Math.max(30L, plugin.getConfigManager().duration("database.autosave-seconds", 300L));
        new BukkitRunnable() {
            @Override
            public void run() {
                // Laufende Sessions in die Spielzeit uebernehmen.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerData data = cache.get(player.getUniqueId());
                    if (data != null) {
                        data.flushSession();
                    }
                }
                int saved = 0;
                for (PlayerData data : cache.values()) {
                    if (data.isDirty()) {
                        save(data);
                        saved++;
                    }
                }
                if (saved > 0 && plugin.getConfigManager().bool("database.log-autosave", false)) {
                    plugin.getLogger().info("Automatisch gespeichert: " + saved + " Profile.");
                }
            }
        }.runTaskTimer(plugin, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    /** Schreibt beim Herunterfahren alle geaenderten Datensaetze blockierend weg. */
    public void saveAllBlocking() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = cache.get(player.getUniqueId());
            if (data != null) {
                data.endSession();
                data.setLastQuit(System.currentTimeMillis());
            }
        }
        List<Object[]> rows = new ArrayList<>();
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                rows.add(snapshot(data));
                data.clearDirty();
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        plugin.getDatabase().blocking(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (Object[] values : rows) {
                    bind(statement, values);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (java.sql.SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        });
        plugin.getLogger().info(rows.size() + " Profile gespeichert.");
    }

    /** Rangliste nach Guthaben (Bar + Bank). */
    public List<PlayerData> topBalances(int limit) {
        List<PlayerData> list = new ArrayList<>(cache.values());
        list.sort(Comparator.comparingDouble((PlayerData data) -> data.getMoney() + data.getBank()).reversed());
        return list.size() <= limit ? list : new ArrayList<>(list.subList(0, limit));
    }

    /** Rangliste nach Spielzeit. */
    public List<PlayerData> topPlaytime(int limit) {
        List<PlayerData> list = new ArrayList<>(cache.values());
        list.sort(Comparator.comparingLong(PlayerData::getTotalPlaytime).reversed());
        return list.size() <= limit ? list : new ArrayList<>(list.subList(0, limit));
    }

    public int size() {
        return cache.size();
    }
}
