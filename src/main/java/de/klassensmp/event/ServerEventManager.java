package de.klassensmp.event;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.ServerEventType;
import de.klassensmp.model.Warp;
import de.klassensmp.util.Compat;
import de.klassensmp.util.ItemBuilder;
import de.klassensmp.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event-System des Klassen-SMP.
 *
 * <p>Es kann immer genau ein Event gleichzeitig laufen. Der Ablauf ist fuer
 * alle Arten gleich: ankuendigen, Anmeldephase mit Countdown, Start,
 * typspezifische Regeln, Sieger ermitteln, Belohnung ausschuetten.</p>
 */
public final class ServerEventManager {

    private static final String FILE = "events.yml";
    private static final String TREASURE_MARKER = "KlassenSMP-Schatz";

    private final KlassenSMP plugin;
    private final Map<String, EventDefinition> definitions = new ConcurrentHashMap<>();

    private EventDefinition active;
    private EventState state = EventState.ENDED;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    /** Positionen, an die Teilnehmer nach dem Event zurueckkehren. */
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new ConcurrentHashMap<>();

    private final List<BukkitTask> tasks = new ArrayList<>();
    private int countdown;
    private int wave;

    public ServerEventManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Konfiguration
    // ------------------------------------------------------------------

    public void load() {
        definitions.clear();
        var config = plugin.getConfigManager().loadFile(FILE);
        var section = config.getConfigurationSection("events");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            var entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            ServerEventType type = ServerEventType.parse(entry.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("Event '" + key + "' hat einen unbekannten Typ.");
                continue;
            }
            String id = key.toLowerCase(Locale.ROOT);
            definitions.put(id, new EventDefinition(id,
                    entry.getString("display", key),
                    entry.getString("description", ""),
                    entry.getString("icon", "FIREWORK_ROCKET"),
                    type,
                    entry.getString("warp", ""),
                    Math.max(5, entry.getInt("countdown", 30)),
                    Math.max(1, entry.getInt("min-players", 2)),
                    Math.max(0, entry.getInt("max-players", 0)),
                    Math.max(30, entry.getInt("duration", 300)),
                    Math.max(0.0D, entry.getDouble("reward-money", 0.0D)),
                    Math.max(0, entry.getInt("reward-experience", 0)),
                    entry.getStringList("reward-commands"),
                    entry.getConfigurationSection("settings")));
        }
        plugin.getLogger().info(definitions.size() + " Events geladen.");
    }

    public EventDefinition getDefinition(String id) {
        return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public List<EventDefinition> all() {
        List<EventDefinition> list = new ArrayList<>(definitions.values());
        list.sort(Comparator.comparing(EventDefinition::id));
        return list;
    }

    public List<String> nameList() {
        return new ArrayList<>(definitions.keySet());
    }

    // ------------------------------------------------------------------
    // Zustand
    // ------------------------------------------------------------------

    public EventDefinition getActive() {
        return active;
    }

    public EventState getState() {
        return state;
    }

    public boolean isRunning() {
        return active != null && state != EventState.ENDED;
    }

    public boolean isParticipant(Player player) {
        return player != null && participants.contains(player.getUniqueId());
    }

    public int participantCount() {
        return participants.size();
    }

    /** {@code true}, wenn im aktiven Event PvP erlaubt sein soll. */
    public boolean isPvpEvent() {
        return active != null && state == EventState.RUNNING
                && (active.type() == ServerEventType.PVP || active.type() == ServerEventType.MOB_ARENA);
    }

    // ------------------------------------------------------------------
    // Start / Stop
    // ------------------------------------------------------------------

    /** Ergebnis eines Startversuchs. */
    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        UNKNOWN_EVENT,
        NO_LOCATION
    }

    public StartResult start(CommandSender sender, String id) {
        if (isRunning()) {
            return StartResult.ALREADY_RUNNING;
        }
        EventDefinition definition = getDefinition(id);
        if (definition == null) {
            return StartResult.UNKNOWN_EVENT;
        }
        if (resolveLocation(definition) == null) {
            return StartResult.NO_LOCATION;
        }

        this.active = definition;
        this.state = EventState.WAITING;
        this.countdown = definition.countdownSeconds();
        this.wave = 0;
        participants.clear();
        scores.clear();
        returnLocations.clear();
        previousGameModes.clear();

        Bukkit.broadcastMessage(plugin.getMessages().get("events.announced",
                "%event%", definition.displayName(),
                "%seconds%", String.valueOf(countdown)));

        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::tickCountdown, 20L, 20L));
        return StartResult.STARTED;
    }

    private void tickCountdown() {
        if (state != EventState.WAITING || active == null) {
            return;
        }
        countdown--;
        if (countdown == 30 || countdown == 15 || countdown == 10 || (countdown <= 5 && countdown > 0)) {
            Bukkit.broadcastMessage(plugin.getMessages().get("events.countdown",
                    "%event%", active.displayName(),
                    "%seconds%", String.valueOf(countdown)));
        }
        if (countdown <= 0) {
            if (participants.size() < active.minPlayers()) {
                Bukkit.broadcastMessage(plugin.getMessages().get("events.not-enough-players",
                        "%event%", active.displayName(),
                        "%needed%", String.valueOf(active.minPlayers())));
                stopActiveEvent(null, false);
                return;
            }
            beginEvent();
        }
    }

    private void beginEvent() {
        this.state = EventState.RUNNING;
        cancelTasks();

        Location location = resolveLocation(active);
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            returnLocations.put(uuid, player.getLocation().clone());
            previousGameModes.put(uuid, player.getGameMode());
            if (location != null) {
                plugin.getTeleportManager().teleportInstant(player, location, null);
            }
            scores.put(uuid, 0);
            plugin.getMessages().send(player, "events.started", "%event%", active.displayName());
        }

        Bukkit.broadcastMessage(plugin.getMessages().get("events.begin", "%event%", active.displayName()));

        switch (active.type()) {
            case DROP -> startDropTask();
            case MOB_ARENA -> startArenaTask();
            case TREASURE -> startTreasureHunt();
            default -> {
                // SPLEEF, PVP, PARKOUR und BUILD benoetigen keine eigene Schleife.
            }
        }

        // Zeitlimit
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin,
                () -> finish(null, "events.time-over"), active.durationSeconds() * 20L));
    }

    /** Beendet das laufende Event. */
    public void stopActiveEvent(CommandSender sender, boolean silent) {
        if (active == null) {
            return;
        }
        cancelTasks();
        restoreAll();

        if (!silent) {
            Bukkit.broadcastMessage(plugin.getMessages().get("events.stopped", "%event%", active.displayName()));
        }
        if (sender != null) {
            plugin.getMessages().send(sender, "events.stopped-staff", "%event%", active.displayName());
        }

        this.active = null;
        this.state = EventState.ENDED;
        participants.clear();
        scores.clear();
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    private void restoreAll() {
        for (UUID uuid : new ArrayList<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            GameMode previous = previousGameModes.remove(uuid);
            if (previous != null) {
                player.setGameMode(previous);
            }
            Location back = returnLocations.remove(uuid);
            if (back != null) {
                plugin.getTeleportManager().teleportInstant(player, back, null);
            }
        }
        returnLocations.clear();
        previousGameModes.clear();
    }

    private Location resolveLocation(EventDefinition definition) {
        if (definition.warp() != null && !definition.warp().isBlank()) {
            Warp warp = plugin.getWarpManager().get(definition.warp());
            if (warp != null) {
                return warp.location();
            }
        }
        return plugin.getSpawnManager().getSpawn();
    }

    // ------------------------------------------------------------------
    // Teilnahme
    // ------------------------------------------------------------------

    /** Ergebnis eines Beitrittsversuchs. */
    public enum JoinResult {
        SUCCESS,
        NO_EVENT,
        ALREADY_STARTED,
        ALREADY_JOINED,
        FULL
    }

    public JoinResult join(Player player) {
        if (active == null || state == EventState.ENDED) {
            return JoinResult.NO_EVENT;
        }
        if (state == EventState.RUNNING) {
            return JoinResult.ALREADY_STARTED;
        }
        if (!participants.add(player.getUniqueId())) {
            return JoinResult.ALREADY_JOINED;
        }
        if (active.maxPlayers() > 0 && participants.size() > active.maxPlayers()) {
            participants.remove(player.getUniqueId());
            return JoinResult.FULL;
        }
        Bukkit.broadcastMessage(plugin.getMessages().get("events.joined",
                "%player%", player.getName(),
                "%count%", String.valueOf(participants.size())));
        return JoinResult.SUCCESS;
    }

    public void leave(Player player) {
        if (participants.remove(player.getUniqueId())) {
            scores.remove(player.getUniqueId());
            GameMode previous = previousGameModes.remove(player.getUniqueId());
            if (previous != null) {
                player.setGameMode(previous);
            }
            Location back = returnLocations.remove(player.getUniqueId());
            if (back != null && state == EventState.RUNNING) {
                plugin.getTeleportManager().teleportInstant(player, back, null);
            }
            plugin.getMessages().send(player, "events.left");
            checkLastStanding();
        }
    }

    public void handleQuit(Player player) {
        if (participants.remove(player.getUniqueId())) {
            scores.remove(player.getUniqueId());
            previousGameModes.remove(player.getUniqueId());
            returnLocations.remove(player.getUniqueId());
            checkLastStanding();
        }
    }

    /** Scheidet einen Spieler aus. */
    public void eliminate(Player player, String reasonKey) {
        if (!participants.remove(player.getUniqueId())) {
            return;
        }
        scores.remove(player.getUniqueId());
        GameMode previous = previousGameModes.remove(player.getUniqueId());
        if (previous != null) {
            player.setGameMode(previous);
        }
        Location back = returnLocations.remove(player.getUniqueId());
        if (back != null) {
            plugin.getTeleportManager().teleportInstant(player, back, null);
        }
        plugin.getMessages().send(player, reasonKey == null ? "events.eliminated" : reasonKey);
        Bukkit.broadcastMessage(plugin.getMessages().get("events.eliminated-broadcast",
                "%player%", player.getName(),
                "%left%", String.valueOf(participants.size())));
        checkLastStanding();
    }

    private void checkLastStanding() {
        if (state != EventState.RUNNING || active == null) {
            return;
        }
        boolean lastManStanding = active.type() == ServerEventType.SPLEEF
                || active.type() == ServerEventType.PVP
                || active.type() == ServerEventType.MOB_ARENA;
        if (!lastManStanding) {
            return;
        }
        if (participants.size() <= 1) {
            UUID winner = participants.stream().findFirst().orElse(null);
            finish(winner == null ? null : Bukkit.getPlayer(winner), "events.finished");
        }
    }

    // ------------------------------------------------------------------
    // Typspezifische Regeln
    // ------------------------------------------------------------------

    /** Vom Bewegungs-Listener aufgerufen. */
    public void handleMove(Player player, Location to) {
        if (state != EventState.RUNNING || active == null || !isParticipant(player) || to == null) {
            return;
        }
        if (active.type() == ServerEventType.SPLEEF) {
            int floor = active.setting("floor-y", Integer.MIN_VALUE);
            if (floor != Integer.MIN_VALUE && to.getBlockY() < floor) {
                eliminate(player, "events.spleef-fell");
            }
        } else if (active.type() == ServerEventType.PARKOUR) {
            Location goal = parkourGoal();
            if (goal != null && to.getWorld() != null && to.getWorld().equals(goal.getWorld())
                    && to.distanceSquared(goal) <= 4.0D) {
                finish(player, "events.finished");
            }
        }
    }

    private Location parkourGoal() {
        String warpName = active.setting("goal-warp", "");
        if (warpName.isBlank()) {
            return null;
        }
        Warp warp = plugin.getWarpManager().get(warpName);
        return warp == null ? null : warp.location();
    }

    /** Vom Todes-Listener aufgerufen. */
    public void handleDeath(Player player) {
        if (state != EventState.RUNNING || active == null || !isParticipant(player)) {
            return;
        }
        if (active.type() == ServerEventType.PVP
                || active.type() == ServerEventType.MOB_ARENA
                || active.type() == ServerEventType.SPLEEF) {
            eliminate(player, "events.died");
        }
    }

    /** Vom Aufsammel-Listener aufgerufen (Schatzsuche). */
    public void handleTreasurePickup(Player player, ItemStack item) {
        if (state != EventState.RUNNING || active == null
                || active.type() != ServerEventType.TREASURE || !isParticipant(player)) {
            return;
        }
        if (item == null || item.getItemMeta() == null || item.getItemMeta().getLore() == null) {
            return;
        }
        if (!item.getItemMeta().getLore().contains(de.klassensmp.util.Text.color("&8" + TREASURE_MARKER))) {
            return;
        }
        int score = scores.merge(player.getUniqueId(), 1, Integer::sum);
        int needed = active.setting("treasures-to-win", 5);
        plugin.getMessages().send(player, "events.treasure-found",
                "%found%", String.valueOf(score), "%needed%", String.valueOf(needed));
        if (score >= needed) {
            finish(player, "events.finished");
        }
    }

    private void startDropTask() {
        int interval = Math.max(5, active.setting("interval-seconds", 20));
        List<String> items = active.settingList("items");
        Location location = resolveLocation(active);
        if (location == null) {
            return;
        }
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (items.isEmpty() || location.getWorld() == null) {
                return;
            }
            String entry = items.get(ThreadLocalRandom.current().nextInt(items.size()));
            String[] parts = entry.split(":");
            Material material = Compat.material(parts[0], Material.DIAMOND);
            int amount = parts.length > 1 ? NumberUtil.parseInt(parts[1], 1) : 1;
            location.getWorld().dropItemNaturally(location.clone().add(0, 3, 0),
                    new ItemStack(material, Math.max(1, amount)));
            Bukkit.broadcastMessage(plugin.getMessages().get("events.drop", "%event%", active.displayName()));
        }, interval * 20L, interval * 20L));
    }

    private void startArenaTask() {
        int interval = Math.max(10, active.setting("wave-interval-seconds", 30));
        int perWave = Math.max(1, active.setting("mobs-per-wave", 5));
        String mobName = active.setting("mob", "ZOMBIE");
        Location location = resolveLocation(active);
        if (location == null || location.getWorld() == null) {
            return;
        }
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(mobName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unbekannter Mob-Typ im Event: " + mobName);
            return;
        }

        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            wave++;
            int amount = perWave * wave;
            for (int i = 0; i < amount; i++) {
                double offsetX = ThreadLocalRandom.current().nextDouble(-6, 6);
                double offsetZ = ThreadLocalRandom.current().nextDouble(-6, 6);
                location.getWorld().spawnEntity(location.clone().add(offsetX, 1, offsetZ), entityType);
            }
            Bukkit.broadcastMessage(plugin.getMessages().get("events.wave",
                    "%wave%", String.valueOf(wave), "%amount%", String.valueOf(amount)));
        }, interval * 20L, interval * 20L));
    }

    private void startTreasureHunt() {
        int amount = Math.max(1, active.setting("treasure-count", 20));
        int radius = Math.max(10, active.setting("radius", 60));
        Location center = resolveLocation(active);
        if (center == null || center.getWorld() == null) {
            return;
        }
        ItemStack treasure = new ItemBuilder(
                Compat.material(active.setting("treasure-item", "GOLD_INGOT"), Material.GOLD_INGOT))
                .name(plugin.getMessages().plain("events.treasure-name"))
                .lore("&8" + TREASURE_MARKER)
                .build();

        for (int i = 0; i < amount; i++) {
            double x = center.getX() + ThreadLocalRandom.current().nextDouble(-radius, radius);
            double z = center.getZ() + ThreadLocalRandom.current().nextDouble(-radius, radius);
            int y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;
            center.getWorld().dropItem(new Location(center.getWorld(), x, y, z), treasure.clone());
        }
    }

    // ------------------------------------------------------------------
    // Abschluss
    // ------------------------------------------------------------------

    /** Beendet das Event und vergibt die Belohnung an den Sieger. */
    public void finish(Player winner, String messageKey) {
        if (active == null || state == EventState.ENDED) {
            return;
        }
        EventDefinition definition = active;
        cancelTasks();

        if (winner != null) {
            if (definition.rewardMoney() > 0) {
                plugin.getEconomyManager().deposit(winner.getUniqueId(), definition.rewardMoney());
            }
            if (definition.rewardExperience() > 0) {
                winner.giveExp(definition.rewardExperience());
            }
            for (String command : definition.rewardCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", winner.getName()));
            }
            Bukkit.broadcastMessage(plugin.getMessages().get("events.winner",
                    "%player%", winner.getName(),
                    "%event%", definition.displayName(),
                    "%reward%", plugin.getEconomyManager().format(definition.rewardMoney())));
            if (plugin.getConfigManager().bool("sounds.enabled", true)) {
                Compat.playSound(winner,
                        plugin.getConfigManager().string("sounds.event-win", "ui.toast.challenge_complete"), 1F, 1F);
            }
        } else if (messageKey != null) {
            Bukkit.broadcastMessage(plugin.getMessages().get(messageKey, "%event%", definition.displayName()));
        }

        restoreAll();
        this.active = null;
        this.state = EventState.ENDED;
        participants.clear();
        scores.clear();
    }

    /** Bestimmt manuell einen Sieger (z.B. beim Bauwettbewerb). */
    public boolean declareWinner(Player winner) {
        if (active == null || state != EventState.RUNNING) {
            return false;
        }
        finish(winner, "events.finished");
        return true;
    }

    /** Aktuelle Punktestaende, absteigend sortiert. */
    public Map<String, Integer> scoreboardEntries() {
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(scores.entrySet());
        entries.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : entries) {
            Player player = Bukkit.getPlayer(entry.getKey());
            result.put(player == null ? entry.getKey().toString() : player.getName(), entry.getValue());
        }
        return result;
    }

    public int getCountdown() {
        return countdown;
    }

    public List<String> participantNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
