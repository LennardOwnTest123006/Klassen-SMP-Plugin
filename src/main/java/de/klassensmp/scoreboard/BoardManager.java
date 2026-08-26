package de.klassensmp.scoreboard;

import de.klassensmp.KlassenSMP;
import de.klassensmp.performance.PerformanceSnapshot;
import de.klassensmp.util.Placeholders;
import de.klassensmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seitenleisten-Scoreboard.
 *
 * <p>Jeder Spieler erhaelt ein eigenes Scoreboard. Zeilen werden ueber Teams
 * mit unsichtbaren Eintraegen realisiert und nur dann aktualisiert, wenn sich
 * der Text tatsaechlich geaendert hat - dadurch flackert nichts und es wird
 * kein Scoreboard pro Tick neu aufgebaut.</p>
 *
 * <p>Dasselbe Scoreboard traegt auch die Teams fuer die Tablist-Sortierung
 * (siehe {@link de.klassensmp.tab.TabManager}), weil ein Spieler immer nur
 * ein Scoreboard gleichzeitig sehen kann.</p>
 */
public final class BoardManager {

    private static final String OBJECTIVE_NAME = "ks_sidebar";
    private static final int MAX_LINES = 15;

    private final KlassenSMP plugin;
    private final Placeholders placeholders;

    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    /** Zuletzt gesetzte Zeilen je Spieler - Basis fuer den Aenderungsvergleich. */
    private final Map<UUID, List<String>> lastLines = new ConcurrentHashMap<>();
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    private boolean enabled = true;
    private String title = "&a&lKLASSEN SMP";
    private List<String> template = List.of();
    private long intervalTicks = 40L;

    public BoardManager(KlassenSMP plugin) {
        this.plugin = plugin;
        this.placeholders = new Placeholders(plugin);
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager();
        this.enabled = config.bool("scoreboard.enabled", true);
        this.title = config.string("scoreboard.title", title);
        this.template = new ArrayList<>(config.get().getStringList("scoreboard.lines"));
        this.intervalTicks = Math.max(10L, config.duration("scoreboard.update-ticks", 40L));
        lastLines.clear();
    }

    public Placeholders getPlaceholders() {
        return placeholders;
    }

    /** Das Scoreboard eines Spielers; wird bei Bedarf erzeugt. */
    public Scoreboard getBoard(Player player) {
        return boards.computeIfAbsent(player.getUniqueId(), id -> {
            var manager = Bukkit.getScoreboardManager();
            return manager == null ? null : manager.getNewScoreboard();
        });
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                PerformanceSnapshot snapshot = plugin.getPerformanceManager().cached();
                Placeholders.PlatformCounts counts = placeholders.countPlatforms();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    update(player, counts, snapshot);
                }
            }
        }.runTaskTimer(plugin, 40L, intervalTicks);
    }

    public void handleJoin(Player player) {
        Scoreboard board = getBoard(player);
        if (board != null) {
            player.setScoreboard(board);
        }
        if (enabled && !disabled.contains(player.getUniqueId())) {
            update(player, placeholders.countPlatforms(), plugin.getPerformanceManager().cached());
        }
    }

    public void handleQuit(Player player) {
        boards.remove(player.getUniqueId());
        lastLines.remove(player.getUniqueId());
        disabled.remove(player.getUniqueId());
    }

    /** @return {@code true}, wenn das Scoreboard jetzt sichtbar ist. */
    public boolean toggle(Player player) {
        if (disabled.remove(player.getUniqueId())) {
            update(player, placeholders.countPlatforms(), plugin.getPerformanceManager().cached());
            return true;
        }
        disabled.add(player.getUniqueId());
        clear(player);
        return false;
    }

    public boolean isVisible(Player player) {
        return enabled && !disabled.contains(player.getUniqueId());
    }

    private void clear(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        lastLines.remove(player.getUniqueId());
    }

    private void update(Player player, Placeholders.PlatformCounts counts, PerformanceSnapshot snapshot) {
        if (!enabled || disabled.contains(player.getUniqueId())) {
            return;
        }
        Scoreboard board = getBoard(player);
        if (board == null) {
            return;
        }
        if (!player.getScoreboard().equals(board)) {
            player.setScoreboard(board);
        }

        Objective objective = board.getObjective(OBJECTIVE_NAME);
        String renderedTitle = Text.color(placeholders.apply(player, title, counts, snapshot));
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, renderedTitle);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (!renderedTitle.equals(objective.getDisplayName())) {
            objective.setDisplayName(renderedTitle);
        }

        List<String> lines = new ArrayList<>(Math.min(MAX_LINES, template.size()));
        for (String raw : template) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            lines.add(Text.color(placeholders.apply(player, raw, counts, snapshot)));
        }

        List<String> previous = lastLines.get(player.getUniqueId());
        if (lines.equals(previous)) {
            return; // nichts hat sich geaendert
        }

        int size = lines.size();
        for (int i = 0; i < MAX_LINES; i++) {
            String entry = lineEntry(i);
            Team team = board.getTeam("ks_line" + i);
            if (i >= size) {
                if (team != null) {
                    team.removeEntry(entry);
                    board.resetScores(entry);
                }
                continue;
            }
            if (team == null) {
                team = board.registerNewTeam("ks_line" + i);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            String line = lines.get(i);
            if (!line.equals(team.getPrefix())) {
                team.setPrefix(line);
            }
            objective.getScore(entry).setScore(size - i);
        }
        lastLines.put(player.getUniqueId(), lines);
    }

    /**
     * Unsichtbarer, eindeutiger Eintrag fuer eine Zeile.
     * Farbcodes werden im Scoreboard nicht dargestellt und eignen sich damit
     * als "leerer" Schluessel.
     */
    private String lineEntry(int index) {
        ChatColor[] colors = ChatColor.values();
        return colors[index % colors.length].toString() + ChatColor.RESET;
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        boards.clear();
        lastLines.clear();
    }
}
