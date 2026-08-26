package de.klassensmp.crate;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Crate;
import de.klassensmp.model.CrateReward;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crate-System.
 *
 * <p>Crates werden ueber {@code crates.yml} definiert. Zum Oeffnen wird ein
 * Schluessel benoetigt - entweder ein Item (vom Team vergeben) oder eine
 * Permission. Es gibt bewusst keinerlei Echtgeld-Anbindung.</p>
 */
public final class CrateManager {

    private static final String FILE = "crates.yml";
    /** Unsichtbare Markierung im Lore, an der ein Schluessel erkannt wird. */
    private static final String KEY_MARKER = "&8&kks";

    private final KlassenSMP plugin;
    private final Map<String, Crate> crates = new ConcurrentHashMap<>();

    public CrateManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        crates.clear();
        YamlConfiguration config = plugin.getConfigManager().loadFile(FILE);
        ConfigurationSection section = config.getConfigurationSection("crates");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            List<CrateReward> rewards = new ArrayList<>();
            ConfigurationSection rewardSection = entry.getConfigurationSection("rewards");
            if (rewardSection != null) {
                for (String rewardKey : rewardSection.getKeys(false)) {
                    ConfigurationSection reward = rewardSection.getConfigurationSection(rewardKey);
                    if (reward == null) {
                        continue;
                    }
                    ItemStack item = null;
                    Object rawItem = reward.get("item");
                    if (rawItem instanceof ItemStack stack) {
                        item = stack.clone();
                    } else if (rawItem instanceof String text) {
                        item = parseItem(text);
                    }
                    rewards.add(new CrateReward(
                            reward.getString("display", rewardKey),
                            item,
                            Math.max(0.0D, reward.getDouble("money", 0.0D)),
                            Math.max(0, reward.getInt("experience", 0)),
                            reward.getStringList("commands"),
                            Math.max(0.0D, reward.getDouble("chance", 1.0D)),
                            reward.getBoolean("broadcast", false)));
                }
            }
            if (rewards.isEmpty()) {
                plugin.getLogger().warning("Crate '" + key + "' hat keine Belohnungen und wird uebersprungen.");
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            crates.put(id, new Crate(id,
                    entry.getString("display", key),
                    entry.getString("icon", "ENDER_CHEST"),
                    entry.getString("key-permission", ""),
                    rewards));
        }
        plugin.getLogger().info(crates.size() + " Crates geladen.");
    }

    private ItemStack parseItem(String text) {
        String[] parts = text.split(":");
        Material material = Compat.material(parts[0], null);
        if (material == null) {
            return null;
        }
        int amount = parts.length > 1 ? NumberUtil.parseInt(parts[1], 1) : 1;
        return new ItemBuilder(material, amount).build();
    }

    public Crate get(String id) {
        return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Crate> all() {
        List<Crate> list = new ArrayList<>(crates.values());
        list.sort(Comparator.comparing(Crate::id));
        return list;
    }

    public List<String> nameList() {
        return new ArrayList<>(crates.keySet());
    }

    // ------------------------------------------------------------------
    // Schluessel
    // ------------------------------------------------------------------

    /** Erzeugt ein Schluessel-Item fuer eine Crate. */
    public ItemStack createKey(Crate crate, int amount) {
        Material material = Compat.material(
                plugin.getConfigManager().string("crates.key-material", "TRIPWIRE_HOOK"), Material.TRIPWIRE_HOOK);
        List<String> lore = new ArrayList<>();
        lore.add(plugin.getMessages().plain("crates.key-lore", "%crate%", crate.displayName()));
        lore.add(KEY_MARKER + crate.id());
        return new ItemBuilder(material, amount)
                .name(plugin.getMessages().plain("crates.key-name", "%crate%", crate.displayName()))
                .lore(lore)
                .glow()
                .build();
    }

    /** Prueft, ob ein Item der Schluessel zu dieser Crate ist. */
    public boolean isKey(ItemStack item, Crate crate) {
        if (item == null || crate == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return false;
        }
        String marker = Text.color(KEY_MARKER + crate.id());
        return meta.getLore().contains(marker);
    }

    public int countKeys(Player player, Crate crate) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isKey(item, crate)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Verbraucht genau einen Schluessel.
     *
     * @return {@code true}, wenn ein Schluessel entfernt wurde
     */
    public boolean consumeKey(Player player, Crate crate) {
        if (!crate.keyPermission().isBlank() && player.hasPermission(crate.keyPermission())) {
            return true; // Permission ersetzt den Schluessel
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isKey(item, crate)) {
                continue;
            }
            if (item.getAmount() <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(slot, item);
            }
            player.updateInventory();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Belohnungen
    // ------------------------------------------------------------------

    /** Waehlt eine Belohnung anhand der konfigurierten Gewichte. */
    public CrateReward randomReward(Crate crate) {
        double total = crate.totalWeight();
        if (total <= 0) {
            return crate.rewards().get(ThreadLocalRandom.current().nextInt(crate.rewards().size()));
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        double current = 0;
        for (CrateReward reward : crate.rewards()) {
            current += Math.max(0.0D, reward.chance());
            if (roll < current) {
                return reward;
            }
        }
        return crate.rewards().get(crate.rewards().size() - 1);
    }

    /** Uebergibt eine Belohnung an den Spieler. */
    public void giveReward(Player player, Crate crate, CrateReward reward) {
        if (reward.item() != null) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward.item().clone());
            for (ItemStack rest : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        if (reward.money() > 0) {
            plugin.getEconomyManager().deposit(player.getUniqueId(), reward.money());
        }
        if (reward.experience() > 0) {
            player.giveExp(reward.experience());
        }
        for (String command : reward.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        }

        plugin.getMessages().send(player, "crates.reward",
                "%crate%", crate.displayName(), "%reward%", reward.displayName());

        if (reward.broadcast()) {
            Bukkit.broadcastMessage(plugin.getMessages().get("crates.broadcast",
                    "%player%", player.getName(),
                    "%crate%", crate.displayName(),
                    "%reward%", reward.displayName()));
        }
    }

    /** Vorschau-Item einer Belohnung fuer die GUI. */
    public ItemStack previewItem(CrateReward reward) {
        ItemStack base = reward.item() != null
                ? reward.item().clone()
                : new ItemStack(reward.money() > 0 ? Material.GOLD_INGOT : Material.EXPERIENCE_BOTTLE);
        ItemBuilder builder = new ItemBuilder(base).name(reward.displayName());
        if (reward.money() > 0) {
            builder.addLore(plugin.getMessages().plain("crates.preview-money",
                    "%money%", plugin.getEconomyManager().format(reward.money())));
        }
        if (reward.experience() > 0) {
            builder.addLore(plugin.getMessages().plain("crates.preview-xp",
                    "%amount%", String.valueOf(reward.experience())));
        }
        return builder.build();
    }
}
