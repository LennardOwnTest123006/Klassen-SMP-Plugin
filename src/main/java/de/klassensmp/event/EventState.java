package de.klassensmp.event;

/** Lebenszyklus eines Server-Events. */
public enum EventState {

    /** Anmeldung laeuft, Countdown zaehlt herunter. */
    WAITING,
    /** Das Event laeuft. */
    RUNNING,
    /** Das Event ist beendet. */
    ENDED
}
