package de.klassensmp.model;

import java.util.UUID;

/**
 * Zwischengespeicherte Spielerdaten.
 *
 * <p>Die Instanz lebt solange der Spieler online ist und wird periodisch sowie
 * beim Verlassen in die Datenbank geschrieben. Geldoperationen sind
 * synchronisiert, damit gleichzeitige Zugriffe (z.B. Shop und {@code /pay})
 * keine inkonsistenten Betraege erzeugen koennen.</p>
 */
public final class PlayerData {

    private final UUID uuid;

    private volatile String name;
    private volatile long firstJoin;
    private volatile long lastJoin;
    private volatile long lastQuit;
    private volatile long storedPlaytime;

    private double money;
    private double bank;
    private double earned;
    private double spent;

    private volatile int kills;
    private volatile int deaths;
    private volatile int mobKills;
    private volatile long blocksBroken;
    private volatile long blocksPlaced;

    private volatile boolean pvpEnabled = true;
    private volatile boolean bedrock;

    /** Zeitpunkt des aktuellen Session-Beginns (0 = offline). */
    private volatile long sessionStart;
    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            markDirty();
        }
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(long lastJoin) {
        this.lastJoin = lastJoin;
        markDirty();
    }

    public long getLastQuit() {
        return lastQuit;
    }

    public void setLastQuit(long lastQuit) {
        this.lastQuit = lastQuit;
        markDirty();
    }

    /** Gesamte Spielzeit inklusive der laufenden Session. */
    public long getTotalPlaytime() {
        long session = sessionStart > 0 ? System.currentTimeMillis() - sessionStart : 0L;
        return storedPlaytime + Math.max(0L, session);
    }

    public long getStoredPlaytime() {
        return storedPlaytime;
    }

    public void setStoredPlaytime(long storedPlaytime) {
        this.storedPlaytime = Math.max(0L, storedPlaytime);
    }

    public void startSession() {
        this.sessionStart = System.currentTimeMillis();
    }

    /** Uebernimmt die laufende Session in die gespeicherte Spielzeit. */
    public void flushSession() {
        if (sessionStart > 0) {
            long delta = System.currentTimeMillis() - sessionStart;
            if (delta > 0) {
                this.storedPlaytime += delta;
            }
            this.sessionStart = System.currentTimeMillis();
            markDirty();
        }
    }

    public void endSession() {
        flushSession();
        this.sessionStart = 0L;
    }

    // ------------------------------------------------------------------
    // Economy
    // ------------------------------------------------------------------

    public synchronized double getMoney() {
        return money;
    }

    public synchronized void setMoney(double money) {
        this.money = Math.max(0.0D, money);
        markDirty();
    }

    public synchronized double getBank() {
        return bank;
    }

    public synchronized void setBank(double bank) {
        this.bank = Math.max(0.0D, bank);
        markDirty();
    }

    public synchronized double getEarned() {
        return earned;
    }

    public synchronized void setEarned(double earned) {
        this.earned = Math.max(0.0D, earned);
    }

    public synchronized double getSpent() {
        return spent;
    }

    public synchronized void setSpent(double spent) {
        this.spent = Math.max(0.0D, spent);
    }

    /** Erhoeht das Guthaben und protokolliert die Einnahme. */
    public synchronized void addMoney(double amount) {
        if (amount <= 0) {
            return;
        }
        this.money += amount;
        this.earned += amount;
        markDirty();
    }

    /**
     * Bucht einen Betrag ab.
     *
     * @return {@code true}, wenn genug Guthaben vorhanden war.
     */
    public synchronized boolean removeMoney(double amount) {
        if (amount <= 0 || money < amount) {
            return false;
        }
        this.money -= amount;
        this.spent += amount;
        markDirty();
        return true;
    }

    // ------------------------------------------------------------------
    // Statistiken
    // ------------------------------------------------------------------

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = Math.max(0, kills);
    }

    public void addKill() {
        this.kills++;
        markDirty();
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = Math.max(0, deaths);
    }

    public void addDeath() {
        this.deaths++;
        markDirty();
    }

    public int getMobKills() {
        return mobKills;
    }

    public void setMobKills(int mobKills) {
        this.mobKills = Math.max(0, mobKills);
    }

    public void addMobKill() {
        this.mobKills++;
        markDirty();
    }

    public long getBlocksBroken() {
        return blocksBroken;
    }

    public void setBlocksBroken(long blocksBroken) {
        this.blocksBroken = Math.max(0L, blocksBroken);
    }

    public void addBlockBroken() {
        this.blocksBroken++;
        markDirty();
    }

    public long getBlocksPlaced() {
        return blocksPlaced;
    }

    public void setBlocksPlaced(long blocksPlaced) {
        this.blocksPlaced = Math.max(0L, blocksPlaced);
    }

    public void addBlockPlaced() {
        this.blocksPlaced++;
        markDirty();
    }

    /** Kill/Death-Verhaeltnis, Tode von 0 werden als 1 gewertet. */
    public double getKdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    // ------------------------------------------------------------------
    // Sonstiges
    // ------------------------------------------------------------------

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
        markDirty();
    }

    public boolean isBedrock() {
        return bedrock;
    }

    public void setBedrock(boolean bedrock) {
        this.bedrock = bedrock;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
