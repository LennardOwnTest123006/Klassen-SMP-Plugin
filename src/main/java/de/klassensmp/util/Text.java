package de.klassensmp.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Farb- und Text-Hilfen. Unterstuetzt klassische {@code &}-Codes sowie
 * Hex-Farben im Format {@code &#RRGGBB} (Spigot 1.16+).
 */
public final class Text {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /** Uebersetzt &-Codes und Hex-Farben in echte Chat-Farben. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder(input.length());
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>(input == null ? 0 : input.size());
        if (input != null) {
            for (String line : input) {
                out.add(color(line));
            }
        }
        return out;
    }

    /** Entfernt saemtliche Farbcodes aus einem bereits eingefaerbten Text. */
    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(color(input));
    }

    /** Ersetzt Platzhalter paarweise: replace(text, "%a%", "1", "%b%", "2"). */
    public static String replace(String text, String... pairs) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace(pairs[i], pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return result;
    }

    /** Kuerzt einen Text hart auf eine maximale Laenge (schuetzt vor zu langen Eingaben). */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    public static void send(CommandSender sender, String message) {
        if (sender != null && message != null && !message.isEmpty()) {
            sender.sendMessage(color(message));
        }
    }

    /**
     * Entfernt alle Zeichen, die in einer Chat-Nachricht oder in einem
     * Konfigurationsnamen nichts zu suchen haben (Schutz vor Format-Exploits).
     */
    public static String sanitizeName(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
