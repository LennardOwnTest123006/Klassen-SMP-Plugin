package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Verwaltet den Serverspawn.
 *
 * <p>Der Spawn liegt in einer eigenen Datei ({@code spawn.yml}) und nicht in
 * der Datenbank - so bleibt er auch dann erhalten, wenn die Datenbank
 * ausgetauscht wird.</p>
 */
public final class SpawnManager {

    private static final String FILE = "spawn.yml";

    private final KlassenSMP plugin;
    private Location spawn;

    public SpawnManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        this.spawn = LocationUtil.deserialize(config.getString("spawn"));
        if (spawn == null) {
            World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (main != null) {
                this.spawn = main.getSpawnLocation();
            }
        }
    }

    /** @return der gesetzte Spawn oder der Weltspawn der Hauptwelt. */
    public Location getSpawn() {
        if (spawn != null && spawn.getWorld() != null) {
            return spawn.clone();
        }
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getSpawnLocation();
    }

    public void setSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        this.spawn = location.clone();
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        config.set("spawn", LocationUtil.serialize(this.spawn));
        plugin.getConfigManager().saveFile(config, FILE);
    }

    /** Radius des Spawnschutzes aus der Config. */
    public int getProtectionRadius() {
        return Math.max(0, plugin.getConfigManager().integer("protection.spawn.radius", 32));
    }

    /** Prueft, ob eine Position im geschuetzten Spawnbereich liegt. */
    public boolean isInSpawnArea(Location location) {
        Location current = getSpawn();
        if (current == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (!plugin.getConfigManager().bool("protection.spawn.enabled", true)) {
            return false;
        }
        if (!location.getWorld().equals(current.getWorld())) {
            return false;
        }
        int radius = getProtectionRadius();
        return LocationUtil.distanceSquared2D(location, current) <= (double) radius * radius;
    }
}
