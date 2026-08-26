package de.klassensmp.model;

import java.util.Locale;

/** Ausloeser, die einen Erfolg freischalten koennen. */
public enum AchievementTrigger {

    /** Erster Beitritt zum Server. */
    FIRST_JOIN,
    /** Anzahl abgebauter Bloecke. */
    BLOCKS_BROKEN,
    /** Anzahl platzierter Bloecke. */
    BLOCKS_PLACED,
    /** Spielzeit in Minuten. */
    PLAYTIME_MINUTES,
    /** Getoetete Spieler. */
    PLAYER_KILLS,
    /** Getoetete Mobs. */
    MOB_KILLS,
    /** Insgesamt verdientes Geld. */
    MONEY_EARNED,
    /** Erstmals einen bestimmten Block abgebaut (Material in {@code icon}-Logik ueber die Config). */
    MINE_DIAMOND,
    /** Betreten des Nether. */
    ENTER_NETHER,
    /** Betreten des End. */
    ENTER_END,
    /** Enderdrache besiegt. */
    KILL_DRAGON,
    /** Anzahl gestellter Homes. */
    HOMES_SET;

    public static AchievementTrigger parse(String raw) {
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
