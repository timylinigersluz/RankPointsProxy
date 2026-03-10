package ch.ksrminecraft.RankProxyPlugin.utils;

import org.slf4j.Logger;

public class LogHelper {

    private final Logger logger;
    private final LogLevel configuredLevel;

    public LogHelper(Logger logger, LogLevel configuredLevel) {
        this.logger = logger;
        this.configuredLevel = configuredLevel;
    }

    public LogLevel getConfiguredLevel() {
        return configuredLevel;
    }

    public boolean isErrorEnabled() {
        return configuredLevel.allows(LogLevel.ERROR);
    }

    public boolean isWarnEnabled() {
        return configuredLevel.allows(LogLevel.WARN);
    }

    public boolean isInfoEnabled() {
        return configuredLevel.allows(LogLevel.INFO);
    }

    public boolean isDebugEnabled() {
        return configuredLevel.allows(LogLevel.DEBUG);
    }

    public boolean isTraceEnabled() {
        return configuredLevel.allows(LogLevel.TRACE);
    }

    public void error(String msg, Object... args) {
        if (isErrorEnabled()) {
            logger.error(msg, args);
        }
    }

    public void warn(String msg, Object... args) {
        if (isWarnEnabled()) {
            logger.warn(msg, args);
        }
    }

    public void info(String msg, Object... args) {
        if (isInfoEnabled()) {
            logger.info(msg, args);
        }
    }

    public void debug(String msg, Object... args) {
        if (isDebugEnabled()) {
            logger.debug(msg, args);
        }
    }

    public void trace(String msg, Object... args) {
        if (isTraceEnabled()) {
            logger.trace(msg, args);
        }
    }
}