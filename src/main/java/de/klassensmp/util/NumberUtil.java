package de.klassensmp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Zahl- und Waehrungsformatierung sowie sicheres Parsen von Betraegen. */
public final class NumberUtil {

    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat PLAIN_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        MONEY_FORMAT = new DecimalFormat("#,##0.00", symbols);
        PLAIN_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private NumberUtil() {
    }

    public static String formatMoney(double amount) {
        synchronized (MONEY_FORMAT) {
            return MONEY_FORMAT.format(round(amount));
        }
    }

    public static String formatNumber(long value) {
        synchronized (PLAIN_FORMAT) {
            return PLAIN_FORMAT.format(value);
        }
    }

    public static String formatTps(double tps) {
        return String.format(Locale.GERMANY, "%.2f", Math.min(20.0D, Math.max(0.0D, tps)));
    }

    /** Rundet auf zwei Nachkommastellen - verhindert Rundungs-Exploits in der Economy. */
    public static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Parst einen Geldbetrag aus Nutzereingabe.
     *
     * @return positiver, gerundeter Betrag oder -1 bei ungueltiger Eingabe.
     */
    public static double parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String normalized = input.trim().replace(",", ".").replace("_", "");
        if (normalized.length() > 20) {
            return -1;
        }
        double value;
        try {
            value = Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
        if (!Double.isFinite(value) || value <= 0) {
            return -1;
        }
        return round(value);
    }

    /** Parst eine positive Ganzzahl, sonst {@code fallback}. */
    public static int parseInt(String input, int fallback) {
        if (input == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
