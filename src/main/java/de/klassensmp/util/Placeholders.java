package de.klassensmp.util;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.model.Rank;
import de.klassensmp.performance.PerformanceSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Loest die Platzhalter des Plugins in Tablist, Scoreboard und Chat auf.
 *
 * <p>Die Werte stammen aus bereits vorhandenen Caches (Spielerdaten,
 * Performance-Momentaufnahme). Es findet hier kein Weltscan und kein
 * Datenbankzugriff statt, damit der Aufruf auch mehrfach pro Sekunde
 * guenstig bleibt.</p>
 */
public final class Placeholders {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    private final KlassenSMP plugin;

    public Placeholders(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Zaehlt Java- und Bedrock-Spieler in einem Durchlauf. */
    public record PlatformCounts(int java, int bedrock) {

        public int total() {
            return java + bedrock;
        }
    }

    public PlatformCounts countPlatforms() {
        int java = 0;
        int bedrock = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getVanishManager().isVanished(player)) {
                continue;
            }
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (data != null && data.isBedrock()) {
                bedrock++;
            } else {
                java++;
            }
        }
        return new PlatformCounts(java, bedrock);
    }

    /**
     * Ersetzt alle bekannten Platzhalter in einem Text.
     *
     * @param counts vorher berechnete Plattformzahlen (spart Mehrfachzaehlung)
     */
    public String apply(Player player, String text, PlatformCounts counts, PerformanceSnapshot snapshot) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.indexOf('%') < 0) {
            return text;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        Rank rank = plugin.getRankManager().getRank(player);
        int ping = Compat.ping(player);
        LocalDateTime now = LocalDateTime.now();

        String result = Text.replace(text,
                "%player%", player.getName(),
                "%displayname%", player.getDisplayName(),
                "%rank%", rank.displayName(),
                "%prefix%", rank.prefix(),
                "%suffix%", rank.suffix(),
                "%namecolor%", rank.nameColor(),
                "%world%", player.getWorld().getName(),
                "%online%", String.valueOf(counts.total()),
                "%max%", String.valueOf(Bukkit.getMaxPlayers()),
                "%java%", String.valueOf(counts.java()),
                "%bedrock%", String.valueOf(counts.bedrock()),
                "%ping%", ping < 0 ? "-" : String.valueOf(ping),
                "%tps%", NumberUtil.formatTps(snapshot.measuredTps()),
                "%status%", snapshot.status().getDisplay(),
                "%statusicon%", snapshot.status().getIcon(),
                "%entities%", String.valueOf(snapshot.entities()),
                "%chunks%", String.valueOf(snapshot.chunks()),
                "%time%", TIME.format(now),
                "%date%", DATE.format(now),
                "%server%", plugin.getConfigManager().string("server-name", "KlassenSMP"),
                "%website%", plugin.getConfigManager().string("website", "klassensmp.de"));

        if (data != null) {
            result = Text.replace(result,
                    "%money%", plugin.getEconomyManager().format(data.getMoney()),
                    "%balance%", NumberUtil.formatMoney(data.getMoney()),
                    "%bank%", plugin.getEconomyManager().format(data.getBank()),
                    "%earned%", plugin.getEconomyManager().format(data.getEarned()),
                    "%spent%", plugin.getEconomyManager().format(data.getSpent()),
                    "%playtime%", TimeUtil.formatPlaytime(data.getTotalPlaytime()),
                    "%kills%", String.valueOf(data.getKills()),
                    "%deaths%", String.valueOf(data.getDeaths()),
                    "%mobkills%", String.valueOf(data.getMobKills()),
                    "%blocksbroken%", NumberUtil.formatNumber(data.getBlocksBroken()),
                    "%blocksplaced%", NumberUtil.formatNumber(data.getBlocksPlaced()),
                    "%platform%", data.isBedrock() ? bedrockIcon() : javaIcon(),
                    "%pvp%", data.isPvpEnabled()
                            ? plugin.getMessages().plain("common.on")
                            : plugin.getMessages().plain("common.off"));
        }

        result = Text.replace(result,
                "%claims%", String.valueOf(plugin.getClaimManager().claimsOf(player.getUniqueId()).size()),
                "%homes%", String.valueOf(plugin.getHomeManager().count(player.getUniqueId())),
                "%achievements%", String.valueOf(plugin.getAchievementManager().unlockedCount(player.getUniqueId())),
                "%achievements_total%", String.valueOf(plugin.getAchievementManager().total()));

        // Externe Platzhalter zuletzt, damit sie unsere nicht ueberschreiben.
        return plugin.getHooks().applyPlaceholders(player, result);
    }

    public String javaIcon() {
        return plugin.getConfigManager().string("tablist.java-icon", "&f☕ Java");
    }

    public String bedrockIcon() {
        return plugin.getConfigManager().string("tablist.bedrock-icon", "&b❖ Bedrock");
    }

    /** Kurzes Symbol fuer die Tablist-Zeile eines Spielers. */
    public String platformTag(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean bedrock = data != null && data.isBedrock();
        return plugin.getConfigManager().string(
                bedrock ? "tablist.bedrock-tag" : "tablist.java-tag",
                bedrock ? "&b❖ " : "&f☕ ");
    }
}
