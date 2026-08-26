package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Weltbezogene Einstellungen und Informationen.
 *
 * <p>Es wird ausschliesslich die Bukkit-World-API genutzt. Das Plugin erzeugt
 * keine Welten selbst - es arbeitet mit den Welten, die der Server laedt
 * (inklusive Event-, Test- und Minigame-Welten).</p>
 */
public final class WorldManager {

    private final KlassenSMP plugin;

    public WorldManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public List<World> worlds() {
        return new ArrayList<>(Bukkit.getWorlds());
    }

    public List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    private List<String> lowerList(String path) {
        List<String> raw = plugin.getConfigManager().get().getStringList(path);
        List<String> lower = new ArrayList<>(raw.size());
        for (String entry : raw) {
            lower.add(entry.toLowerCase(Locale.ROOT));
        }
        return lower;
    }

    /** Welten, in denen KlassenSMP komplett passiv bleibt. */
    public boolean isDisabled(String worldName) {
        return worldName != null && lowerList("worlds.disabled").contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Welten, in denen kein Home gesetzt werden darf. */
    public boolean isHomeDisabled(String worldName) {
        return worldName != null && lowerList("worlds.no-homes").contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Welten, in denen keine Claims erlaubt sind. */
    public boolean isClaimDisabled(String worldName) {
        return worldName != null && lowerList("worlds.no-claims").contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Welten, in denen PvP grundsaetzlich verboten ist. */
    public boolean isPvpDisabled(String worldName) {
        return worldName != null && lowerList("worlds.no-pvp").contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Besonders geschuetzte Welten (Anti-Grief): nur mit Berechtigung bebaubar. */
    public boolean isProtected(String worldName) {
        return worldName != null && lowerList("protection.protected-worlds").contains(worldName.toLowerCase(Locale.ROOT));
    }

    public World world(String name) {
        return name == null ? null : Bukkit.getWorld(name);
    }

    /** Gesamtzahl geladener Chunks ueber alle Welten. */
    public int loadedChunks() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getLoadedChunks().length;
        }
        return total;
    }

    /** Gesamtzahl aller Entities ueber alle Welten. */
    public int entityCount() {
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getEntities().size();
        }
        return total;
    }
}
