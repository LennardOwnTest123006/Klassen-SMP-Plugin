package de.klassensmp.model;

import org.bukkit.Location;

/**
 * Ein serverweiter Warp-Punkt.
 *
 * @param permission leere Zeichenkette = fuer alle nutzbar
 * @param icon       Material-Name fuer die Warp-GUI (darf leer sein)
 */
public record Warp(String name, Location location, String permission, String icon, String creator, long created) {

    public boolean isProtected() {
        return permission != null && !permission.isBlank();
    }
}
