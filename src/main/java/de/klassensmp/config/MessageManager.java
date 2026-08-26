package de.klassensmp.config;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet alle Spielertexte aus {@code messages.yml}.
 *
 * <p>Alle Nachrichten werden beim Laden gecacht und bereits eingefaerbt,
 * damit im laufenden Betrieb keine wiederholte Farbuebersetzung noetig ist.</p>
 */
public final class MessageManager {

    private static final String FILE_NAME = "messages.yml";

    private final KlassenSMP plugin;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> listCache = new ConcurrentHashMap<>();

    private YamlConfiguration messages;
    private String prefix = "";

    public MessageManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cache.clear();
        listCache.clear();
        this.messages = plugin.getConfigManager().loadFile(FILE_NAME);
        YamlConfiguration defaults = defaults();
        if (defaults != null) {
            this.messages.setDefaults(defaults);
            this.messages.options().copyDefaults(true);
            plugin.getConfigManager().saveFile(this.messages, FILE_NAME);
        }
        this.prefix = Text.color(messages.getString("prefix", "&8[&aKlassenSMP&8] &7"));
    }

    private YamlConfiguration defaults() {
        try (var stream = plugin.getResource(FILE_NAME)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    public String prefix() {
        return prefix;
    }

    /**
     * Liefert eine Nachricht. Fehlt der Schluessel, wird der Schluessel selbst
     * zurueckgegeben - so faellt eine unvollstaendige messages.yml sofort auf,
     * ohne dass das Plugin abstuerzt.
     */
    public String raw(String key) {
        return cache.computeIfAbsent(key, k -> {
            String value = messages == null ? null : messages.getString(k);
            return value == null ? "&c[" + k + "]" : Text.color(value);
        });
    }

    /** Nachricht mit Prefix und optionalen Platzhalterpaaren. */
    public String get(String key, String... placeholders) {
        String message = raw(key);
        if (message.isEmpty()) {
            return "";
        }
        return prefix + Text.replace(message, placeholders);
    }

    /** Nachricht ohne Prefix (fuer GUIs, Lore, Tablist). */
    public String plain(String key, String... placeholders) {
        return Text.replace(raw(key), placeholders);
    }

    public List<String> list(String key) {
        return listCache.computeIfAbsent(key, k -> {
            if (messages == null) {
                return List.of();
            }
            List<String> raw = messages.getStringList(k);
            return List.copyOf(Text.color(raw));
        });
    }

    public List<String> list(String key, String... placeholders) {
        List<String> source = list(key);
        List<String> out = new ArrayList<>(source.size());
        for (String line : source) {
            out.add(Text.replace(line, placeholders));
        }
        return out;
    }

    /** Sendet eine Nachricht mit Prefix. Leere Nachrichten werden unterdrueckt. */
    public void send(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        String message = raw(key);
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(prefix + Text.replace(message, placeholders));
    }

    /** Sendet eine Nachricht ohne Prefix. */
    public void sendPlain(CommandSender sender, String key, String... placeholders) {
        if (sender == null) {
            return;
        }
        String message = raw(key);
        if (message.isBlank()) {
            return;
        }
        sender.sendMessage(Text.replace(message, placeholders));
    }
}
