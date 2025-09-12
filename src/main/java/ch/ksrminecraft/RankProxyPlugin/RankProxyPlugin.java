package ch.ksrminecraft.RankProxyPlugin;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.commands.*;
import ch.ksrminecraft.RankProxyPlugin.listeners.PlayerLoginListener;
import ch.ksrminecraft.RankProxyPlugin.utils.*;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(id = "rankproxyplugin", name = "RankProxyPlugin", version = "1.0")
public class RankProxyPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Scheduler scheduler;
    private final Path dataDirectory;

    private PointsAPI pointsAPI;
    private ConfigManager config;
    private StafflistManager stafflistManager;
    private LuckPerms luckPerms;
    private RankManager rankManager;
    private PromotionManager promotionManager;
    private OfflinePlayerStore offlinePlayerStore;

    // Für sauberes Herunterfahren des Pools
    private DataSource staffDataSource;

    @Inject
    public RankProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.scheduler = server.getScheduler();
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            copyResourceIfMissing("resources.yaml");
            copyResourceIfMissing("ranks.yaml");

            this.config = new ConfigManager(dataDirectory, logger);
            this.pointsAPI = config.loadAPI();

            this.luckPerms = LuckPermsProvider.get();
            logger.info("LuckPerms API erfolgreich initialisiert.");

            // Stafflist-DataSource aus ConfigManager holen
            this.staffDataSource = config.createStafflistDataSource();

            // StafflistManager direkt mit Cache-TTL aus Config initialisieren
            int staffCacheTtl = config.getStaffCacheTtlSeconds();
            this.stafflistManager = new StafflistManager(staffDataSource, logger, staffCacheTtl);

            this.offlinePlayerStore = new OfflinePlayerStore(dataDirectory);
            this.rankManager = new RankManager(dataDirectory, logger, luckPerms);

            this.promotionManager = new PromotionManager(
                    luckPerms,
                    rankManager,
                    stafflistManager,
                    pointsAPI,
                    logger
            );

            SchedulerManager schedulerManager = new SchedulerManager(
                    server,
                    scheduler,
                    pointsAPI,
                    stafflistManager,
                    config,
                    promotionManager,
                    offlinePlayerStore,
                    logger
            );
            schedulerManager.startTasks(this);

            server.getEventManager().register(this, new PlayerLoginListener(
                    promotionManager,
                    offlinePlayerStore,
                    stafflistManager,
                    logger,
                    scheduler,
                    this
            ));

            server.getCommandManager().register("addpoints",
                    new AddPointsCommand(pointsAPI, logger, config.isDebug(), stafflistManager, offlinePlayerStore));
            server.getCommandManager().register("setpoints",
                    new SetPointsCommand(pointsAPI, stafflistManager, offlinePlayerStore));
            server.getCommandManager().register("getpoints",
                    new GetPointsCommand(pointsAPI, offlinePlayerStore));
            server.getCommandManager().register("reloadconfig",
                    new ReloadConfigCommand(config));
            server.getCommandManager().register("staffadd",
                    new StafflistAddCommand(server, stafflistManager));
            server.getCommandManager().register("staffremove",
                    new StafflistRemoveCommand(server, stafflistManager));
            server.getCommandManager().register("stafflist",
                    new StafflistListCommand(stafflistManager));
            server.getCommandManager().register("rankinfo",
                    new RankInfoCommand(pointsAPI, rankManager));

            logger.info("RankProxyPlugin erfolgreich gestartet (Rank-Sync: create-only; Staff via DB ausgeschlossen).");

        } catch (Exception e) {
            logger.error("Fehler beim Starten des Plugins", e);
        }
    }

    private void copyResourceIfMissing(String resourceName) {
        try {
            Path target = dataDirectory.resolve(resourceName);
            if (!Files.exists(target)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                    if (in == null) {
                        logger.warn("Resource '{}' nicht im JAR gefunden", resourceName);
                        return;
                    }
                    Files.createDirectories(dataDirectory);
                    Files.copy(in, target);
                    logger.info("Default {} erstellt im {}", resourceName, target.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Fehler beim Kopieren der Ressource {}", resourceName, e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (offlinePlayerStore != null) {
            logger.info("Saving offline player store...");
            offlinePlayerStore.save();
            logger.info("Offline player store saved.");
        }
        // Stafflist Hikari-Pool schließen (falls vorhanden)
        if (staffDataSource instanceof HikariDataSource hikari) {
            try {
                logger.info("Shutting down Stafflist Hikari pool...");
                hikari.close();
            } catch (Exception ex) {
                logger.warn("Error while closing Stafflist Hikari pool: {}", ex.getMessage());
            }
        }
    }

    // Getter
    public LuckPerms getLuckPerms() { return luckPerms; }
    public PointsAPI getPointsAPI() { return pointsAPI; }
    public StafflistManager getStafflistManager() { return stafflistManager; }
    public ConfigManager getConfigManager() { return config; }
    public RankManager getRankManager() { return rankManager; }
    public PromotionManager getPromotionManager() { return promotionManager; }
    public OfflinePlayerStore getOfflinePlayerStore() { return offlinePlayerStore; }
}
