package de.klassensmp.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formatierung und Parsen von Zeitangaben. */
public final class TimeUtil {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([smhdwSMHDW])");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY).withZone(ZoneId.systemDefault());

    private TimeUtil() {
    }

    /** Formatiert eine Dauer in Millisekunden als "3d 4h 32m" bzw. "12s". */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m");
        }
        if (days == 0 && hours == 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(seconds).append('s');
        }
        return sb.toString().trim();
    }

    /** Kompakte Spielzeit-Darstellung: "4h 32m". */
    public static String formatPlaytime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(Math.max(0, millis));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0, millis)) % 60;
        return hours + "h " + minutes + "m";
    }

    /**
     * Parst Zeitangaben wie "7d", "12h30m", "90s".
     *
     * @return Dauer in Millisekunden oder -1 bei ungueltiger Eingabe.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        Matcher matcher = DURATION.matcher(input.trim());
        long total = 0;
        int matchedChars = 0;
        while (matcher.find()) {
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                return -1;
            }
            matchedChars += matcher.group(0).length();
            switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                case 's' -> total += TimeUnit.SECONDS.toMillis(value);
                case 'm' -> total += TimeUnit.MINUTES.toMillis(value);
                case 'h' -> total += TimeUnit.HOURS.toMillis(value);
                case 'd' -> total += TimeUnit.DAYS.toMillis(value);
                case 'w' -> total += TimeUnit.DAYS.toMillis(value * 7L);
                default -> {
                    return -1;
                }
            }
        }
        if (matchedChars != input.trim().length() || total <= 0) {
            return -1;
        }
        return total;
    }

    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String fileStamp(long epochMillis) {
        return FILE_STAMP.format(Instant.ofEpochMilli(epochMillis));
    }
}
