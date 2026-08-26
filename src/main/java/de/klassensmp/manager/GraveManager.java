package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.gui.GraveGui;
import de.klassensmp.model.Grave;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemSerializer;
import de.klassensmp.util.LocationUtil;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graeber-System.
 *
 * <p>Stirbt ein Spieler, wandert sein Inventar in ein Grab statt auf den Boden.
 * Der Inhalt existiert dabei genau einmal: er liegt ausschliesslich im
 * {@link Grave}-Objekt bzw. in der geoeffneten Oberflaeche. Dadurch ist eine
 * Item-Duplikation ausgeschlossen.</p>
 */
public final class GraveManager {

    private final KlassenSMP plugin;

    private final Map<Integer, Grave> graves = new ConcurrentHashMap<>();
    /** Blockposition -> Grab-ID fuer sehr schnelle Treffererkennung. */
    private final Map<String, Integer> blockIndex = new ConcurrentHashMap<>();
    /** Grab-ID -> urspruenglicher Blockzustand als BlockData-String. */
    private final Map<Integer, String> previousBlocks = new ConcurrentHashMap<>();
    /** Graeber, die gerade geoeffnet sind - verhindert doppelten Zugriff. */
    private final Map<Integer, UUID> openGraves = new ConcurrentHashMap<>();

