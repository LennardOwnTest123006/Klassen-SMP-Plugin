package de.klassensmp.model;

import java.util.List;

/**
 * Definition einer Aufgabe aus {@code quests.yml}.
 *
 * @param target   optionaler Filter (Material- bzw. EntityType-Name, "" = beliebig)
 * @param amount   benoetigte Anzahl
 */
public record Quest(String id,
                    String displayName,
                    String description,
                    String icon,
                    QuestPeriod period,
                    QuestType type,
                    String target,
                    int amount,
                    double moneyReward,
                    int experienceReward,
                    List<String> commands) {

    public boolean matchesTarget(String value) {
        return target == null || target.isBlank() || target.equalsIgnoreCase(value);
    }
}
