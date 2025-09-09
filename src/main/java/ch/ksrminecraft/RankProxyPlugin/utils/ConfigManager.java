package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

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

    private void init() throws IOException {
        if (!Files.exists(configFile)) {
            Files.createDirectories(configFile.getParent());
            Files.createFile(configFile);

            YamlConfigurationLoader saver = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();

            CommentedConfigurationNode root = saver.createNode();

            root.node("mysql").comment("MySQL-Datenbankverbindung");
            root.node("mysql", "host").set("localhost:3306").comment("Hostname:Port oder komplette JDBC-URL (jdbc:mysql://host:3306/db)");
            root.node("mysql", "database").set("database_name").comment("Datenbankname (entfällt, wenn host bereits JDBC-URL ist)");
            root.node("mysql", "user").set("username").comment("Benutzername für die Datenbankverbindung");
            root.node("mysql", "password").set("password").comment("Passwort für die Datenbankverbindung");

            Map<String, String> defaultParams = new LinkedHashMap<>();
            defaultParams.put("useUnicode", "true");
            defaultParams.put("characterEncoding", "utf8");
            defaultParams.put("serverTimezone", "UTC");
            defaultParams.put("cachePrepStmts", "true");
            defaultParams.put("tcpKeepAlive", "true");
            root.node("mysql", "params").set(defaultParams).comment("Optionale JDBC-Parameter");

            root.node("debug").set(false).comment("Aktiviere Debug-Ausgaben (true/false)");
            root.node("log", "level").set("INFO").comment("Log-Level: OFF, ERROR, WARN, INFO, DEBUG, TRACE");

            root.node("points", "interval-seconds").set(60);
            root.node("points", "amount").set(1);

            saver.save(root);

            logger.warn("Config file 'resources.yaml' was created.");
            logger.warn("Please edit the file to add your MySQL credentials and restart the proxy.");
            throw new IllegalStateException("Initial configuration created – setup required.");
        }
    }

    private void load() {
        try {
            this.loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();
            this.root = loader.load();

            boolean changed = false;
            if (root.node("mysql", "database").virtual()) {
                root.node("mysql", "database").set("database_name");
                changed = true;
            }
            if (root.node("mysql", "params").virtual()) {
                Map<String, String> defaultParams = new LinkedHashMap<>();
                defaultParams.put("useUnicode", "true");
                defaultParams.put("characterEncoding", "utf8");
                defaultParams.put("serverTimezone", "UTC");
                defaultParams.put("cachePrepStmts", "true");
                defaultParams.put("tcpKeepAlive", "true");
                root.node("mysql", "params").set(defaultParams);
                changed = true;
            }
            if (root.node("log", "level").virtual()) {
                root.node("log", "level").set("INFO");
                changed = true;
            }
            if (changed) {
                loader.save(root);
                logger.info("Configuration 'resources.yaml' updated with missing defaults.");
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

    public PointsAPI loadAPI() {
        String host = root.node("mysql", "host").getString();
        String user = root.node("mysql", "user").getString();
        String password = root.node("mysql", "password").getString();
        boolean debug = root.node("debug").getBoolean(false);

        if (host == null || user == null || password == null) {
            logger.warn("MySQL config is incomplete. Please check resources.yaml.");
            throw new IllegalStateException("Missing MySQL config values");
        }

        logger.info("Loaded MySQL config: host={}, user={}", host, user);
        java.util.logging.Logger javaLogger = java.util.logging.Logger.getLogger("RankPointsAPI");
        return new PointsAPI(host, user, password, javaLogger, debug);
    }

    // === Stafflist DataSource für StafflistManager ===
    public DataSource createStafflistDataSource() {
        String url = getJdbcUrl();
        String user = getJdbcUser();
        String pass = getJdbcPassword();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(pass);
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setPoolName("StafflistPool");

        return new HikariDataSource(hikariConfig);
    }

    // === Hilfs-Getter für JDBC-Config ===
    public String getJdbcUrl() {
        String host = root.node("mysql", "host").getString();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("mysql.host is missing");
        }
        if (host.startsWith("jdbc:")) {
            return host;
        }
        String database = root.node("mysql", "database").getString();
        if (database == null || database.isBlank()) {
            throw new IllegalStateException("mysql.database is missing");
        }
        return "jdbc:mysql://" + host + "/" + database;
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

    // === Weitere Getter ===
    public boolean isDebug() { return root.node("debug").getBoolean(false); }
    public CommentedConfigurationNode getRoot() { return root; }
    public int getIntervalSeconds() { return root.node("points", "interval-seconds").getInt(60); }
    public int getPointAmount() { return root.node("points", "amount").getInt(1); }
}
