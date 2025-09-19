package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {

    private final Path configFile;
    private final Logger baseLogger;
    private LogHelper log; // unser Log-System
    private YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.configFile = dataDirectory.resolve("resources.yaml");
        this.baseLogger = logger;
        try {
            init();
            load();
        } catch (Exception e) {
            // erster Fehler nur direkt ins Base-Logger
            baseLogger.error("Could not initialize configuration. Plugin will not be enabled.", e);
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // Initiale Erstellung mit Defaults
    // ---------------------------------------------------------------------
    private void init() throws IOException {
        if (!Files.exists(configFile)) {
            Files.createDirectories(configFile.getParent());
            Files.createFile(configFile);

            YamlConfigurationLoader saver = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();

            CommentedConfigurationNode root = saver.createNode();

            // MySQL
            root.node("mysql").comment("MySQL-Datenbankverbindung");
            root.node("mysql", "host").set("jdbc:mysql://localhost:3306/database_name");
            root.node("mysql", "user").set("username");
            root.node("mysql", "password").set("password");
            root.node("mysql", "database").set("database_name");

            Map<String, String> defaultParams = new LinkedHashMap<>();
            defaultParams.put("useUnicode", "true");
            defaultParams.put("characterEncoding", "utf8");
            defaultParams.put("serverTimezone", "UTC");
            defaultParams.put("cachePrepStmts", "true");
            defaultParams.put("tcpKeepAlive", "true");
            root.node("mysql", "params").set(defaultParams);

            // Logging & Debug
            root.node("debug").set(false);
            root.node("log", "level").set("INFO");

            // Punkte & Promotion
            root.node("points", "interval-seconds").set(60);
            root.node("points", "amount").set(1);
            root.node("points", "promotion-interval-seconds").set(60);

            // Autosave
            root.node("storage", "autosave-interval-seconds").set(300);

            // Staff
            root.node("staff", "cache-ttl-seconds").set(60);
            root.node("staff", "give-points").set(false);

            saver.save(root);

            baseLogger.warn("Config file 'resources.yaml' was created.");
            throw new IllegalStateException("Initial configuration created – setup required.");
        }
    }

    // ---------------------------------------------------------------------
    // Laden & Migration
    // ---------------------------------------------------------------------
    private void load() {
        try {
            this.loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();
            this.root = loader.load();

            // LogHelper initialisieren (immer neu bei reload)
            LogLevel level = LogLevel.fromString(root.node("log", "level").getString("INFO"));
            this.log = new LogHelper(baseLogger, level);
            log.info("Log-Level gesetzt auf {}", level);

            boolean changed = false;

            // Migration MySQL host
            String hostRaw = root.node("mysql", "host").getString("");
            String dbName = root.node("mysql", "database").getString("");
            if (!hostRaw.startsWith("jdbc:")) {
                if (dbName != null && !dbName.isBlank()) {
                    String jdbc = "jdbc:mysql://" + hostRaw + "/" + dbName;
                    root.node("mysql", "host").set(jdbc);
                    log.info("Migrated MySQL config to JDBC URL: {}", jdbc);
                    changed = true;
                } else {
                    root.node("mysql", "host").set("jdbc:mysql://localhost:3306/database_name");
                    changed = true;
                }
            }

            if (changed) {
                loader.save(root);
                log.info("Configuration 'resources.yaml' updated with missing defaults / migration.");
            }

            log.info("Configuration loaded from resources.yaml at {}", configFile.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to load configuration file: {}", e.getMessage());
            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    public void reload() {
        load();
        log.info("Configuration reloaded from resources.yaml (Log-Level: {})", getLogLevel());
    }

    // ---------------------------------------------------------------------
    // RankPointsAPI
    // ---------------------------------------------------------------------
    public PointsAPI loadAPI() {
        String jdbcUrl = root.node("mysql", "host").getString();
        String user = root.node("mysql", "user").getString();
        String password = root.node("mysql", "password").getString();
        boolean debug = root.node("debug").getBoolean(false);
        boolean givePointsToStaff = root.node("staff", "give-points").getBoolean(false);

        if (jdbcUrl == null || user == null || password == null) {
            log.warn("MySQL config is incomplete. Please check resources.yaml.");
            throw new IllegalStateException("Missing MySQL config values");
        }

        log.info("Loaded MySQL config: url={}, user={}, staffPoints={}", jdbcUrl, user, givePointsToStaff);
        java.util.logging.Logger javaLogger = java.util.logging.Logger.getLogger("RankPointsAPI");

        // excludeStaff = !givePointsToStaff
        return new PointsAPI(jdbcUrl, user, password, javaLogger, debug, !givePointsToStaff);
    }

    // ---------------------------------------------------------------------
    // Stafflist Pool
    // ---------------------------------------------------------------------
    public DataSource createStafflistDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(getJdbcUrl());
        cfg.setUsername(getJdbcUser());
        cfg.setPassword(getJdbcPassword());
        cfg.setPoolName("RankProxyPlugin-StafflistPool");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(5_000);
        cfg.setValidationTimeout(2_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setKeepaliveTime(900_000);
        cfg.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(cfg);
    }

    // ---------------------------------------------------------------------
    // Getter
    // ---------------------------------------------------------------------
    public String getJdbcUrl() {
        return root.node("mysql", "host").getString("jdbc:mysql://localhost:3306/database_name");
    }

    public String getJdbcUser() {
        return root.node("mysql", "user").getString("username");
    }

    public String getJdbcPassword() {
        return root.node("mysql", "password").getString("password");
    }

    public boolean isStaffPointsAllowed() {
        return root.node("staff", "give-points").getBoolean(false);
    }

    public boolean isDebug() {
        return root.node("debug").getBoolean(false);
    }

    public int getIntervalSeconds() {
        return root.node("points", "interval-seconds").getInt(60);
    }

    public int getPointAmount() {
        return root.node("points", "amount").getInt(1);
    }

    public int getPromotionIntervalSeconds() {
        return root.node("points", "promotion-interval-seconds").getInt(60);
    }

    public int getAutosaveIntervalSeconds() {
        return root.node("storage", "autosave-interval-seconds").getInt(300);
    }

    public int getStaffCacheTtlSeconds() {
        return root.node("staff", "cache-ttl-seconds").getInt(60);
    }

    public String getLogLevel() {
        return root.node("log", "level").getString("INFO");
    }

    public LogHelper getLogger() {
        return log;
    }

    public String getStaffGroupName() {
        return root.node("staff", "group").getString("staff"); // Default = staff
    }

    public String getDefaultGroupName() {return root.node("default", "group").getString("player");
    }
}
