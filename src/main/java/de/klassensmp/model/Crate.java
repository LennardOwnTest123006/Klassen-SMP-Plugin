package de.klassensmp.model;

import java.util.List;

/** Eine Crate mit gewichteten Belohnungen. */
public record Crate(String id,
                    String displayName,
                    String icon,
                    String keyPermission,
                    List<CrateReward> rewards) {

    /** Summe aller Gewichte - Basis fuer die Zufallsauswahl. */
    public double totalWeight() {
        double total = 0;
        for (CrateReward reward : rewards) {
            total += Math.max(0.0D, reward.chance());
        }
        return total;
    }
}