    public GraveManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().bool("graves.enabled", true);
    }

    // ------------------------------------------------------------------
    // Laden / Speichern
    // ------------------------------------------------------------------

    public void load() {
        plugin.getDatabase().asyncQuery(connection -> {
            List<Object[]> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ks_graves WHERE claimed = 0");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{
                            rs.getInt("id"), rs.getString("owner"), rs.getString("owner_name"),
                            rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                            rs.getLong("created"), rs.getLong("expires"), rs.getInt("experience"),
                            rs.getString("contents")
                    });
                }
            }
            return rows;
        }, rows -> {
            if (rows == null) {
                return;
            }
            for (Object[] row : rows) {
                World world = Bukkit.getWorld((String) row[3]);
                if (world == null) {
                    continue;
                }
                Location location = new Location(world, (Integer) row[4], (Integer) row[5], (Integer) row[6]);
                Grave grave = new Grave((Integer) row[0], parseUuid((String) row[1]), (String) row[2],
                        location, (Long) row[7], (Long) row[8], (Integer) row[9],
                        ItemSerializer.fromString((String) row[10]), false);
                if (grave.getOwner() == null) {
                    continue;
                }
                graves.put(grave.getId(), grave);
                blockIndex.put(key(location), grave.getId());
            }
            plugin.getLogger().info(graves.size() + " offene Graeber geladen.");
        });
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return null;
        }
    }

    private String key(Location location) {
        return location.getWorld().getName() + ':' + location.getBlockX() + ':'
                + location.getBlockY() + ':' + location.getBlockZ();
    }

    /** Startet die regelmaessige Bereinigung abgelaufener Graeber. */
    public void start() {
        long minutes = Math.max(1L, plugin.getConfigManager().duration("graves.cleanup-interval-minutes", 5L));
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpired();
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L * minutes);
    }

    private void cleanupExpired() {
        for (Grave grave : new ArrayList<>(graves.values())) {
            if (!grave.isExpired() || openGraves.containsKey(grave.getId())) {
                continue;
            }
            boolean drop = plugin.getConfigManager().bool("graves.drop-on-expire", false);
            if (drop && grave.getLocation().getWorld() != null) {
                for (ItemStack item : ItemSerializer.compact(grave.getContents())) {
                    grave.getLocation().getWorld().dropItemNaturally(grave.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }
            removeGrave(grave, true);
        }
    }

    // ------------------------------------------------------------------
    // Grab erzeugen
    // ------------------------------------------------------------------

    /**
     * Legt ein Grab fuer den gestorbenen Spieler an.
     *
     * @param drops     die Items, die sonst gedroppt wuerden (werden uebernommen)
     * @param experience gespeicherte Erfahrungspunkte
     * @return {@code true}, wenn ein Grab angelegt wurde
     */
    public boolean createGrave(Player player, List<ItemStack> drops, int experience) {
        if (!isEnabled() || drops == null || drops.isEmpty()) {
            return false;
        }
        Location death = player.getLocation();
        Location graveLocation = findGraveBlock(death);
        if (graveLocation == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        long minutes = Math.max(1L, plugin.getConfigManager().duration("graves.lifetime-minutes", 60L));
        long expires = now + minutes * 60_000L;

        ItemStack[] contents = drops.toArray(new ItemStack[0]);
        String serialized = ItemSerializer.toString(contents);
        UUID owner = player.getUniqueId();
        String ownerName = player.getName();
        String worldName = graveLocation.getWorld().getName();
        int x = graveLocation.getBlockX();
        int y = graveLocation.getBlockY();
        int z = graveLocation.getBlockZ();

        plugin.getDatabase().asyncQuery(connection -> {
            String sql = """
                    INSERT INTO ks_graves
                    (owner, owner_name, world, x, y, z, created, expires, experience, contents, claimed)
                    VALUES (?,?,?,?,?,?,?,?,?,?,0)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, owner.toString());
                statement.setString(2, ownerName);
                statement.setString(3, worldName);
                statement.setInt(4, x);
                statement.setInt(5, y);
                statement.setInt(6, z);
                statement.setLong(7, now);
                statement.setLong(8, expires);
                statement.setInt(9, Math.max(0, experience));
                statement.setString(10, serialized);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        }, id -> {
            if (id == null || id <= 0) {
                // Konnte nicht gespeichert werden - Items zurueckgeben statt zu verlieren.
                plugin.getLogger().warning("Grab konnte nicht gespeichert werden, Items werden gedroppt.");
                World world = graveLocation.getWorld();
                if (world != null) {
                    for (ItemStack item : ItemSerializer.compact(contents)) {
                        world.dropItemNaturally(graveLocation, item);
                    }
                }
                return;
            }
            Grave grave = new Grave(id, owner, ownerName, graveLocation, now, expires,
                    Math.max(0, experience), contents, false);
            graves.put(id, grave);
            blockIndex.put(key(graveLocation), id);
            placeBlock(grave);

            Player online = Bukkit.getPlayer(owner);
            if (online != null) {
                plugin.getMessages().send(online, "graves.created",
                        "%id%", String.valueOf(id),
                        "%location%", LocationUtil.pretty(graveLocation),
                        "%time%", TimeUtil.formatDuration(expires - System.currentTimeMillis()));
            }
        });
        return true;
    }

    /** Sucht eine geeignete Blockposition fuer das Grab. */
    private Location findGraveBlock(Location death) {
        World world = death.getWorld();
        if (world == null) {
            return null;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int startY = Math.max(minY, Math.min(maxY, death.getBlockY()));

        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{1, -1}) {
                int y = startY + dy * sign;
                if (y < minY || y > maxY) {
                    continue;
                }
                Block block = world.getBlockAt(death.getBlockX(), y, death.getBlockZ());
                if (isReplaceable(block)) {
                    return block.getLocation();
                }
                if (dy == 0) {
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Ein Block darf ersetzt werden, wenn er Luft, Wasser oder eine
     * nicht feste, durchlaessige Dekoration (Gras, Schnee, Farn) ist.
     * Bewusst ohne feste Material-Namen, damit die Pruefung bei Umbenennungen
     * zwischen Minecraft-Versionen weiterhin funktioniert.
     */
    private boolean isReplaceable(Block block) {
        Material type = block.getType();
        if (type.isAir() || type == Material.WATER) {
            return true;
        }
        return block.isPassable() && !type.isSolid() && type != Material.LAVA;
    }

    private void placeBlock(Grave grave) {
        if (!plugin.getConfigManager().bool("graves.place-block", true)) {
            return;
        }
        Location location = grave.getLocation();
        if (location.getWorld() == null) {
            return;
        }
        Block block = location.getBlock();
        previousBlocks.put(grave.getId(), block.getBlockData().getAsString());
        Material material = Compat.material(
                plugin.getConfigManager().string("graves.block", "CHEST"), Material.CHEST);
        block.setType(material, false);
    }

    private void restoreBlock(Grave grave) {
        if (grave.getLocation().getWorld() == null) {
            return;
        }
        Block block = grave.getLocation().getBlock();
        String previous = previousBlocks.remove(grave.getId());
        if (previous != null) {
            try {
                block.setBlockData(Bukkit.createBlockData(previous), false);
                return;
            } catch (IllegalArgumentException ignored) {
                // ungueltige BlockData - dann eben Luft
            }
        }
        block.setType(Material.AIR, false);
    }

    // ------------------------------------------------------------------
    // Zugriff
    // ------------------------------------------------------------------

    public Grave getGraveAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Integer id = blockIndex.get(key(location));
        return id == null ? null : graves.get(id);
    }

    public Grave getGrave(int id) {
        return graves.get(id);
    }

    public List<Grave> gravesOf(UUID owner) {
        List<Grave> list = new ArrayList<>();
        for (Grave grave : graves.values()) {
            if (grave.getOwner().equals(owner)) {
                list.add(grave);
            }
        }
        list.sort(Comparator.comparingLong(Grave::getCreated).reversed());
        return list;
    }

    public List<Grave> allGraves() {
        List<Grave> list = new ArrayList<>(graves.values());
        list.sort(Comparator.comparingLong(Grave::getCreated).reversed());
        return list;
    }

    /**
     * Oeffnet ein Grab.
     *
     * @return {@code false}, wenn es bereits jemand geoeffnet hat.
     */
    public boolean open(Player player, Grave grave) {
        if (grave == null) {
            return false;
        }
        UUID current = openGraves.get(grave.getId());
        if (current != null && !current.equals(player.getUniqueId())) {
            plugin.getMessages().send(player, "graves.busy");
            return false;
        }
        openGraves.put(grave.getId(), player.getUniqueId());
        new GraveGui(plugin, grave).open(player);
        return true;
    }

    /**
     * Uebernimmt den Inhalt einer geschlossenen Grab-Oberflaeche.
     * Ist das Grab leer, wird es entfernt.
     */
    public void handleClose(Player player, Grave grave, ItemStack[] contents) {
        openGraves.remove(grave.getId());
        grave.setContents(contents);

        int experience = grave.takeExperience();
        if (experience > 0 && player != null) {
            player.giveExp(experience);
        }

        boolean empty = ItemSerializer.compact(contents).isEmpty();
        if (empty) {
            removeGrave(grave, true);
            if (player != null) {
                plugin.getMessages().send(player, "graves.emptied", "%id%", String.valueOf(grave.getId()));
            }
            return;
        }
        persist(grave);
    }

    private void persist(Grave grave) {
        String serialized = ItemSerializer.toString(grave.getContents());
        int id = grave.getId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE ks_graves SET contents = ?, experience = 0 WHERE id = ?")) {
                statement.setString(1, serialized);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
        });
    }

    /** Entfernt ein Grab dauerhaft. */
    public void removeGrave(Grave grave, boolean restoreBlock) {
        if (grave == null) {
            return;
        }
        graves.remove(grave.getId());
        blockIndex.remove(key(grave.getLocation()));
        openGraves.remove(grave.getId());
        if (restoreBlock) {
            restoreBlock(grave);
        } else {
            previousBlocks.remove(grave.getId());
        }
        grave.setClaimed(true);

        int id = grave.getId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("UPDATE ks_graves SET claimed = 1, contents = '' WHERE id = ?")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        });
    }

    public boolean isOpen(int graveId) {
        return openGraves.containsKey(graveId);
    }

    public int count() {
        return graves.size();
    }
}
