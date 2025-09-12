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
    private final Logger logger;
    private YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.configFile = dataDirectory.resolve("resources.yaml");
        this.logger = logger;
        try {
            init();
            load();
        } catch (Exception e) {
            logger.error("Could not initialize configuration. Plugin will not be enabled.", e);
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // Initiale Erstellung mit sinnvollen Defaults (inkl. staff.cache-ttl)
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
            root.node("mysql", "host").set("jdbc:mysql://localhost:3306/database_name")
                    .comment("Komplette JDBC-URL (z.B. jdbc:mysql://host:3306/db)");
            root.node("mysql", "user").set("username").comment("DB-Benutzername");
            root.node("mysql", "password").set("password").comment("DB-Passwort");

            // Optional: frühere Struktur (wird bei load() automatisch migriert, falls vorhanden)
            root.node("mysql", "database").set("database_name")
                    .comment("Nur für Migration: wird bei Bedarf zu JDBC-URL zusammengesetzt");

            Map<String, String> defaultParams = new LinkedHashMap<>();
            defaultParams.put("useUnicode", "true");
            defaultParams.put("characterEncoding", "utf8");
            defaultParams.put("serverTimezone", "UTC");
            defaultParams.put("cachePrepStmts", "true");
            defaultParams.put("tcpKeepAlive", "true");
            root.node("mysql", "params").set(defaultParams).comment("Optionale JDBC-Parameter (werden der URL NICHT automatisch angehängt)");

            // Logging & Debug
            root.node("debug").set(false).comment("Aktiviere Debug-Ausgaben (true/false)");
            root.node("log", "level").set("INFO").comment("Log-Level: OFF, ERROR, WARN, INFO, DEBUG, TRACE");

            // Punkte & Promotion
            root.node("points", "interval-seconds").set(60).comment("Intervall für Spielzeit-Punkte");
            root.node("points", "amount").set(1).comment("Punkte pro Intervall");
            root.node("points", "promotion-interval-seconds").set(60).comment("Intervall für Promotions-Prüfung");

            // Autosave Offline-Spieler
            root.node("storage", "autosave-interval-seconds").set(300).comment("Intervall (Sek.) zum Speichern des OfflinePlayerStore");

            // Staff-Cache
            root.node("staff", "cache-ttl-seconds").set(60).comment("TTL des In-Memory-Staff-Caches");

            saver.save(root);

            logger.warn("Config file 'resources.yaml' was created.");
            logger.warn("Please edit the file to add your MySQL credentials and restart the proxy.");
            throw new IllegalStateException("Initial configuration created – setup required.");
        }
    }

    // ---------------------------------------------------------------------
    // Laden & Migration (host+database → JDBC-URL), Defaults nachziehen
    // ---------------------------------------------------------------------
    private void load() {
        try {
            this.loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();
            this.root = loader.load();

            boolean changed = false;

            // Migration: Falls mysql.host KEINE JDBC-URL ist, aus host+database zusammensetzen
            String hostRaw = root.node("mysql", "host").getString("");
            String dbName = root.node("mysql", "database").getString("");
            if (!hostRaw.startsWith("jdbc:")) {
                if (hostRaw.isBlank()) {
                    // Setze ein sinnvolles Default
                    root.node("mysql", "host").set("jdbc:mysql://localhost:3306/database_name");
                    changed = true;
                } else {
                    // hostRaw ist vermutlich "hostname:port" → JDBC bauen, falls database existiert
                    if (dbName == null || dbName.isBlank()) {
                        logger.warn("mysql.host is not a JDBC URL and mysql.database is missing. Using fallback default.");
                        root.node("mysql", "host").set("jdbc:mysql://localhost:3306/database_name");
                        changed = true;
                    } else {
                        String jdbc = "jdbc:mysql://" + hostRaw + "/" + dbName;
                        root.node("mysql", "host").set(jdbc);
                        logger.info("Migrated MySQL config to JDBC URL: {}", jdbc);
                        changed = true;
                    }
                }
            }

            // Sicherstellen, dass Punkte/Promotion/Autosave vorhanden sind
            if (root.node("points", "interval-seconds").virtual()) {
                root.node("points", "interval-seconds").set(60);
                changed = true;
            }
            if (root.node("points", "amount").virtual()) {
                root.node("points", "amount").set(1);
                changed = true;
            }
            if (root.node("points", "promotion-interval-seconds").virtual()) {
                root.node("points", "promotion-interval-seconds").set(60);
                changed = true;
            }
            if (root.node("storage", "autosave-interval-seconds").virtual()) {
                root.node("storage", "autosave-interval-seconds").set(300);
                changed = true;
            }
            if (root.node("staff", "cache-ttl-seconds").virtual()) {
                root.node("staff", "cache-ttl-seconds").set(60);
                changed = true;
            }
            if (root.node("log", "level").virtual()) {
                root.node("log", "level").set("INFO");
                changed = true;
            }

            if (changed) {
                loader.save(root);
                logger.info("Configuration 'resources.yaml' updated with missing defaults / migration.");
            }

            logger.info("Configuration loaded from resources.yaml at {}", configFile.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            throw new RuntimeException("Configuration loading failed", e);
        }
    }

    public void reload() {
        load();
        logger.info("Configuration reloaded from resources.yaml");
    }

    // ---------------------------------------------------------------------
    // RankPointsAPI-Initialisierung (host enthält die JDBC-URL)
    // ---------------------------------------------------------------------
    public PointsAPI loadAPI() {
        String jdbcUrl = root.node("mysql", "host").getString();
        String user = root.node("mysql", "user").getString();
        String password = root.node("mysql", "password").getString();
        boolean debug = root.node("debug").getBoolean(false);

        if (jdbcUrl == null || user == null || password == null) {
            logger.warn("MySQL config is incomplete. Please check resources.yaml.");
            throw new IllegalStateException("Missing MySQL config values");
        }

        logger.info("Loaded MySQL config: url={}, user={}", jdbcUrl, user);
        java.util.logging.Logger javaLogger = java.util.logging.Logger.getLogger("RankPointsAPI");
        return new PointsAPI(jdbcUrl, user, password, javaLogger, debug);
    }

    // ---------------------------------------------------------------------
    // DataSource für StafflistManager (eigener kleiner Pool)
    // ---------------------------------------------------------------------
    public DataSource createStafflistDataSource() {
        String url = getJdbcUrl();
        String user = getJdbcUser();
        String pass = getJdbcPassword();

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        cfg.setPoolName("RankProxyPlugin-StafflistPool");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);

        cfg.setConnectionTimeout(5_000);
        cfg.setValidationTimeout(2_000);
        cfg.setIdleTimeout(600_000);         // 10 min
        cfg.setMaxLifetime(1_800_000);       // 30 min
        cfg.setKeepaliveTime(900_000);       // 15 min

        cfg.setConnectionTestQuery("SELECT 1");

        // Nützliche MySQL-Properties
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");
        cfg.addDataSourceProperty("useUnicode", "true");
        cfg.addDataSourceProperty("characterEncoding", "utf8");

        return new HikariDataSource(cfg);
    }

    // ---------------------------------------------------------------------
    // Hilfs-Getter für JDBC-Config
    // ---------------------------------------------------------------------
    public String getJdbcUrl() {
        String host = root.node("mysql", "host").getString();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("mysql.host is missing");
        }
        // ab jetzt erwarten wir immer eine JDBC-URL
        if (!host.startsWith("jdbc:")) {
            // Fallback (sollte durch Migration nicht vorkommen)
            String database = root.node("mysql", "database").getString("database_name");
            return "jdbc:mysql://" + host + "/" + database;
        }
        return host;
    }

    public String getJdbcUser() {
        String user = root.node("mysql", "user").getString();
        if (user == null) throw new IllegalStateException("mysql.user is missing");
        return user;
    }

    public String getJdbcPassword() {
        String password = root.node("mysql", "password").getString();
        if (password == null) throw new IllegalStateException("mysql.password is missing");
        return password;
    }

    // ---------------------------------------------------------------------
    // Weitere Getter (inkl. neue Keys)
    // ---------------------------------------------------------------------
    public boolean isDebug() {
        return root.node("debug").getBoolean(false);
    }

    public CommentedConfigurationNode getRoot() {
        return root;
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
}
