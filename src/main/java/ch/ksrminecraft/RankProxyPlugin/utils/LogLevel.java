package ch.ksrminecraft.RankProxyPlugin.utils;

/**
 * Enum für konfigurierbare Log-Levels im Plugin.
 * Die Reihenfolge bestimmt die Filterung über ordinal().
 */
public enum LogLevel {
    OFF,    // keine Logs
    ERROR,  // nur Fehler
    WARN,   // Warnungen + Fehler
    INFO,   // Info, Warnungen, Fehler
    DEBUG,  // zusätzlich Debug-Ausgaben
    TRACE;  // zusätzlich sehr detaillierte Trace-Ausgaben

    public static LogLevel fromString(String s) {
        if (s == null) {
            return INFO;
        }

        try {
            return LogLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO; // Fallback
        }
    }

    /**
     * Prüft, ob das aktuell konfigurierte Level das angefragte Level einschliesst.
     */
    public boolean allows(LogLevel requestedLevel) {
        if (this == OFF) {
            return false;
        }
        return this.ordinal() >= requestedLevel.ordinal();
    }
}