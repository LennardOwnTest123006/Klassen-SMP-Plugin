package de.klassensmp.economy;

import de.klassensmp.KlassenSMP;
import de.klassensmp.model.PlayerData;
import de.klassensmp.util.NumberUtil;

import java.util.UUID;

/**
 * Economy des Klassen-SMP.
 *
 * <p>Alle Betraege werden auf zwei Nachkommastellen gerundet und gegen ein
 * konfigurierbares Maximum geprueft. Negative Betraege sind ausgeschlossen,
 * Ueberweisungen laufen atomar ueber eine gemeinsame Sperre - damit sind die
 * klassischen Economy-Duplikationen nicht moeglich.</p>
 */
public final class EconomyManager {

    private final KlassenSMP plugin;

    /** Gemeinsame Sperre fuer Transaktionen, die zwei Konten betreffen. */
    private final Object transferLock = new Object();

    public EconomyManager(KlassenSMP plugin) {
        this.plugin = plugin;
    }

    public String getCurrencySymbol() {
        return plugin.getConfigManager().string("economy.symbol", "$");
    }

    public String getCurrencySingular() {
        return plugin.getConfigManager().string("economy.name-singular", "Muenze");
    }

    public String getCurrencyPlural() {
        return plugin.getConfigManager().string("economy.name-plural", "Muenzen");
    }

    public double getMaxBalance() {
        double max = plugin.getConfigManager().number("economy.max-balance", 10_000_000.0D);
        return max <= 0 ? Double.MAX_VALUE : max;
    }

    /** Formatiert einen Betrag inklusive Waehrungssymbol. */
    public String format(double amount) {
        String symbol = getCurrencySymbol();
        boolean before = plugin.getConfigManager().bool("economy.symbol-before-amount", true);
        String formatted = NumberUtil.formatMoney(amount);
        return before ? symbol + formatted : formatted + " " + symbol;
    }

    public double getBalance(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        return data == null ? 0.0D : data.getMoney();
    }

    public double getBank(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        return data == null ? 0.0D : data.getBank();
    }

    public double getTotal(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        return data == null ? 0.0D : data.getMoney() + data.getBank();
    }

    public boolean has(UUID uuid, double amount) {
        return amount > 0 && getBalance(uuid) >= NumberUtil.round(amount);
    }

    /** Schreibt einen Betrag gut. Ueber dem Maximum wird gedeckelt. */
    public boolean deposit(UUID uuid, double amount) {
        double value = NumberUtil.round(amount);
        if (value <= 0) {
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            return false;
        }
        synchronized (transferLock) {
            double max = getMaxBalance();
            double allowed = Math.min(value, Math.max(0.0D, max - data.getMoney()));
            if (allowed <= 0) {
                return false;
            }
            data.addMoney(allowed);
        }
        return true;
    }

    /** Bucht einen Betrag ab. */
    public boolean withdraw(UUID uuid, double amount) {
        double value = NumberUtil.round(amount);
        if (value <= 0) {
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            return false;
        }
        synchronized (transferLock) {
            return data.removeMoney(value);
        }
    }

    /** Setzt das Guthaben hart (Adminfunktion). */
    public void setBalance(UUID uuid, double amount) {
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            return;
        }
        synchronized (transferLock) {
            data.setMoney(NumberUtil.clamp(NumberUtil.round(amount), 0.0D, getMaxBalance()));
        }
    }

    /** Zahlt Bargeld auf das Bankkonto ein. */
    public boolean depositToBank(UUID uuid, double amount) {
        double value = NumberUtil.round(amount);
        if (value <= 0) {
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            return false;
        }
        synchronized (transferLock) {
            if (!data.removeMoney(value)) {
                return false;
            }
            data.setBank(Math.min(getMaxBalance(), data.getBank() + value));
            // Bank-Einzahlungen sind keine Ausgabe im Sinne der Statistik.
            data.setSpent(Math.max(0.0D, data.getSpent() - value));
            return true;
        }
    }

    /** Hebt Geld vom Bankkonto ab. */
    public boolean withdrawFromBank(UUID uuid, double amount) {
        double value = NumberUtil.round(amount);
        if (value <= 0) {
            return false;
        }
        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        if (data == null) {
            return false;
        }
        synchronized (transferLock) {
            if (data.getBank() < value) {
                return false;
            }
            data.setBank(data.getBank() - value);
            // Bewusst setMoney statt addMoney: eine Abhebung ist keine
            // Einnahme und darf die Statistik "verdient" nicht veraendern.
            data.setMoney(Math.min(getMaxBalance(), data.getMoney() + value));
            return true;
        }
    }

    /**
     * Ueberweist Geld von einem Spieler zum anderen.
     *
     * <p>Der gesamte Vorgang laeuft unter einer Sperre. Schlaegt die Gutschrift
     * fehl, wird die Abbuchung vollstaendig zurueckgenommen.</p>
     */
    public TransferResult transfer(UUID from, UUID to, double rawAmount) {
        double amount = NumberUtil.round(rawAmount);
        if (amount <= 0) {
            return TransferResult.INVALID_AMOUNT;
        }
        if (from == null || to == null) {
            return TransferResult.UNKNOWN_TARGET;
        }
        if (from.equals(to)) {
            return TransferResult.SAME_PLAYER;
        }
        double minimum = plugin.getConfigManager().number("economy.minimum-payment", 1.0D);
        if (amount < minimum) {
            return TransferResult.BELOW_MINIMUM;
        }

        PlayerData sender = plugin.getPlayerDataManager().get(from);
        PlayerData receiver = plugin.getPlayerDataManager().get(to);
        if (sender == null || receiver == null) {
            return TransferResult.UNKNOWN_TARGET;
        }

        synchronized (transferLock) {
            if (receiver.getMoney() + amount > getMaxBalance()) {
                return TransferResult.TARGET_LIMIT_REACHED;
            }
            if (!sender.removeMoney(amount)) {
                return TransferResult.NOT_ENOUGH_MONEY;
            }
            receiver.addMoney(amount);
        }

        plugin.getPlayerDataManager().save(sender);
        plugin.getPlayerDataManager().save(receiver);
        plugin.getQuestManager().handleMoneyEarned(to, amount);
        plugin.getAchievementManager().checkMoney(to);
        return TransferResult.SUCCESS;
    }
}
