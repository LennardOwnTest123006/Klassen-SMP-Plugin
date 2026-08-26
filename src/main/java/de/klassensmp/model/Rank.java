package de.klassensmp.model;

/**
 * Ein Rang des Klassen-SMP.
 *
 * <p>Ränge sind reine Anzeige- und Sortierinformationen. Die eigentlichen
 * Rechte kommen ausschliesslich aus dem Permission-System - es gibt keine
 * fest codierten Rechte.</p>
 *
 * @param id         interner Schluessel (z.B. "vip")
 * @param displayName Anzeigename mit Farbcodes
 * @param prefix     Chat-/Tablist-Prefix mit Farbcodes
 * @param suffix     optionaler Suffix
 * @param permission Permission, die diesen Rang zuweist
 * @param weight     hoeher = wichtiger (Sortierung in Tablist)
 * @param nameColor  Farbcode fuer den Spielernamen
 * @param homes      maximale Anzahl Homes (-1 = unbegrenzt)
 */
public record Rank(String id,
                   String displayName,
                   String prefix,
                   String suffix,
                   String permission,
                   int weight,
                   String nameColor,
                   int homes) {

    /** Sortierpraefix fuer Scoreboard-Teams: hoeheres Gewicht zuerst. */
    public String sortKey() {
        int inverted = Math.max(0, 999 - weight);
        return String.format("%03d", inverted);
    }
}
