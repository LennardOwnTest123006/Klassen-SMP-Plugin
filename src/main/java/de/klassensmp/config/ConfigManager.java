package de.klassensmp.config;

import de.klassensmp.KlassenSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Laedt und verwaltet {@code config.yml} sowie alle zusaetzlichen
 * Konfigurationsdateien des Plugins.
 */
public final class ConfigManager {

    private final KlassenSMP plugin;
    private FileConfiguration config;

    public ConfigManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    /** Laedt bzw. laedt die Hauptkonfiguration neu. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        // Fehlende Schluessel aus der mitgelieferten Standarddatei ergaenzen.
        this.config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public FileConfiguration get() {
        return config;
    }

    public boolean bool(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    public int integer(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    public long duration(String path, long fallbackSeconds) {
        return config.getLong(path, fallbackSeconds);
    }

    public double number(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public String string(String path, String fallback) {
        String value = config.getString(path);
        return value == null ? fallback : value;
    }

    /**
     * Laedt eine zusaetzliche YAML-Datei aus dem Plugin-Ordner und legt sie
     * beim ersten Start aus den Ressourcen an.
     */
    public YamlConfiguration loadFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
            } else {
                try {
                    if (file.getParentFile() != null) {
                        file.getParentFile().mkdirs();
                    }
                    if (!file.createNewFile()) {
                        plugin.getLogger().warning("Datei konnte nicht erstellt werden: " + fileName);
                    }
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Konnte " + fileName + " nicht anlegen", ex);
                }
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Speichert eine zuvor geladene Zusatzdatei. */
    public void saveFile(YamlConfiguration configuration, String fileName) {
        if (configuration == null) {
            return;
        }
        File file = new File(plugin.getDataFolder(), fileName);
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            configuration.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Konnte " + fileName + " nicht speichern", ex);
        }
    }

    public File dataFolder() {
        return plugin.getDataFolder();
    }

    /** Liest einen Enum-artigen Konfigurationswert case-insensitiv. */
    public String enumString(String path, String fallback) {
        return string(path, fallback).trim().toUpperCase(Locale.ROOT);
    }
}
