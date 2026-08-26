package de.klassensmp.tab;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.Rank;
import de.klassensmp.performance.PerformanceSnapshot;
import de.klassensmp.util.Placeholders;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tablist des Klassen-SMP.
 *
 * <p>Kopf- und Fusszeile werden aus der Config gebaut und in einem festen
 * Intervall aktualisiert - bewusst nicht jeden Tick. Die Sortierung nach Rang
 * erfolgt ueber Scoreboard-Teams; jeder Spielername wird zusaetzlich mit einem
 * Java- bzw. Bedrock-Symbol versehen.</p>
 *
 * <p>Es kommen ausschliesslich Spigot/Bukkit-Methoden zum Einsatz
 * ({@code Player#setPlayerListHeaderFooter}, {@code Player#setPlayerListName},
 * Scoreboard-Teams) - keine Paketmanipulation.</p>
 */
public final class TabManager {

    private static final String TEAM_PREFIX = "ks";

    private final KlassenSMP plugin;

    private boolean enabled = true;
    private List<String> headerTemplate = List.of();
    private List<String> footerTemplate = List.of();
    private String listNameFormat = "%platform%%prefix%%namecolor%%player%";
    private long intervalTicks = 40L;

    private volatile boolean updateRequested;

    public TabManager(KlassenSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager();
        this.enabled = config.bool("tablist.enabled", true);
        this.headerTemplate = new ArrayList<>(config.get().getStringList("tablist.header"));
        this.footerTemplate = new ArrayList<>(config.get().getStringList("tablist.footer"));
        this.listNameFormat = config.string("tablist.name-format", listNameFormat);
        this.intervalTicks = Math.max(20L, config.duration("tablist.update-ticks", 40L));
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateRequested = false;
                updateAll();
            }
        }.runTaskTimer(plugin, 40L, intervalTicks);

        // Ausserplanmaessige Aktualisierungen (z.B. nach Vanish) werden gebuendelt.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (updateRequested) {
                    updateRequested = false;
                    updateAll();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Fordert eine Aktualisierung an, die spaetestens im naechsten Sekundentakt erfolgt. */
    public void requestUpdate() {
        this.updateRequested = true;
    }

    public void handleJoin(Player player) {
        requestUpdate();
    }

    public void handleQuit(Player player) {
        String teamName = teamName(player);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player)) {
                continue;
            }
            Scoreboard board = plugin.getBoardManager().getBoard(viewer);
            if (board == null) {
                continue;
            }
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
    }

    private void updateAll() {
        if (!enabled) {
            return;
        }
        Placeholders placeholders = plugin.getBoardManager().getPlaceholders();
        Placeholders.PlatformCounts counts = placeholders.countPlatforms();
        PerformanceSnapshot snapshot = plugin.getPerformanceManager().cached();

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Anzeigedaten einmal je Spieler berechnen ...
        Map<Player, Entry> entries = new HashMap<>(online.size());
        for (Player target : online) {
            Rank rank = plugin.getRankManager().getRank(target);
            String prefix = Text.color(placeholders.platformTag(target) + rank.prefix());
            String suffix = Text.color(rank.suffix());
            entries.put(target, new Entry(teamName(target), prefix, suffix));

            String listName = Text.color(placeholders.apply(target, listNameFormat, counts, snapshot));
            // Die Team-Prefixe liefern bereits Rang und Plattform; hier nur der Name.
            if (!listName.equals(target.getPlayerListName())) {
                target.setPlayerListName(listName);
            }
        }

        // ... und danach auf jedes Scoreboard anwenden.
        for (Player viewer : online) {
            Scoreboard board = plugin.getBoardManager().getBoard(viewer);
            if (board == null) {
                continue;
            }
            applyTeams(board, entries);
            applyHeaderFooter(viewer, placeholders, counts, snapshot);
        }
    }

    /** Anzeigeinformationen eines Spielers in der Tablist. */
    private record Entry(String teamName, String prefix, String suffix) {
    }

    private void applyTeams(Scoreboard board, Map<Player, Entry> entries) {
        Set<String> wanted = new HashSet<>();
        for (Map.Entry<Player, Entry> mapEntry : entries.entrySet()) {
            Player target = mapEntry.getKey();
            Entry entry = mapEntry.getValue();
            wanted.add(entry.teamName());

            Team team = board.getTeam(entry.teamName());
            if (team == null) {
                team = board.registerNewTeam(entry.teamName());
            }
            if (!team.hasEntry(target.getName())) {
                // Der Spieler darf nur in genau einem Team stehen.
                Team current = board.getEntryTeam(target.getName());
                if (current != null && !current.equals(team)) {
                    current.removeEntry(target.getName());
                }
                team.addEntry(target.getName());
            }
            if (!entry.prefix().equals(team.getPrefix())) {
                team.setPrefix(entry.prefix());
            }
            if (!entry.suffix().equals(team.getSuffix())) {
                team.setSuffix(entry.suffix());
            }
        }

        // Teams von Spielern entfernen, die nicht mehr online sind.
        for (Team team : new ArrayList<>(board.getTeams())) {
            String name = team.getName();
            if (name.startsWith(TEAM_PREFIX) && !name.startsWith("ks_line") && !wanted.contains(name)) {
                team.unregister();
            }
        }
    }

    private void applyHeaderFooter(Player viewer, Placeholders placeholders,
                                   Placeholders.PlatformCounts counts, PerformanceSnapshot snapshot) {
        String header = render(viewer, headerTemplate, placeholders, counts, snapshot);
        String footer = render(viewer, footerTemplate, placeholders, counts, snapshot);
        viewer.setPlayerListHeaderFooter(header, footer);
    }

    private String render(Player viewer, List<String> template, Placeholders placeholders,
                          Placeholders.PlatformCounts counts, PerformanceSnapshot snapshot) {
        if (template.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < template.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(Text.color(placeholders.apply(viewer, template.get(i), counts, snapshot)));
        }
        return builder.toString();
    }

    /**
     * Teamname fuer die Sortierung: dreistelliges Ranggewicht + Spielername.
     * Scoreboard-Teamnamen sind auf 16 Zeichen begrenzt, daher wird der Name
     * gekuerzt - fuer ein Klassen-SMP ist das eindeutig genug.
     */
    private String teamName(Player player) {
        Rank rank = plugin.getRankManager().getRank(player);
        String name = player.getName().toLowerCase(Locale.ROOT);
        String shortName = name.length() > 11 ? name.substring(0, 11) : name;
        return TEAM_PREFIX + rank.sortKey() + shortName;
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setPlayerListHeaderFooter("", "");
            player.setPlayerListName(player.getName());
        }
    }
}
