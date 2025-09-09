package ch.ksrminecraft.RankProxyPlugin.utils;

import org.slf4j.Logger;

public class LogHelper {
    private final Logger logger;
    private final LogLevel configuredLevel;

    public LogHelper(Logger logger, LogLevel configuredLevel) {
        this.logger = logger;
        this.configuredLevel = configuredLevel;
    }

    public void error(String msg, Object... args) {
        if (configuredLevel.ordinal() >= LogLevel.ERROR.ordinal()) {
            logger.error(msg, args);
        }
    }

    public void warn(String msg, Object... args) {
        if (configuredLevel.ordinal() >= LogLevel.WARN.ordinal()) {
            logger.warn(msg, args);
        }
    }

    public void info(String msg, Object... args) {
        if (configuredLevel.ordinal() >= LogLevel.INFO.ordinal()) {
            logger.info(msg, args);
        }
    }

    public void debug(String msg, Object... args) {
        if (configuredLevel.ordinal() >= LogLevel.DEBUG.ordinal()) {
            logger.info("[DEBUG] " + msg, args);
        }
    }

    public void trace(String msg, Object... args) {
        if (configuredLevel.ordinal() >= LogLevel.TRACE.ordinal()) {
            logger.info("[TRACE] " + msg, args);
        }
    }
}
