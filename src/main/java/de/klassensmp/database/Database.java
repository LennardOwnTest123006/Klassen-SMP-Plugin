package de.klassensmp.database;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Datenbankzugriff des Plugins.
 *
 * <p>Standard ist SQLite, optional MySQL/MariaDB. Saemtliche Zugriffe laufen
 * ueber einen einzelnen dedizierten Thread: dadurch blockiert nie der Main
 * Thread und es kann gleichzeitig keine Race Condition auf der Verbindung
 * entstehen. Alle Abfragen nutzen ausschliesslich PreparedStatements.</p>
 */
public final class Database {

    private final KlassenSMP plugin;
    private final ExecutorService executor;

    private Connection connection;
    private boolean mysql;
    private volatile boolean available;

    private String jdbcUrl;
    private String user;
    private String password;

    public Database(KlassenSMP plugin) {
        this.plugin = plugin;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "KlassenSMP-Database");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadExecutor(factory);
    }

    /**
     * Baut die Verbindung auf und legt das Schema an.
     *
     * @return {@code true}, wenn die Datenbank einsatzbereit ist.
     */
    public boolean connect() {
        var config = plugin.getConfigManager().get();
        String type = config.getString("database.type", "SQLITE").trim().toUpperCase(Locale.ROOT);
        this.mysql = type.equals("MYSQL") || type.equals("MARIADB");

        try {
            if (mysql) {
                String host = config.getString("database.mysql.host", "localhost");
                int port = config.getInt("database.mysql.port", 3306);
                String db = config.getString("database.mysql.database", "klassensmp");
                this.user = config.getString("database.mysql.user", "root");
                this.password = config.getString("database.mysql.password", "");
                boolean ssl = config.getBoolean("database.mysql.use-ssl", false);
                this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db
                        + "?useSSL=" + ssl
                        + "&allowPublicKeyRetrieval=" + !ssl
                        + "&characterEncoding=utf8"
                        + "&serverTimezone=UTC";
                loadDriver("com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver");
            } else {
                File file = new File(plugin.getDataFolder(), config.getString("database.sqlite.file", "database.db"));
                if (file.getParentFile() != null) {
                    file.getParentFile().mkdirs();
                }
                this.jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
                this.user = null;
                this.password = null;
                loadDriver("org.sqlite.JDBC");
            }

            openConnection();
            createSchema();
            this.available = true;
            plugin.getLogger().info("Datenbank verbunden (" + (mysql ? "MySQL/MariaDB" : "SQLite") + ").");
            return true;
        } catch (SQLException | ClassNotFoundException ex) {
            // Bewusst ohne Zugangsdaten geloggt.
            plugin.getLogger().log(Level.SEVERE, "Datenbankverbindung fehlgeschlagen: " + ex.getMessage());
            this.available = false;
            return false;
        }
    }

    private void loadDriver(String... candidates) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String candidate : candidates) {
            try {
                Class.forName(candidate);
                return;
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        throw last == null ? new ClassNotFoundException("Kein JDBC-Treiber gefunden") : last;
    }

    private void openConnection() throws SQLException {
        if (user != null) {
            this.connection = DriverManager.getConnection(jdbcUrl, user, password);
        } else {
            this.connection = DriverManager.getConnection(jdbcUrl);
        }
        if (!mysql) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA foreign_keys=ON");
            }
        }
    }

    /** Stellt sicher, dass die Verbindung noch nutzbar ist (Timeouts bei MySQL). */
    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            openConnection();
        }
        return connection;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isMysql() {
        return mysql;
    }

    /** Fuehrt eine Operation asynchron auf dem Datenbank-Thread aus. */
    public void async(SqlTask task) {
        if (!available || executor.isShutdown()) {
            return;
        }
        executor.execute(() -> runTask(task));
    }

    /**
     * Fuehrt eine Abfrage asynchron aus und liefert das Ergebnis wieder
     * auf dem Main Thread an {@code callback}.
     */
    public <T> void asyncQuery(SqlQuery<T> query, Consumer<T> callback) {
        if (!available || executor.isShutdown()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }
        executor.execute(() -> {
            T result = null;
            try {
                result = query.run(connection());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Datenbankabfrage fehlgeschlagen: " + ex.getMessage());
            }
            T finalResult = result;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
            }
        });
    }

    /**
     * Fuehrt eine Operation blockierend aus. Nur fuer den Shutdown gedacht,
     * niemals waehrend des laufenden Betriebs vom Main Thread aufrufen.
     */
    public void blocking(SqlTask task) {
        runTask(task);
    }

    private void runTask(SqlTask task) {
        try {
            task.run(connection());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Datenbankoperation fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void createSchema() throws SQLException {
        String autoId = mysql
                ? "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";

        String[] statements = {
                """
                CREATE TABLE IF NOT EXISTS ks_players (
                    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    first_join BIGINT NOT NULL DEFAULT 0,
                    last_join BIGINT NOT NULL DEFAULT 0,
                    last_quit BIGINT NOT NULL DEFAULT 0,
                    playtime BIGINT NOT NULL DEFAULT 0,
                    money DOUBLE NOT NULL DEFAULT 0,
                    bank DOUBLE NOT NULL DEFAULT 0,
                    earned DOUBLE NOT NULL DEFAULT 0,
                    spent DOUBLE NOT NULL DEFAULT 0,
                    kills INT NOT NULL DEFAULT 0,
                    deaths INT NOT NULL DEFAULT 0,
                    mob_kills INT NOT NULL DEFAULT 0,
                    blocks_broken BIGINT NOT NULL DEFAULT 0,
                    blocks_placed BIGINT NOT NULL DEFAULT 0,
                    pvp_enabled INT NOT NULL DEFAULT 1,
                    bedrock INT NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_homes (
                    uuid VARCHAR(36) NOT NULL,
                    name VARCHAR(32) NOT NULL,
                    location TEXT NOT NULL,
                    created BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, name)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_warps (
                    name VARCHAR(32) NOT NULL PRIMARY KEY,
                    location TEXT NOT NULL,
                    permission VARCHAR(64) NOT NULL DEFAULT '',
                    icon VARCHAR(64) NOT NULL DEFAULT '',
                    creator VARCHAR(36) NOT NULL DEFAULT '',
                    created BIGINT NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_punishments (
                    """ + autoId + """
                    ,
                    target_uuid VARCHAR(36) NOT NULL,
                    target_name VARCHAR(16) NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    reason VARCHAR(255) NOT NULL DEFAULT '',
                    staff VARCHAR(32) NOT NULL DEFAULT 'Konsole',
                    created BIGINT NOT NULL DEFAULT 0,
                    expires BIGINT NOT NULL DEFAULT 0,
                    active INT NOT NULL DEFAULT 1
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_achievements (
                    uuid VARCHAR(36) NOT NULL,
                    achievement VARCHAR(48) NOT NULL,
                    unlocked BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, achievement)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_quests (
                    uuid VARCHAR(36) NOT NULL,
                    quest_id VARCHAR(48) NOT NULL,
                    period VARCHAR(8) NOT NULL,
                    period_key VARCHAR(16) NOT NULL,
                    progress INT NOT NULL DEFAULT 0,
                    target INT NOT NULL DEFAULT 1,
                    claimed INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, quest_id, period_key)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_kit_uses (
                    uuid VARCHAR(36) NOT NULL,
                    kit VARCHAR(32) NOT NULL,
                    last_used BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid, kit)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_claims (
                    """ + autoId + """
                    ,
                    owner VARCHAR(36) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    min_x INT NOT NULL,
                    min_z INT NOT NULL,
                    max_x INT NOT NULL,
                    max_z INT NOT NULL,
                    created BIGINT NOT NULL DEFAULT 0
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_claim_members (
                    claim_id INT NOT NULL,
                    uuid VARCHAR(36) NOT NULL,
                    PRIMARY KEY (claim_id, uuid)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_ignores (
                    uuid VARCHAR(36) NOT NULL,
                    ignored VARCHAR(36) NOT NULL,
                    PRIMARY KEY (uuid, ignored)
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS ks_graves (
                    """ + autoId + """
                    ,
                    owner VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL DEFAULT '',
                    world VARCHAR(64) NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    created BIGINT NOT NULL DEFAULT 0,
                    expires BIGINT NOT NULL DEFAULT 0,
                    experience INT NOT NULL DEFAULT 0,
                    contents TEXT NOT NULL,
                    claimed INT NOT NULL DEFAULT 0
                )
                """
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.executeUpdate(sql);
            }
        }
        createIndex("ks_idx_players_name", "ks_players (name)");
        createIndex("ks_idx_players_money", "ks_players (money)");
        createIndex("ks_idx_punish_target", "ks_punishments (target_uuid, type, active)");
        createIndex("ks_idx_claims_world", "ks_claims (world)");
        createIndex("ks_idx_graves_owner", "ks_graves (owner, claimed)");
    }

    /**
     * Legt einen Index an. MySQL kennt kein {@code CREATE INDEX IF NOT EXISTS},
     * daher wird ein bereits vorhandener Index bewusst still uebergangen.
     */
    private void createIndex(String name, String definition) {
        String sql = "CREATE INDEX " + (mysql ? "" : "IF NOT EXISTS ") + name + " ON " + definition;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ignored) {
            // Index existiert bereits - kein Fehlerfall.
        }
    }

    /**
     * Beendet den Datenbank-Thread. Bereits eingereihte Schreibvorgaenge
     * werden noch abgearbeitet, damit keine Spielerdaten verloren gehen.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Datenbank-Thread konnte nicht rechtzeitig beendet werden.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        available = false;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Datenbank konnte nicht sauber geschlossen werden: " + ex.getMessage());
        }
    }
}
