package de.klassensmp.command;

import de.klassensmp.KlassenSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Basis aller Befehle.
 *
 * <p>Uebernimmt Rechtepruefung, Spieler-/Konsolenpruefung, Fehlerbehandlung
 * und das Filtern der Tab-Vervollstaendigung. Konkrete Befehle kuemmern sich
 * dadurch nur noch um ihre eigentliche Aufgabe.</p>
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final KlassenSMP plugin;
    private final String permission;
    private final boolean playerOnly;

    protected BaseCommand(KlassenSMP plugin, String permission, boolean playerOnly) {
        this.plugin = plugin;
        this.permission = permission;
        this.playerOnly = playerOnly;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            plugin.getMessages().send(sender, "common.no-permission");
            return true;
        }
        if (playerOnly && !(sender instanceof Player)) {
            plugin.getMessages().send(sender, "common.player-only");
            return true;
        }
        try {
            execute(sender, command.getName().toLowerCase(Locale.ROOT), args);
        } catch (RuntimeException ex) {
            // Ein Fehler in einem Befehl darf niemals den Server beeintraechtigen.
            plugin.getLogger().log(Level.SEVERE,
                    "Fehler im Befehl /" + command.getName() + " von " + sender.getName(), ex);
            plugin.getMessages().send(sender, "common.command-error");
        }
        return true;
    }

    /**
     * Fuehrt den Befehl aus.
     *
     * @param name Befehlsname in Kleinbuchstaben (fuer Klassen, die mehrere Befehle bedienen)
     */
    protected abstract void execute(CommandSender sender, String name, String[] args);

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (permission != null && !permission.isBlank() && !sender.hasPermission(permission)) {
            return List.of();
        }
        List<String> suggestions = complete(sender, command.getName().toLowerCase(Locale.ROOT), args);
        if (suggestions == null || suggestions.isEmpty() || args.length == 0) {
            return List.of();
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && suggestion.toLowerCase(Locale.ROOT).startsWith(current)) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }

    /** Vorschlaege fuer die Tab-Vervollstaendigung (ungefiltert). */
    protected List<String> complete(CommandSender sender, String name, String[] args) {
        return List.of();
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    /**
     * Sichtbare Spielernamen.
     * Versteckte Spieler erscheinen nur fuer Teammitglieder - so verraet die
     * Tab-Vervollstaendigung keine Moderationsinformationen.
     */
    protected List<String> visiblePlayerNames(CommandSender sender) {
        boolean seeVanished = sender.hasPermission("klassensmp.vanish.see");
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!seeVanished && plugin.getVanishManager().isVanished(player)) {
                continue;
            }
            names.add(player.getName());
        }
        return names;
    }

    /** Alle bekannten Spielernamen (auch offline) - nur fuer Teambefehle. */
    protected List<String> knownPlayerNames() {
        return plugin.getPlayerDataManager().knownNames();
    }

    /** Sucht einen sichtbaren Onlinespieler; meldet dem Absender, wenn nicht gefunden. */
    protected Player findOnline(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || (!sender.hasPermission("klassensmp.vanish.see")
                && plugin.getVanishManager().isVanished(target))) {
            plugin.getMessages().send(sender, "common.player-not-found", "%player%", name);
            return null;
        }
        return target;
    }

    /** Fuegt die restlichen Argumente zu einem Text zusammen. */
    protected String join(String[] args, int from) {
        if (args.length <= from) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    protected Player asPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
