package de.klassensmp.model;

import java.util.Locale;

/** Fortschrittsart einer Aufgabe. */
public enum QuestType {

    BREAK_BLOCKS,
    PLACE_BLOCKS,
    KILL_MOBS,
    KILL_PLAYERS,
    PLAY_MINUTES,
    EARN_MONEY,
    FISH_ITEMS,
    CRAFT_ITEMS,
    MINE_ORE,
    WALK_BLOCKS;

    public static QuestType parse(String raw) {
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
