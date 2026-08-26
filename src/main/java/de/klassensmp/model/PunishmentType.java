package de.klassensmp.model;

import java.util.Locale;

/** Art einer Moderationsmassnahme. */
public enum PunishmentType {

    BAN("Bann"),
    MUTE("Stummschaltung"),
    WARN("Verwarnung"),
    KICK("Kick");

    private final String displayName;

    PunishmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PunishmentType parse(String raw) {
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
