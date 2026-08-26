package de.klassensmp.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Kleiner Builder fuer GUI- und Kit-Items. */
public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material == null ? Material.STONE : material,
                NumberUtil.clamp(amount, 1, 64));
    }

    public ItemBuilder(ItemStack source) {
        this.item = source == null ? new ItemStack(Material.STONE) : source.clone();
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Text.color(lines));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder addLore(String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add(Text.color(line));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(NumberUtil.clamp(amount, 1, 64));
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (enchantment != null && level > 0) {
            item.addUnsafeEnchantment(enchantment, level);
        }
        return this;
    }

    /** Optischer Glanz ohne echte Verzauberungswirkung. */
    public ItemBuilder glow() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        Enchantment lure = Compat.enchantment("lure");
        if (lure != null) {
            item.addUnsafeEnchantment(lure, 1);
        }
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(flags);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        return flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
    }

    public ItemBuilder skullOwner(OfflinePlayer owner) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta && owner != null) {
            skullMeta.setOwningPlayer(owner);
            item.setItemMeta(skullMeta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
