package de.klassensmp.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Serialisierung und Sicherheitspruefungen fuer Positionen. */
public final class LocationUtil {

    private LocationUtil() {
    }

    /** Serialisiert eine Location als "welt;x;y;z;yaw;pitch". */
    public static String serialize(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getName() + ';'
                + location.getX() + ';'
                + location.getY() + ';'
                + location.getZ() + ';'
                + location.getYaw() + ';'
                + location.getPitch();
    }

    /** Liest eine serialisierte Location. Gibt {@code null} zurueck, wenn die Welt fehlt. */
    public static Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0F;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0F;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Kurzform fuer Anzeigen: "world 120 / 64 / -310". */
    public static String pretty(Location location) {
        if (location == null || location.getWorld() == null) {
            return "-";
        }
        return location.getWorld().getName() + " "
                + location.getBlockX() + " / "
                + location.getBlockY() + " / "
                + location.getBlockZ();
    }

    /**
     * Prueft, ob an dieser Position gefahrlos gestanden werden kann:
     * fester Boden, zwei freie Bloecke und kein Lava/Feuer.
     */
    public static boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        int y = location.getBlockY();
        if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (isDangerous(feet.getType()) || isDangerous(head.getType()) || isDangerous(ground.getType())) {
            return false;
        }
        return ground.getType().isSolid();
    }

    private static boolean isDangerous(Material material) {
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.MAGMA_BLOCK
                || material == Material.CACTUS
                || material == Material.POWDER_SNOW;
    }

    /**
     * Sucht ausgehend von {@code origin} die naechste sichere Position.
     * Es wird nur in bereits geladenen bzw. angrenzenden Bloecken gesucht,
     * damit keine unnoetigen Chunk-Generierungen ausgeloest werden.
     *
     * @return sichere Location oder {@code null}, wenn keine gefunden wurde.
     */
    public static Location findSafe(Location origin, int horizontalRadius, int verticalRadius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        if (isSafe(origin)) {
            return origin;
        }
        World world = origin.getWorld();
        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue; // nur der aeussere Ring, innere wurden bereits geprueft
                    }
                    for (int dy = 0; dy <= verticalRadius; dy++) {
                        for (int sign : new int[]{1, -1}) {
                            int y = origin.getBlockY() + dy * sign;
                            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) {
                                continue;
                            }
                            Location candidate = new Location(world,
                                    origin.getBlockX() + dx + 0.5D, y,
                                    origin.getBlockZ() + dz + 0.5D,
                                    origin.getYaw(), origin.getPitch());
                            if (isSafe(candidate)) {
                                return candidate;
                            }
                            if (dy == 0) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Zentriert eine Location auf die Blockmitte (fuer sauberes Teleportieren). */
    public static Location center(Location location) {
        if (location == null) {
            return null;
        }
        Location copy = location.clone();
        copy.setX(location.getBlockX() + 0.5D);
        copy.setZ(location.getBlockZ() + 0.5D);
        return copy;
    }

    /** Quadratische Distanz in der XZ-Ebene - vermeidet teure Wurzelberechnungen. */
    public static double distanceSquared2D(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
