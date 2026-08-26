package de.klassensmp.performance;

/** Grobe Bewertung der aktuellen Serverleistung. */
public enum ServerStatus {

    GOOD("&aSEHR GUT", "&a🟢"),
    MEDIUM("&eMITTEL", "&e🟡"),
    CRITICAL("&cKRITISCH", "&c🔴");

    private final String display;
    private final String icon;

    ServerStatus(String display, String icon) {
        this.display = display;
        this.icon = icon;
    }

    public String getDisplay() {
        return display;
    }

    public String getIcon() {
        return icon;
    }
}
