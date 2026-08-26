package de.klassensmp.economy;

/** Ergebnis einer Geldueberweisung. */
public enum TransferResult {

    SUCCESS,
    INVALID_AMOUNT,
    BELOW_MINIMUM,
    NOT_ENOUGH_MONEY,
    UNKNOWN_TARGET,
    SAME_PLAYER,
    TARGET_LIMIT_REACHED
}
