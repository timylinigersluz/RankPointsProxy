package ch.ksrminecraft.RankProxyPlugin.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import javax.sql.DataSource;

public class DbPoolManager {

    private final HikariDataSource dataSource;

    public DbPoolManager(String jdbcUrl, String user, String pass, Logger logger) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        // Performance/Robustheit
        cfg.setMaximumPoolSize(8);
        cfg.setMinimumIdle(2);

        cfg.setConnectionTimeout(2000);      // ms: warten auf freie Connection
        cfg.setValidationTimeout(1000);
        cfg.setIdleTimeout(600_000);         // 10 min
        cfg.setMaxLifetime(30 * 60 * 1000);  // 30 min (unter MySQL wait_timeout)
        cfg.setKeepaliveTime(5 * 60 * 1000); // 5 min

        // MySQL kann i.d.R. isValid(); kein TestQuery nötig
        // cfg.setConnectionTestQuery("SELECT 1");

        // Optionale Leakerkennung fürs Debuggen:
        // cfg.setLeakDetectionThreshold(5000);

        this.dataSource = new HikariDataSource(cfg);
        logger.info("[DbPool] HikariCP initialisiert ({}).", jdbcUrl);
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
