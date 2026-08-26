package de.klassensmp.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialisiert Items ueber die Bukkit-Konfigurationsserialisierung.
 *
 * <p>Bewusst YAML statt {@code BukkitObjectOutputStream}: Das Format ist
 * versionsstabil, menschenlesbar und bricht nicht, wenn sich interne
 * Serialisierungsdetails aendern.</p>
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    public static String toString(ItemStack[] items) {
        YamlConfiguration config = new YamlConfiguration();
        int written = 0;
        if (items != null) {
            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                if (item != null && !item.getType().isAir()) {
                    config.set("i." + i, item);
                    written++;
                }
            }
        }
        config.set("size", items == null ? 0 : items.length);
        config.set("count", written);
        return config.saveToString();
    }

    /**
     * Liest ein serialisiertes Item-Array zurueck.
     *
     * @return Array in Originalgroesse; nicht belegte Slots sind {@code null}.
     */
    public static ItemStack[] fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ItemStack[0];
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(raw);
        } catch (InvalidConfigurationException ex) {
            return new ItemStack[0];
        }
        int size = Math.max(0, config.getInt("size", 0));
        ItemStack[] items = new ItemStack[size];
        if (config.getConfigurationSection("i") == null) {
            return items;
        }
        for (String key : config.getConfigurationSection("i").getKeys(false)) {
            int index = NumberUtil.parseInt(key, -1);
            if (index < 0 || index >= size) {
                continue;
            }
            Object value = config.get("i." + key);
            if (value instanceof ItemStack item) {
                items[index] = item;
            }
        }
        return items;
    }

    /** Entfernt {@code null}- und Luft-Eintraege. */
    public static List<ItemStack> compact(ItemStack[] items) {
        List<ItemStack> list = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    list.add(item);
                }
            }
        }
        return list;
    }
}
