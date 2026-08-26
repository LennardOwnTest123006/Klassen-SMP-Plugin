package de.klassensmp.model;

import java.util.Locale;

/** Unterstuetzte Event-Arten fuer {@code /event}. */
public enum ServerEventType {

    /** Untergrund wird abgebaut, wer unter die Grenzhoehe faellt, scheidet aus. */
    SPLEEF,
    /** Letzter ueberlebender Spieler gewinnt (PvP-Turnier). */
    PVP,
    /** Regelmaessige Item-Drops an der Event-Position. */
    DROP,
    /** Mob-Wellen, letzte ueberlebende Gruppe gewinnt. */
    MOB_ARENA,
    /** Wer zuerst das Ziel erreicht, gewinnt. */
    PARKOUR,
    /** Versteckte Belohnungen einsammeln. */
    TREASURE,
    /** Bauwettbewerb - der Gewinner wird vom Team bestimmt. */
    BUILD;

    public static ServerEventType parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
