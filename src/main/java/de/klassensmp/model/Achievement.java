package de.klassensmp.model;

/**
 * Ein Erfolg des Klassen-SMP.
 *
 * @param id      eindeutiger Schluessel
 * @param trigger Ausloeser (siehe {@link AchievementTrigger})
 * @param amount  benoetigter Zaehlerstand fuer zaehlende Ausloeser
 */
public record Achievement(String id,
                          String displayName,
                          String description,
                          String icon,
                          AchievementTrigger trigger,
                          long amount,
                          double moneyReward,
                          int experienceReward,
                          java.util.List<String> commands) {
}
