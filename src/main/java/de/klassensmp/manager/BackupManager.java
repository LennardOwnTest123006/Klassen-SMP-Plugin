package de.klassensmp.manager;

import de.klassensmp.KlassenSMP;
import de.klassensmp.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Erstellt ZIP-Backups der Welten und der Plugin-Daten.
 *
 * <p>Ablauf: Auf dem Main Thread werden die Welten gespeichert und das
 * automatische Speichern kurzzeitig abgeschaltet. Das eigentliche Kopieren
 * laeuft asynchron. Erst danach wird das Auto-Save wieder aktiviert. Dadurch
 * schreibt der Server waehrend des Backups nicht in dieselben Dateien.</p>
 */
public final class BackupManager {

    private final KlassenSMP plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private BukkitTask scheduledTask;

    public BackupManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running.get();
    }

    public File backupFolder() {
        File folder = new File(plugin.getConfigManager().string("backup.folder", "").isBlank()
                ? new File(plugin.getDataFolder(), "backups").getAbsolutePath()
                : plugin.getConfigManager().string("backup.folder", ""));
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Backup-Ordner konnte nicht erstellt werden: " + folder.getAbsolutePath());
        }
        return folder;
    }

    /** Startet bzw. erneuert den automatischen Backup-Zeitplan. */
    public void startScheduler() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
        if (!plugin.getConfigManager().bool("backup.auto.enabled", false)) {
            return;
        }
        long hours = Math.max(1L, plugin.getConfigManager().duration("backup.auto.interval-hours", 12L));
        long ticks = hours * 60L * 60L * 20L;
        this.scheduledTask = new BukkitRunnable() {
            @Override
            public void run() {
                start(Bukkit.getConsoleSender());
            }
        }.runTaskTimer(plugin, ticks, ticks);
        plugin.getLogger().info("Automatische Backups aktiv (alle " + hours + " Stunden).");
    }

    /** Ergebnis eines Backup-Starts. */
    public enum StartResult {
        STARTED,
        ALREADY_RUNNING,
        DISABLED
    }

    /**
     * Startet ein Backup.
     *
     * @param sender erhaelt Start- und Abschlussmeldung (darf {@code null} sein)
     */
    public StartResult start(CommandSender sender) {
        if (!plugin.getConfigManager().bool("backup.enabled", true)) {
            return StartResult.DISABLED;
        }
        if (!running.compareAndSet(false, true)) {
            return StartResult.ALREADY_RUNNING;
        }

        List<World> worlds = selectWorlds();
        List<File> sources = new ArrayList<>();
        List<World> disabledAutoSave = new ArrayList<>();

        for (World world : worlds) {
            world.save();
            if (world.isAutoSave()) {
                world.setAutoSave(false);
                disabledAutoSave.add(world);
            }
            sources.add(world.getWorldFolder());
        }
        if (plugin.getConfigManager().bool("backup.include-plugin-data", true)) {
            sources.add(plugin.getDataFolder());
        }

        String name = "backup_" + TimeUtil.fileStamp(System.currentTimeMillis()) + ".zip";
        File target = new File(backupFolder(), name);

        if (sender != null) {
            plugin.getMessages().send(sender, "backup.started", "%file%", name);
        }
        long start = System.currentTimeMillis();

        new BukkitRunnable() {
            @Override
            public void run() {
                boolean success = createArchive(sources, target);
                long duration = System.currentTimeMillis() - start;
                int removed = cleanupOld();

                // Zurueck auf den Main Thread fuer Bukkit-Aufrufe.
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (World world : disabledAutoSave) {
                            world.setAutoSave(true);
                        }
                        running.set(false);
                        if (sender != null) {
                            if (success) {
                                plugin.getMessages().send(sender, "backup.finished",
                                        "%file%", name,
                                        "%size%", formatSize(target.length()),
                                        "%time%", TimeUtil.formatDuration(duration),
                                        "%removed%", String.valueOf(removed));
                            } else {
                                plugin.getMessages().send(sender, "backup.failed");
                            }
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);

        return StartResult.STARTED;
    }

    private List<World> selectWorlds() {
        List<String> configured = plugin.getConfigManager().get().getStringList("backup.worlds");
        if (configured.isEmpty()) {
            return new ArrayList<>(Bukkit.getWorlds());
        }
        List<World> worlds = new ArrayList<>();
        for (String name : configured) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                worlds.add(world);
            }
        }
        return worlds;
    }

    /** Packt alle Quellordner in ein ZIP-Archiv. */
    private boolean createArchive(List<File> sources, File target) {
        List<String> excluded = new ArrayList<>();
        for (String entry : plugin.getConfigManager().get().getStringList("backup.exclude")) {
            excluded.add(entry.toLowerCase(Locale.ROOT));
        }
        if (excluded.isEmpty()) {
            excluded.addAll(Arrays.asList("session.lock", "uid.dat", "backups"));
        }

        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        try (OutputStream out = Files.newOutputStream(temporary.toPath());
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Math.max(0, Math.min(9, plugin.getConfigManager().integer("backup.compression-level", 5))));
            for (File source : sources) {
                if (source == null || !source.exists()) {
                    continue;
                }
                addFolder(zip, source.toPath(), source.getName(), excluded);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Backup fehlgeschlagen: " + ex.getMessage());
            deleteQuietly(temporary);
            return false;
        }

        // Erst nach vollstaendigem Schreiben umbenennen - ein abgebrochenes
        // Backup hinterlaesst so niemals ein scheinbar gueltiges Archiv.
        try {
            Files.move(temporary.toPath(), target.toPath());
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Backup konnte nicht abgeschlossen werden: " + ex.getMessage());
            deleteQuietly(temporary);
            return false;
        }
    }

    private void addFolder(ZipOutputStream zip, Path root, String prefix, List<String> excluded) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(Locale.ROOT);
                return excluded.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (excluded.contains(name) || name.endsWith(".part")) {
                    return FileVisitResult.CONTINUE;
                }
                String entryName = prefix + "/" + root.relativize(file).toString().replace(File.separatorChar, '/');
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = Files.newInputStream(file)) {
                    in.transferTo(zip);
                } catch (IOException ex) {
                    // Eine einzelne, gerade gesperrte Datei darf das Backup nicht abbrechen.
                    plugin.getLogger().warning("Datei uebersprungen: " + entryName);
                }
                zip.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Loescht die aeltesten Backups, bis die konfigurierte Anzahl erreicht ist. */
    private int cleanupOld() {
        int keep = Math.max(1, plugin.getConfigManager().integer("backup.keep", 5));
        File[] files = backupFolder().listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
        if (files == null || files.length <= keep) {
            return 0;
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        int removed = 0;
        for (int i = keep; i < list.size(); i++) {
            if (list.get(i).delete()) {
                removed++;
            }
        }
        return removed;
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            plugin.getLogger().warning("Temporaere Backup-Datei konnte nicht geloescht werden: " + file.getName());
        }
    }

    /** Liste vorhandener Backups, neuestes zuerst. */
    public List<File> listBackups() {
        File[] files = backupFolder().listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
        if (files == null) {
            return List.of();
        }
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(Comparator.comparingLong(File::lastModified).reversed());
        return list;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.GERMANY, "%.1f KB", bytes / 1024.0D);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0D * 1024.0D));
        }
        return String.format(Locale.GERMANY, "%.2f GB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }
}
