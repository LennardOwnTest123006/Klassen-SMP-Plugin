package de.klassensmp.performance;

import java.util.Map;

/**
 * Momentaufnahme der Serverleistung.
 *
 * <p>Alle Werte stammen direkt aus der Bukkit/Spigot-API bzw. aus eigenen
 * Messungen. Werte, die eine Spigot-Version nicht bereitstellt, sind
 * ausdruecklich als "nicht verfuegbar" markiert und werden nicht geschaetzt.</p>
 *
 * @param serverTps      vom Server gemeldete TPS oder {@code null}, wenn die
 *                       laufende Version diese API nicht anbietet
 * @param entitiesByType Anzahl Entities je Typ (nur bei Detailabfragen gefuellt)
 */
public record PerformanceSnapshot(double measuredTps,
                                  double[] serverTps,
                                  int players,
                                  int entities,
                                  int livingEntities,
                                  int items,
                                  int chunks,
                                  int hoppers,
                                  int redstonePerSecond,
                                  int hopperTransfersPerSecond,
                                  long usedMemoryMb,
                                  long maxMemoryMb,
                                  Map<String, Integer> entitiesByType,
                                  ServerStatus status,
                                  long timestamp) {

    public boolean hasServerTps() {
        return serverTps != null && serverTps.length > 0;
    }

    public double memoryPercent() {
        return maxMemoryMb <= 0 ? 0.0D : usedMemoryMb * 100.0D / maxMemoryMb;
    }
}
