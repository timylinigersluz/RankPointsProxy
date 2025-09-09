package ch.ksrminecraft.RankProxyPlugin.utils;

/**
 * Enum für konfigurierbare Log-Levels im Plugin.
 * Reihenfolge bestimmt die Filterung (ordinal).
 */
public enum LogLevel {
    OFF,    // keine Logs
    ERROR,  // nur Fehler
    WARN,   // Warnungen + Fehler
    INFO,   // Standard: Info, Warnungen, Fehler
    DEBUG,  // inkl. Debug-Ausgaben
    TRACE;  // inkl. Trace-Ausgaben (sehr detailliert)

    public static LogLevel fromString(String s) {
        if (s == null) return INFO;
        try {
            return LogLevel.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO; // Fallback
        }
    }
}
