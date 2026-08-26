package de.klassensmp.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ein rechteckiges, schuetzendes Grundstueck.
 *
 * <p>Claims sind bewusst zweidimensional (X/Z, volle Weltenhoehe). Das ist fuer
 * ein Klassen-SMP voellig ausreichend und erlaubt eine sehr schnelle Pruefung
 * ohne aufwendige Raumsuche.</p>
 */
public final class Claim {

    private final int id;
    private final UUID owner;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final long created;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    public Claim(int id, UUID owner, String world, int x1, int z1, int x2, int z2, long created) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxZ = Math.max(z1, z2);
        this.created = created;
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getWorld() {
        return world;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public long getCreated() {
        return created;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(UUID uuid) {
        if (uuid != null && !uuid.equals(owner)) {
            members.add(uuid);
        }
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return uuid != null && (uuid.equals(owner) || members.contains(uuid));
    }

    public boolean contains(Location location) {
        return location != null
                && location.getWorld() != null
                && location.getWorld().getName().equals(world)
                && contains(location.getBlockX(), location.getBlockZ());
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Prueft, ob sich dieses Claim mit einem anderen Bereich ueberschneidet. */
    public boolean overlaps(String otherWorld, int x1, int z1, int x2, int z2) {
        if (!world.equals(otherWorld)) {
            return false;
        }
        int oMinX = Math.min(x1, x2);
        int oMaxX = Math.max(x1, x2);
        int oMinZ = Math.min(z1, z2);
        int oMaxZ = Math.max(z1, z2);
        return minX <= oMaxX && maxX >= oMinX && minZ <= oMaxZ && maxZ >= oMinZ;
    }

    /** Flaeche in Bloecken. */
    public int area() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }
}
