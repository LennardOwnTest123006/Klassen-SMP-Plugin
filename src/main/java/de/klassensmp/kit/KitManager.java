package de.klassensmp.kit;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Kit;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kit-System.
 *
 * <p>Kits werden in {@code kits.yml} beschrieben. Items koennen entweder in
 * Kurzschreibweise ({@code DIAMOND_SWORD:1:sharpness=5}) oder als vollstaendig
 * serialisierte Items (durch {@code /kit create}) hinterlegt werden.</p>
 */
public final class KitManager {

    private static final String FILE = "kits.yml";

    private final KlassenSMP plugin;
    private final Map<String, Kit> kits = new ConcurrentHashMap<>();
    /** UUID -> (Kit -> Zeitpunkt der letzten Nutzung). */
    private final Map<UUID, Map<String, Long>> uses = new ConcurrentHashMap<>();

    public KitManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Laden / Speichern
    // ------------------------------------------------------------------

    public void load() {
        kits.clear();
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        ConfigurationSection section = config.getConfigurationSection("kits");
        if (section == null) {
            plugin.getLogger().info("Keine Kits konfiguriert.");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT);
            List<ItemStack> items = readItems(entry.getList("items"), name);
            Kit kit = new Kit(name,
                    entry.getString("display", key),
                    entry.getString("icon", "CHEST"),
                    entry.getString("permission", ""),
                    Math.max(0L, entry.getLong("cooldown", 0L)),
                    entry.getBoolean("one-time", false),
                    Math.max(0.0D, entry.getDouble("price", 0.0D)),
                    entry.getStringList("description"),
                    items,
                    entry.getStringList("commands"));
            kits.put(name, kit);
        }
        plugin.getLogger().info(kits.size() + " Kits geladen.");
    }

    /**
     * Liest die Item-Liste eines Kits.
     * Unterstuetzt serialisierte Items und die Kurzschreibweise.
     */
    private List<ItemStack> readItems(List<?> raw, String kitName) {
        List<ItemStack> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        for (Object entry : raw) {
            if (entry instanceof ItemStack item) {
                items.add(item.clone());
            } else if (entry instanceof String text) {
                ItemStack parsed = parseShorthand(text);
                if (parsed == null) {
                    plugin.getLogger().warning("Kit '" + kitName + "': ungueltiger Eintrag '" + text + "'");
                } else {
                    items.add(parsed);
                }
            } else if (entry instanceof Map<?, ?> map) {
                ItemStack deserialized = ItemStack.deserialize(castMap(map));
                items.add(deserialized);
            }
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    /** Format: {@code MATERIAL[:ANZAHL[:enchant=stufe,enchant=stufe[:Name]]]}. */
    private ItemStack parseShorthand(String text) {
        String[] parts = text.split(":");
        Material material = Compat.material(parts[0], null);
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = parts.length > 1 ? NumberUtil.parseInt(parts[1], 1) : 1;
        ItemBuilder builder = new ItemBuilder(material, amount);

        if (parts.length > 2 && !parts[2].isBlank()) {
            for (String enchantEntry : parts[2].split(",")) {
                String[] pair = enchantEntry.split("=");
                Enchantment enchantment = Compat.enchantment(pair[0]);
                int level = pair.length > 1 ? NumberUtil.parseInt(pair[1], 1) : 1;
                if (enchantment != null) {
                    builder.enchant(enchantment, level);
                } else {
                    plugin.getLogger().warning("Unbekannte Verzauberung: " + pair[0]);
                }
            }
        }
        if (parts.length > 3 && !parts[3].isBlank()) {
            builder.name(parts[3].replace('_', ' '));
        }
        return builder.build();
    }

    /** Speichert das aktuelle Inventar eines Spielers als Kit. */
    public boolean createFromInventory(Player player, String rawName, long cooldownSeconds) {
        String name = Text.sanitizeName(rawName);
        if (name.isEmpty() || name.length() > 24) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        String path = "kits." + name;
        config.set(path + ".display", "&a" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1));
        config.set(path + ".icon", items.isEmpty() ? "CHEST" : items.get(0).getType().name());
        if (config.getString(path + ".permission") == null) {
            config.set(path + ".permission", "klassensmp.kit." + name);
        }
        config.set(path + ".cooldown", Math.max(0L, cooldownSeconds));
        config.set(path + ".one-time", config.getBoolean(path + ".one-time", false));
        config.set(path + ".price", config.getDouble(path + ".price", 0.0D));
        config.set(path + ".items", items);
        plugin.getConfigManager().saveFile(config, FILE);
        load();
        return true;
    }

    public boolean delete(String rawName) {
        String name = Text.sanitizeName(rawName);
        if (!kits.containsKey(name)) {
            return false;
        }
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        config.set("kits." + name, null);
        plugin.getConfigManager().saveFile(config, FILE);
        kits.remove(name);
        return true;
    }

    // ------------------------------------------------------------------
    // Zugriff
    // ------------------------------------------------------------------

    public Kit get(String name) {
        return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
    }

    public List<Kit> all() {
        List<Kit> list = new ArrayList<>(kits.values());
        list.sort(Comparator.comparing(Kit::name));
        return list;
    }

    public List<Kit> availableFor(Player player) {
        List<Kit> list = new ArrayList<>();
        for (Kit kit : all()) {
            if (!kit.hasPermission() || player.hasPermission(kit.permission())) {
                list.add(kit);
            }
        }
        return list;
    }

    public List<String> nameList() {
        return new ArrayList<>(kits.keySet());
    }

    // ------------------------------------------------------------------
    // Cooldowns
    // ------------------------------------------------------------------

    public void loadUses(UUID uuid) {
        plugin.getDatabase().asyncQuery(connection -> {
            Map<String, Long> map = new HashMap<>();
            try (PreparedStatement statement =
                         connection.prepareStatement("SELECT kit, last_used FROM ks_kit_uses WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        map.put(rs.getString("kit"), rs.getLong("last_used"));
                    }
                }
            }
            return map;
        }, map -> uses.put(uuid, map == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(map)));
    }

    public void unload(UUID uuid) {
        uses.remove(uuid);
    }

    /** @return verbleibende Millisekunden oder 0, wenn das Kit verfuegbar ist. */
    public long remainingCooldown(UUID uuid, Kit kit) {
        Map<String, Long> map = uses.get(uuid);
        if (map == null) {
            return 0L;
        }
        Long last = map.get(kit.name());
        if (last == null) {
            return 0L;
        }
        if (kit.oneTime()) {
            return Long.MAX_VALUE;
        }
        if (kit.cooldownSeconds() <= 0) {
            return 0L;
        }
        long ready = last + kit.cooldownSeconds() * 1000L;
        return Math.max(0L, ready - System.currentTimeMillis());
    }

    /** Ergebnis eines Kit-Versuchs. */
    public enum GiveResult {
        SUCCESS,
        NO_PERMISSION,
        COOLDOWN,
        ALREADY_USED,
        NOT_ENOUGH_MONEY,
        INVENTORY_FULL,
        UNKNOWN
    }

    /**
     * Gibt einem Spieler ein Kit.
     *
     * <p>Es wird zuerst geprueft, ob genug Platz vorhanden ist. Erst danach
     * werden Geld abgebucht und der Cooldown gesetzt - so kann ein Kit nicht
     * durch ein volles Inventar verloren gehen.</p>
     */
    public GiveResult give(Player player, Kit kit) {
        if (kit == null) {
            return GiveResult.UNKNOWN;
        }
        if (kit.hasPermission() && !player.hasPermission(kit.permission())) {
            return GiveResult.NO_PERMISSION;
        }

        long remaining = remainingCooldown(player.getUniqueId(), kit);
        if (remaining == Long.MAX_VALUE) {
            return GiveResult.ALREADY_USED;
        }
        if (remaining > 0 && !player.hasPermission("klassensmp.kit.nocooldown")) {
            return GiveResult.COOLDOWN;
        }

        int freeSlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                freeSlots++;
            }
        }
        if (freeSlots < kit.items().size()) {
            return GiveResult.INVENTORY_FULL;
        }

        if (kit.price() > 0 && !plugin.getEconomyManager().withdraw(player.getUniqueId(), kit.price())) {
            return GiveResult.NOT_ENOUGH_MONEY;
        }

        for (ItemStack item : kit.items()) {
            player.getInventory().addItem(item.clone());
        }
        for (String command : kit.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName()));
        }

        long now = System.currentTimeMillis();
        uses.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(kit.name(), now);
        UUID uuid = player.getUniqueId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "REPLACE INTO ks_kit_uses (uuid, kit, last_used) VALUES (?,?,?)")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, kit.name());
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        });
        return GiveResult.SUCCESS;
    }
}
