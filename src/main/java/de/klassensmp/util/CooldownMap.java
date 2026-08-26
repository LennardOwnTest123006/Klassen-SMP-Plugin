package de.klassensmp.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speichert Cooldowns pro Spieler. Abgelaufene Eintraege werden beim Zugriff
 * und ueber {@link #cleanup()} entfernt, damit die Map nicht unbegrenzt waechst.
 */
public final class CooldownMap {

    private final Map<UUID, Long> expiry = new ConcurrentHashMap<>();

    /** Setzt einen Cooldown in Millisekunden. */
    public void set(UUID uuid, long durationMillis) {
        if (uuid == null || durationMillis <= 0) {
            return;
        }
        expiry.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    /** @return verbleibende Millisekunden oder 0, wenn kein Cooldown aktiv ist. */
    public long remaining(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = expiry.get(uuid);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            expiry.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    public boolean isActive(UUID uuid) {
        return remaining(uuid) > 0L;
    }

    public void clear(UUID uuid) {
        if (uuid != null) {
            expiry.remove(uuid);
        }
    }

    public void clearAll() {
        expiry.clear();
    }

    /** Entfernt alle abgelaufenen Eintraege. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        expiry.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public int size() {
        return expiry.size();
    }
}
