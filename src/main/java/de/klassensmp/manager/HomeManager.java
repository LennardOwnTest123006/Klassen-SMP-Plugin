package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Home;
import de.klassensmp.util.LocationUtil;
import de.klassensmp.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet die Heimatpunkte der Spieler.
 *
 * <p>Homes werden beim Beitritt eines Spielers asynchron geladen und danach im
 * Speicher gehalten, damit {@code /home} keinen Datenbankzugriff benoetigt.</p>
 */
public final class HomeManager {

    private final KlassenSMP plugin;
    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();

    public HomeManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Laedt die Homes eines Spielers in den Cache.
     *
     * <p>Die Datenbank liefert nur Zeichenketten; die Umwandlung in echte
     * {@link Location}-Objekte passiert bewusst erst im Callback auf dem
     * Main Thread, weil dort auf die Weltenliste zugegriffen wird.</p>
     */
    public void loadFor(UUID uuid) {
        plugin.getDatabase().asyncQuery(connection -> {
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT name, location, created FROM ks_homes WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{
                                rs.getString("name"),
                                rs.getString("location"),
                                String.valueOf(rs.getLong("created"))
                        });
                    }
                }
            }
            return rows;
        }, rows -> {
            Map<String, Home> loaded = new ConcurrentHashMap<>();
            if (rows != null) {
                for (String[] row : rows) {
                    Location location = LocationUtil.deserialize(row[1]);
                    if (location == null) {
                        // Welt existiert nicht mehr - Home bleibt gespeichert, ist aber nicht nutzbar.
                        continue;
                    }
                    long created = 0L;
                    try {
                        created = Long.parseLong(row[2]);
                    } catch (NumberFormatException ignored) {
                        // Zeitstempel ist optional
                    }
                    loaded.put(row[0].toLowerCase(Locale.ROOT), new Home(row[0], location, created));
                }
            }
            homes.put(uuid, loaded);
        });
    }

    public void unload(UUID uuid) {
        homes.remove(uuid);
    }

    public List<Home> getHomes(UUID uuid) {
        Map<String, Home> map = homes.get(uuid);
        return map == null ? List.of() : new ArrayList<>(map.values());
    }

    public List<String> getHomeNames(UUID uuid) {
        Map<String, Home> map = homes.get(uuid);
        if (map == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(map.size());
        for (Home home : map.values()) {
            names.add(home.name());
        }
        return names;
    }

    public Home getHome(UUID uuid, String name) {
        Map<String, Home> map = homes.get(uuid);
        return map == null || name == null ? null : map.get(name.toLowerCase(Locale.ROOT));
    }

    public int count(UUID uuid) {
        Map<String, Home> map = homes.get(uuid);
        return map == null ? 0 : map.size();
    }

    /** Ergebnis eines {@code /sethome}. */
    public enum SetResult {
        SUCCESS,
        LIMIT_REACHED,
        INVALID_NAME,
        WORLD_DISABLED
    }

    public SetResult setHome(Player player, String rawName) {
        String name = Text.sanitizeName(rawName);
        if (name.isEmpty() || name.length() > 24) {
            return SetResult.INVALID_NAME;
        }
        if (plugin.getWorldManager().isHomeDisabled(player.getWorld().getName())) {
            return SetResult.WORLD_DISABLED;
        }
        UUID uuid = player.getUniqueId();
        Map<String, Home> map = homes.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());

        boolean replacing = map.containsKey(name);
        if (!replacing) {
            int max = plugin.getRankManager().getMaxHomes(player);
            if (max >= 0 && map.size() >= max) {
                return SetResult.LIMIT_REACHED;
            }
        }

        Location location = player.getLocation().clone();
        long now = System.currentTimeMillis();
        map.put(name, new Home(name, location, now));

        String serialized = LocationUtil.serialize(location);
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO ks_homes (uuid, name, location, created) VALUES (?,?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, serialized);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
        });
        plugin.getAchievementManager().checkHomes(player, map.size());
        return SetResult.SUCCESS;
    }

    public boolean deleteHome(UUID uuid, String rawName) {
        String name = Text.sanitizeName(rawName);
        Map<String, Home> map = homes.get(uuid);
        if (map == null || map.remove(name) == null) {
            return false;
        }
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("DELETE FROM ks_homes WHERE uuid = ? AND LOWER(name) = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.executeUpdate();
            }
        });
        return true;
    }
}
