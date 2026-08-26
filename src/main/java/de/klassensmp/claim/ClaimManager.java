package de.klassensmp.claim;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Eigenes, bewusst einfaches Claim-System.
 *
 * <p>Claims sind rechteckige Bereiche ueber die volle Weltenhoehe. Fuer eine
 * schnelle Pruefung werden sie zusaetzlich nach Chunks indiziert - dadurch
 * kostet die Frage "wem gehoert dieser Block?" nur einen Map-Zugriff und den
 * Vergleich der wenigen Claims in diesem Chunk.</p>
 *
 * <p>Ist auf dem Server ein etabliertes Claim-Plugin (z.B. GriefPrevention)
 * im Einsatz, kann dieses System in der Config vollstaendig deaktiviert
 * werden - der Schutz uebernimmt dann das andere Plugin.</p>
 */
public final class ClaimManager {

    private final KlassenSMP plugin;

    private final Map<Integer, Claim> claims = new ConcurrentHashMap<>();
    /**
     * Bereiche, deren Datenbankeintrag noch geschrieben wird. Sie zaehlen
     * bereits bei der Ueberschneidungspruefung mit, damit zwei schnell
     * hintereinander abgesetzte Befehle keine ueberlappenden Claims erzeugen.
     */
    private final List<int[]> pendingRegions = new CopyOnWriteArrayList<>();
    private final Map<int[], String> pendingWorlds = new ConcurrentHashMap<>();
    /** Weltname -> Chunk-Schluessel -> Claims, die diesen Chunk beruehren. */
    private final Map<String, Map<Long, List<Claim>>> chunkIndex = new ConcurrentHashMap<>();

