package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Rank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verwaltet die Raenge des Servers.
 *
 * <p>Ränge sind reine Darstellung: welcher Rang gilt, entscheidet
 * ausschliesslich die zugehoerige Permission. Es gibt keine fest codierten
 * Rechte - jeder Rang ist in der {@code config.yml} frei konfigurierbar.</p>
 */
public final class RankManager {

    private final KlassenSMP plugin;

    /** Nach Gewicht absteigend sortiert - der erste Treffer gewinnt. */
    private final List<Rank> ranks = new ArrayList<>();
    private final Map<String, Rank> byId = new LinkedHashMap<>();

    private Rank defaultRank;

    public RankManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ranks.clear();
        byId.clear();

        ConfigurationSection section = plugin.getConfigManager().get().getConfigurationSection("ranks.list");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                Rank rank = new Rank(
                        id.toLowerCase(Locale.ROOT),
                        entry.getString("display", id),
                        entry.getString("prefix", ""),
                        entry.getString("suffix", ""),
                        entry.getString("permission", ""),
                        entry.getInt("weight", 0),
                        entry.getString("name-color", "&7"),
                        entry.getInt("homes", 1));
                ranks.add(rank);
                byId.put(rank.id(), rank);
            }
        }
        ranks.sort(Comparator.comparingInt(Rank::weight).reversed());

        String defaultId = plugin.getConfigManager().string("ranks.default", "spieler").toLowerCase(Locale.ROOT);
        this.defaultRank = byId.get(defaultId);
        if (defaultRank == null) {
            // Notfall-Rang, damit das Plugin auch bei kaputter Config funktioniert.
            this.defaultRank = new Rank("spieler", "&7Spieler", "&7[Spieler] ", "", "", 0, "&7", 1);
            byId.put(defaultRank.id(), defaultRank);
            if (!ranks.contains(defaultRank)) {
                ranks.add(defaultRank);
            }
        }
        plugin.getLogger().info(ranks.size() + " Raenge geladen.");
    }

    /**
     * Ermittelt den hoechsten Rang, dessen Permission der Spieler besitzt.
     * Raenge ohne Permission gelten nur als Standardrang.
     */
    public Rank getRank(Permissible permissible) {
        if (permissible == null) {
            return defaultRank;
        }
        for (Rank rank : ranks) {
            String permission = rank.permission();
            if (permission != null && !permission.isBlank() && permissible.hasPermission(permission)) {
                return rank;
            }
        }
        return defaultRank;
    }

    public Rank getById(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Rank getDefaultRank() {
        return defaultRank;
    }

    public List<Rank> getRanks() {
        return List.copyOf(ranks);
    }

    /**
     * Maximale Anzahl Homes fuer einen Spieler.
     *
     * <p>Es gewinnt der hoechste Wert aus allen Raengen, deren Permission der
     * Spieler besitzt. {@code -1} bedeutet unbegrenzt.</p>
     */
    public int getMaxHomes(Player player) {
        if (player == null) {
            return 0;
        }
        if (player.hasPermission("klassensmp.home.unlimited")) {
            return -1;
        }
        int max = defaultRank.homes();
        for (Rank rank : ranks) {
            String permission = rank.permission();
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
                if (rank.homes() < 0) {
                    return -1;
                }
                max = Math.max(max, rank.homes());
            }
        }
        return Math.max(0, max);
    }

    /** Prefix inklusive Farbcodes fuer Chat und Tablist. */
    public String getPrefix(Player player) {
        return getRank(player).prefix();
    }

    public String getSuffix(Player player) {
        return getRank(player).suffix();
    }
}
