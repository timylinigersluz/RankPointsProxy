package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            root.node("mysql", "host").set("localhost:3306").comment("Hostname und Port der Datenbank");
            root.node("mysql", "user").set("username").comment("Benutzername für die Datenbankverbindung");
            root.node("mysql", "password").set("password").comment("Passwort für die Datenbankverbindung");
            root.node("debug").set(false).comment("Aktiviere Debug-Ausgaben (true/false)");
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
            logger.info("Configuration loaded from resources.yaml");
            logger.info("Loading config from path: " + configFile.toAbsolutePath());
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

        if (host == null || user == null || password == null) {
            logger.warn("MySQL config is incomplete. Please check resources.yaml.");
            throw new IllegalStateException("Missing MySQL config values");
        }

        logger.info("Loaded MySQL config: host={}, user={}", host, user);
        return new PointsAPI(host, user, password);
    }

    public boolean isDebug() {
        return root.node("debug").getBoolean(false);
    }

    public CommentedConfigurationNode getRoot() {
        return root;
    }
}