    public ClaimManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().bool("claims.enabled", true);
    }

    // ------------------------------------------------------------------
    // Laden
    // ------------------------------------------------------------------

    public void load() {
        plugin.getDatabase().asyncQuery(connection -> {
            List<Claim> loaded = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ks_claims");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID owner;
                    try {
                        owner = UUID.fromString(rs.getString("owner"));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    loaded.add(new Claim(rs.getInt("id"), owner, rs.getString("world"),
                            rs.getInt("min_x"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_z"),
                            rs.getLong("created")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ks_claim_members");
                 ResultSet rs = statement.executeQuery()) {
                Map<Integer, Claim> byId = new ConcurrentHashMap<>();
                for (Claim claim : loaded) {
                    byId.put(claim.getId(), claim);
                }
                while (rs.next()) {
                    Claim claim = byId.get(rs.getInt("claim_id"));
                    if (claim == null) {
                        continue;
                    }
                    try {
                        claim.addMember(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException ignored) {
                        // fehlerhafter Eintrag
                    }
                }
            }
            return loaded;
        }, loaded -> {
            claims.clear();
            chunkIndex.clear();
            if (loaded != null) {
                for (Claim claim : loaded) {
                    claims.put(claim.getId(), claim);
                    index(claim);
                }
                plugin.getLogger().info(claims.size() + " Claims geladen.");
            }
        });
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void index(Claim claim) {
        Map<Long, List<Claim>> worldIndex =
                chunkIndex.computeIfAbsent(claim.getWorld().toLowerCase(Locale.ROOT), key -> new ConcurrentHashMap<>());
        for (int cx = claim.getMinX() >> 4; cx <= claim.getMaxX() >> 4; cx++) {
            for (int cz = claim.getMinZ() >> 4; cz <= claim.getMaxZ() >> 4; cz++) {
                worldIndex.computeIfAbsent(chunkKey(cx, cz), key -> new CopyOnWriteArrayList<>()).add(claim);
            }
        }
    }

    private void unindex(Claim claim) {
        Map<Long, List<Claim>> worldIndex = chunkIndex.get(claim.getWorld().toLowerCase(Locale.ROOT));
        if (worldIndex == null) {
            return;
        }
        for (int cx = claim.getMinX() >> 4; cx <= claim.getMaxX() >> 4; cx++) {
            for (int cz = claim.getMinZ() >> 4; cz <= claim.getMaxZ() >> 4; cz++) {
                List<Claim> list = worldIndex.get(chunkKey(cx, cz));
                if (list != null) {
                    list.remove(claim);
                    if (list.isEmpty()) {
                        worldIndex.remove(chunkKey(cx, cz));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Abfragen
    // ------------------------------------------------------------------

    /** @return das Claim an dieser Position oder {@code null}. */
    public Claim getClaimAt(Location location) {
        if (!isEnabled() || location == null || location.getWorld() == null) {
            return null;
        }
        Map<Long, List<Claim>> worldIndex = chunkIndex.get(location.getWorld().getName().toLowerCase(Locale.ROOT));
        if (worldIndex == null) {
            return null;
        }
        List<Claim> candidates = worldIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) {
            return null;
        }
        for (Claim claim : candidates) {
            if (claim.contains(location.getBlockX(), location.getBlockZ())) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Prueft, ob ein Spieler an dieser Position bauen darf.
     *
     * <p>Erlaubt sind: Bereiche ohne Claim, eigene Claims, Claims mit
     * Mitgliedschaft und Spieler mit {@code klassensmp.claims.bypass}.</p>
     */
    public boolean canBuild(Player player, Location location) {
        Claim claim = getClaimAt(location);
        if (claim == null) {
            return true;
        }
        if (claim.isMember(player.getUniqueId())) {
            return true;
        }
        return player.hasPermission("klassensmp.claims.bypass");
    }

    public List<Claim> claimsOf(UUID owner) {
        List<Claim> list = new ArrayList<>();
        for (Claim claim : claims.values()) {
            if (claim.getOwner().equals(owner)) {
                list.add(claim);
            }
        }
        list.sort(Comparator.comparingLong(Claim::getCreated));
        return list;
    }

    public List<Claim> allClaims() {
        return new ArrayList<>(claims.values());
    }

    public int count() {
        return claims.size();
    }

    public int blocksUsed(UUID owner) {
        int total = 0;
        for (Claim claim : claimsOf(owner)) {
            total += claim.area();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Erstellen / Loeschen
    // ------------------------------------------------------------------

    /** Ergebnis eines Claim-Versuchs. */
    public enum CreateResult {
        SUCCESS,
        DISABLED,
        WORLD_DISABLED,
        TOO_SMALL,
        TOO_LARGE,
        OVERLAPS,
        LIMIT_REACHED,
        BLOCK_LIMIT_REACHED
    }

    /**
     * Legt ein quadratisches Claim um eine Position an.
     *
     * @param radius halbe Kantenlaenge in Bloecken
     */
    public CreateResult create(Player player, Location center, int radius, Consumer<Claim> onCreated) {
        if (!isEnabled()) {
            return CreateResult.DISABLED;
        }
        if (center == null || center.getWorld() == null) {
            return CreateResult.WORLD_DISABLED;
        }
        String world = center.getWorld().getName();
        if (plugin.getWorldManager().isClaimDisabled(world)) {
            return CreateResult.WORLD_DISABLED;
        }

        int minSize = Math.max(1, plugin.getConfigManager().integer("claims.min-size", 5));
        int maxSize = Math.max(minSize, plugin.getConfigManager().integer("claims.max-size", 100));
        int size = radius * 2 + 1;
        if (size < minSize) {
            return CreateResult.TOO_SMALL;
        }
        if (size > maxSize && !player.hasPermission("klassensmp.claims.unlimited")) {
            return CreateResult.TOO_LARGE;
        }

        int maxClaims = plugin.getConfigManager().integer("claims.max-per-player", 3);
        if (maxClaims > 0 && !player.hasPermission("klassensmp.claims.unlimited")
                && claimsOf(player.getUniqueId()).size() >= maxClaims) {
            return CreateResult.LIMIT_REACHED;
        }

        int x1 = center.getBlockX() - radius;
        int z1 = center.getBlockZ() - radius;
        int x2 = center.getBlockX() + radius;
        int z2 = center.getBlockZ() + radius;

        int maxBlocks = plugin.getConfigManager().integer("claims.max-blocks-per-player", 40000);
        if (maxBlocks > 0 && !player.hasPermission("klassensmp.claims.unlimited")
                && blocksUsed(player.getUniqueId()) + size * size > maxBlocks) {
            return CreateResult.BLOCK_LIMIT_REACHED;
        }

        for (Claim existing : claims.values()) {
            if (existing.overlaps(world, x1, z1, x2, z2)) {
                return CreateResult.OVERLAPS;
            }
        }
        for (int[] pending : pendingRegions) {
            if (world.equals(pendingWorlds.get(pending))
                    && overlaps(pending, x1, z1, x2, z2)) {
                return CreateResult.OVERLAPS;
            }
        }

        int[] reserved = {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
        pendingRegions.add(reserved);
        pendingWorlds.put(reserved, world);

        long now = System.currentTimeMillis();
        UUID owner = player.getUniqueId();
        plugin.getDatabase().asyncQuery(connection -> {
            String sql = "INSERT INTO ks_claims (owner, world, min_x, min_z, max_x, max_z, created) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, owner.toString());
                statement.setString(2, world);
                statement.setInt(3, Math.min(x1, x2));
                statement.setInt(4, Math.min(z1, z2));
                statement.setInt(5, Math.max(x1, x2));
                statement.setInt(6, Math.max(z1, z2));
                statement.setLong(7, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        }, id -> {
            pendingRegions.remove(reserved);
            pendingWorlds.remove(reserved);
            if (id == null || id <= 0) {
                plugin.getMessages().send(player, "claims.save-failed");
                return;
            }
            Claim claim = new Claim(id, owner, world, x1, z1, x2, z2, now);
            claims.put(id, claim);
            index(claim);
            if (onCreated != null) {
                onCreated.accept(claim);
            }
        });
        return CreateResult.SUCCESS;
    }

    public boolean delete(Claim claim) {
        if (claim == null || claims.remove(claim.getId()) == null) {
            return false;
        }
        unindex(claim);
        int id = claim.getId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ks_claims WHERE id = ?")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ks_claim_members WHERE claim_id = ?")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        });
        return true;
    }

    // ------------------------------------------------------------------
    // Mitglieder
    // ------------------------------------------------------------------

    public void trust(Claim claim, UUID member) {
        if (claim == null || member == null || claim.getOwner().equals(member)) {
            return;
        }
        claim.addMember(member);
        int id = claim.getId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("REPLACE INTO ks_claim_members (claim_id, uuid) VALUES (?,?)")) {
                statement.setInt(1, id);
                statement.setString(2, member.toString());
                statement.executeUpdate();
            }
        });
    }

    public void untrust(Claim claim, UUID member) {
        if (claim == null || member == null) {
            return;
        }
        claim.removeMember(member);
        int id = claim.getId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("DELETE FROM ks_claim_members WHERE claim_id = ? AND uuid = ?")) {
                statement.setInt(1, id);
                statement.setString(2, member.toString());
                statement.executeUpdate();
            }
        });
    }

    /** Ueberschneidungspruefung fuer noch nicht gespeicherte Bereiche. */
    private boolean overlaps(int[] region, int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        return region[0] <= maxX && region[2] >= minX && region[1] <= maxZ && region[3] >= minZ;
    }

    /** Beim Herunterfahren gibt es nichts Ungespeichertes - Methode dient der Symmetrie. */
    public void saveAllBlocking() {
        // Claims werden bei jeder Aenderung sofort geschrieben.
    }
}
