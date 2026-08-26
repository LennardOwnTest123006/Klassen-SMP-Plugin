package de.klassensmp.model;

import java.util.UUID;

/** Ein Eintrag im Moderationslog (Ban, Mute, Warn, Kick). */
public record Punishment(int id,
                         UUID target,
                         String targetName,
                         PunishmentType type,
                         String reason,
                         String staff,
                         long created,
                         long expires,
                         boolean active) {

    /** {@code true}, wenn die Strafe unbefristet ist. */
    public boolean isPermanent() {
        return expires <= 0L;
    }

    /** {@code true}, wenn die Strafe abgelaufen ist. */
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expires;
    }

    public long remaining() {
        return isPermanent() ? Long.MAX_VALUE : Math.max(0L, expires - System.currentTimeMillis());
    }
}
