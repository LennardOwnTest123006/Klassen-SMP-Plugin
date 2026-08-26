package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Warp;
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

/** Verwaltet die serverweiten Warp-Punkte. */
public final class WarpManager {

    private final KlassenSMP plugin;
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDatabase().asyncQuery(connection -> {
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ks_warps");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[]{
                            rs.getString("name"),
                            rs.getString("location"),
                            rs.getString("permission"),
                            rs.getString("icon"),
                            rs.getString("creator"),
                            String.valueOf(rs.getLong("created"))
                    });
                }
            }
            return rows;
        }, rows -> {
            warps.clear();
            if (rows == null) {
                return;
            }
            for (String[] row : rows) {
                Location location = LocationUtil.deserialize(row[1]);
                if (location == null) {
                    plugin.getLogger().warning("Warp '" + row[0] + "' zeigt auf eine nicht geladene Welt.");
                    continue;
                }
                long created = 0L;
                try {
                    created = Long.parseLong(row[5]);
                } catch (NumberFormatException ignored) {
                    // optional
                }
                warps.put(row[0].toLowerCase(Locale.ROOT),
                        new Warp(row[0], location, row[2] == null ? "" : row[2],
                                row[3] == null ? "" : row[3], row[4] == null ? "" : row[4], created));
            }
            plugin.getLogger().info(warps.size() + " Warps geladen.");
        });
    }

    public Warp get(String name) {
        return name == null ? null : warps.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Warp> all() {
        return new ArrayList<>(warps.values());
    }

    /** Warps, die der Spieler tatsaechlich benutzen darf. */
    public List<Warp> visibleFor(Player player) {
        List<Warp> visible = new ArrayList<>();
        for (Warp warp : warps.values()) {
            if (canUse(player, warp)) {
                visible.add(warp);
            }
        }
        visible.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return visible;
    }

    public boolean canUse(Player player, Warp warp) {
        if (warp == null || player == null) {
            return false;
        }
        return !warp.isProtected() || player.hasPermission(warp.permission());
    }

    /**
     * Legt einen Warp an oder ueberschreibt ihn.
     *
     * @return {@code false}, wenn der Name ungueltig ist.
     */
    public boolean setWarp(String rawName, Location location, String permission, String icon, UUID creator) {
        String name = Text.sanitizeName(rawName);
        if (name.isEmpty() || name.length() > 24 || location == null || location.getWorld() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String perm = permission == null ? "" : permission;
        String ico = icon == null ? "" : icon;
        String creatorId = creator == null ? "" : creator.toString();

        warps.put(name, new Warp(name, location.clone(), perm, ico, creatorId, now));

        String serialized = LocationUtil.serialize(location);
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO ks_warps (name, location, permission, icon, creator, created) VALUES (?,?,?,?,?,?)")) {
                statement.setString(1, name);
                statement.setString(2, serialized);
                statement.setString(3, perm);
                statement.setString(4, ico);
                statement.setString(5, creatorId);
                statement.setLong(6, now);
                statement.executeUpdate();
            }
        });
        return true;
    }

    public boolean deleteWarp(String rawName) {
        String name = Text.sanitizeName(rawName);
        if (warps.remove(name) == null) {
            return false;
        }
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ks_warps WHERE LOWER(name) = ?")) {
                statement.setString(1, name);
                statement.executeUpdate();
            }
        });
        return true;
    }

    public int size() {
        return warps.size();
    }
}
