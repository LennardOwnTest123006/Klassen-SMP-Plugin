package de.klassensmp.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Ein Grab mit dem Inventar eines gestorbenen Spielers.
 *
 * <p>Der Inhalt wird ausschliesslich hier gehalten und beim Oeffnen genau
 * einmal ausgegeben - dadurch ist eine Item-Duplikation ausgeschlossen.</p>
 */
public final class Grave {

    private final int id;
    private final UUID owner;
    private final String ownerName;
    private final Location location;
    private final long created;
    private final long expires;
    private int experience;
    private ItemStack[] contents;
    private boolean claimed;

    public Grave(int id, UUID owner, String ownerName, Location location,
                 long created, long expires, int experience, ItemStack[] contents, boolean claimed) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.location = location;
        this.created = created;
        this.expires = expires;
        this.experience = experience;
        this.contents = contents == null ? new ItemStack[0] : contents;
        this.claimed = claimed;
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        return location;
    }

    public long getCreated() {
        return created;
    }

    public long getExpires() {
        return expires;
    }

    public int getExperience() {
        return experience;
    }

    /**
     * Entnimmt die gespeicherte Erfahrung und setzt sie auf 0.
     * Dadurch kann sie beim erneuten Oeffnen nicht ein zweites Mal
     * ausgezahlt werden.
     */
    public int takeExperience() {
        int value = experience;
        this.experience = 0;
        return value;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = contents == null ? new ItemStack[0] : contents;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public boolean isExpired() {
        return expires > 0 && System.currentTimeMillis() >= expires;
    }
}
