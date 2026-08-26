package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.command.PluginCommand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registriert alle Befehle des Plugins.
 *
 * <p>Die Befehle selbst stehen in der {@code plugin.yml}; hier werden nur die
 * Ausfuehrenden und die Tab-Vervollstaendigung zugewiesen. Fehlt ein Befehl in
 * der {@code plugin.yml}, wird das beim Start deutlich protokolliert.</p>
 */
public final class CommandManager {

    private final KlassenSMP plugin;

    public CommandManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        Map<String, BaseCommand> commands = new LinkedHashMap<>();

        SmpCommand smp = new SmpCommand(plugin);
        commands.put("smp", smp);

        SpawnCommands spawn = new SpawnCommands(plugin);
        commands.put("spawn", spawn);
        commands.put("setspawn", spawn);

        HomeCommands home = new HomeCommands(plugin);
        commands.put("home", home);
        commands.put("sethome", home);
        commands.put("delhome", home);
        commands.put("homes", home);

        TpaCommands tpa = new TpaCommands(plugin);
        commands.put("tpa", tpa);
        commands.put("tpahere", tpa);
        commands.put("tpaccept", tpa);
        commands.put("tpdeny", tpa);

        WarpCommands warp = new WarpCommands(plugin);
        commands.put("warp", warp);
        commands.put("setwarp", warp);
        commands.put("delwarp", warp);

        EconomyCommands economy = new EconomyCommands(plugin);
        commands.put("money", economy);
        commands.put("balance", economy);
        commands.put("pay", economy);
        commands.put("baltop", economy);
        commands.put("bank", economy);
        commands.put("eco", economy);

        StatsCommands stats = new StatsCommands(plugin);
        commands.put("stats", stats);
        commands.put("playtime", stats);
        commands.put("achievements", stats);
        commands.put("quests", stats);

        commands.put("kit", new KitCommand(plugin));
        commands.put("crate", new CrateCommand(plugin));
        commands.put("grave", new GraveCommand(plugin));
        commands.put("pvp", new PvpCommand(plugin));
        commands.put("claim", new ClaimCommand(plugin));
        commands.put("event", new EventCommand(plugin));
        commands.put("mod", new StaffModeCommand(plugin));

        ChatCommands chat = new ChatCommands(plugin);
        commands.put("msg", chat);
        commands.put("reply", chat);
        commands.put("ignore", chat);
        commands.put("chat", chat);
        commands.put("mutechat", chat);
        commands.put("socialspy", chat);
        commands.put("staffchat", chat);

        PerformanceCommands performance = new PerformanceCommands(plugin);
        commands.put("performance", performance);
        commands.put("tps", performance);
        commands.put("lag", performance);
        commands.put("serverboost", performance);

        ModerationCommands moderation = new ModerationCommands(plugin);
        commands.put("kick", moderation);
        commands.put("ban", moderation);
        commands.put("tempban", moderation);
        commands.put("unban", moderation);
        commands.put("mute", moderation);
        commands.put("tempmute", moderation);
        commands.put("unmute", moderation);
        commands.put("warn", moderation);
        commands.put("warnings", moderation);
        commands.put("freeze", moderation);
        commands.put("vanish", moderation);
        commands.put("invsee", moderation);
        commands.put("endersee", moderation);
        commands.put("teleport", moderation);

        AdminCommands admin = new AdminCommands(plugin);
        commands.put("smpadmin", admin);
        commands.put("backup", admin);

        int registered = 0;
        for (Map.Entry<String, BaseCommand> entry : commands.entrySet()) {
            PluginCommand command = plugin.getCommand(entry.getKey());
            if (command == null) {
                plugin.getLogger().warning("Befehl '" + entry.getKey() + "' fehlt in der plugin.yml.");
                continue;
            }
            command.setExecutor(entry.getValue());
            command.setTabCompleter(entry.getValue());
            registered++;
        }
        plugin.getLogger().info(registered + " Befehle registriert.");
    }
}
