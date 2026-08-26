package de.klassensmp.performance;

import java.util.Locale;

/** Betriebsmodi des Server-Boosters. */
public enum BoostMode {

    /** Keine zusaetzlichen Begrenzungen. */
    NORMAL("&aNORMAL"),
    /** Moderate Begrenzungen fuer spuerbar weniger Last. */
    PERFORMANCE("&ePERFORMANCE"),
    /** Starke Begrenzungen. Muss immer bewusst aktiviert werden. */
    EXTREME("&cEXTREME");

    private final String display;

    BoostMode(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public static BoostMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
