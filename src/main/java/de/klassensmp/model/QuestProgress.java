package de.klassensmp.model;

/** Fortschritt eines Spielers bei einer Aufgabe im aktuellen Zeitraum. */
public final class QuestProgress {

    private final String questId;
    private final QuestPeriod period;
    private final String periodKey;
    private final int target;

    private int progress;
    private boolean claimed;
    private boolean dirty;

    public QuestProgress(String questId, QuestPeriod period, String periodKey, int target, int progress, boolean claimed) {
        this.questId = questId;
        this.period = period;
        this.periodKey = periodKey;
        this.target = Math.max(1, target);
        this.progress = Math.max(0, progress);
        this.claimed = claimed;
    }

    public String getQuestId() {
        return questId;
    }

    public QuestPeriod getPeriod() {
        return period;
    }

    public String getPeriodKey() {
        return periodKey;
    }

    public int getTarget() {
        return target;
    }

    public int getProgress() {
        return progress;
    }

    /** @return {@code true}, wenn die Aufgabe durch diesen Schritt fertig wurde. */
    public boolean addProgress(int amount) {
        if (amount <= 0 || isComplete()) {
            return false;
        }
        this.progress = Math.min(target, progress + amount);
        this.dirty = true;
        return isComplete();
    }

    public boolean isComplete() {
        return progress >= target;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public int percent() {
        return (int) Math.round(Math.min(100.0D, progress * 100.0D / target));
    }
}
