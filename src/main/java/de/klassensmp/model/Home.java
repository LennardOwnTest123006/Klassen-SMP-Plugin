package de.klassensmp.model;

import org.bukkit.Location;

/** Ein von einem Spieler gesetzter Heimatpunkt. */
public record Home(String name, Location location, long created) {
}
