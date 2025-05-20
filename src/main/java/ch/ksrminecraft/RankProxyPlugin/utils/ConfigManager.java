package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
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
        init();
        load();
    }

    private void init() {
        try {
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
                saver.save(root);

                logger.warn("resources.yaml created. Please edit it and restart the server.");
                System.exit(0);
            }
        } catch (IOException e) {
            logger.error("Error creating configuration file", e);
            System.exit(1);
        }
    }

    private void load() {
        try {
            this.loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();
            this.root = loader.load();
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            throw new RuntimeException(e);
        }
    }

    public void reload() {
        load();
        logger.info("Configuration reloaded from resources.yaml");
    }

    public PointsAPI loadAPI() {
        try {
            String host = root.node("mysql", "host").getString();
            String user = root.node("mysql", "user").getString();
            String password = root.node("mysql", "password").getString();

            if (host == null || user == null || password == null) {
                throw new IllegalStateException("Missing MySQL config values");
            }

            return new PointsAPI(host, user, password);
        } catch (Exception e) {
            logger.error("Failed to extract PointsAPI credentials from config", e);
            throw new RuntimeException(e);
        }
    }

    public CommentedConfigurationNode getRoot() {
        return root;
    }
}
