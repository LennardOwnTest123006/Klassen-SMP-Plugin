package de.klassensmp.event;

import de.klassensmp.model.ServerEventType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Konfiguration eines Server-Events aus {@code events.yml}.
 *
 * @param warp        Name eines Warps, zu dem die Teilnehmer teleportiert werden
 * @param settings    typspezifische Zusatzeinstellungen
 */
public record EventDefinition(String id,
                              String displayName,
                              String description,
                              String icon,
                              ServerEventType type,
                              String warp,
                              int countdownSeconds,
                              int minPlayers,
                              int maxPlayers,
                              int durationSeconds,
                              double rewardMoney,
                              int rewardExperience,
                              List<String> rewardCommands,
                              ConfigurationSection settings) {

    public int setting(String key, int fallback) {
        return settings == null ? fallback : settings.getInt(key, fallback);
    }

    public double setting(String key, double fallback) {
        return settings == null ? fallback : settings.getDouble(key, fallback);
    }

    public String setting(String key, String fallback) {
        return settings == null ? fallback : settings.getString(key, fallback);
    }

    public List<String> settingList(String key) {
        return settings == null ? List.of() : settings.getStringList(key);
    }
}
