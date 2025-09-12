package ch.ksrminecraft.RankProxyPlugin.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;

public class DbPoolManager {

    private final HikariDataSource dataSource;

    public DbPoolManager(String jdbcUrl, String user, String pass, Logger logger) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        // Pool-Name für bessere Logs / Metriken
        cfg.setPoolName("RankProxyPlugin-Hikari");

        // Performance/Robustheit
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);

        // Warten auf freie Connection / Validierungs-Timeout
        cfg.setConnectionTimeout(5_000);     // ms
        cfg.setValidationTimeout(2_000);     // ms

        // Lebenszyklen (Hikari soll VOR MySQL erneuern)
        cfg.setIdleTimeout(600_000);         // 10 min
        cfg.setMaxLifetime(1_800_000);       // 30 min
        cfg.setKeepaliveTime(900_000);       // 15 min

        // MySQL-Validierung (robust & portabel)
        cfg.setConnectionTestQuery("SELECT 1");

        // Sinnvolle MySQL-Properties (optional, aber oft hilfreich)
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");
        cfg.addDataSourceProperty("useUnicode", "true");
        cfg.addDataSourceProperty("characterEncoding", "utf8");
        // SSL je nach Host anpassen; hier nichts erzwingen.

        // Optional: Leak-Detection (nur für Debug, sonst auslassen)
        // cfg.setLeakDetectionThreshold(10_000);

        this.dataSource = new HikariDataSource(cfg);
        logger.info("[DbPool] HikariCP initialisiert ({}).", jdbcUrl);

        // Kurztest (führt auch sofortige Fehlkonfigurationen ans Licht)
        try (Connection c = this.dataSource.getConnection()) {
            if (!c.isValid(2)) {
                logger.warn("[DbPool] Testverbindung ist nicht gültig.");
            }
        } catch (Exception e) {
            logger.error("[DbPool] Konnte Testverbindung nicht öffnen: {}", e.getMessage(), e);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
