package de.klassensmp.model;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** Zeitraum, fuer den eine Aufgabe gilt. */
public enum QuestPeriod {

    DAILY,
    WEEKLY;

    /**
     * Schluessel des aktuellen Zeitraums. Wechselt der Schluessel, gelten die
     * Aufgaben als abgelaufen und werden neu gewuerfelt.
     */
    public String currentKey() {
        LocalDate today = LocalDate.now();
        if (this == DAILY) {
            return today.toString();
        }
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        return today.getYear() + "-W" + String.format("%02d", today.get(weekFields.weekOfWeekBasedYear()));
    }

    public static QuestPeriod parse(String raw) {
        if (raw == null) {
            return DAILY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return DAILY;
        }
    }
}
