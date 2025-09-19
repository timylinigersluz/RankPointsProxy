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
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import org.slf4j.Logger;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Plugin(id = "rankproxyplugin", name = "RankProxyPlugin", version = "1.0")
public class RankProxyPlugin {

    private final ProxyServer server;
    private final Logger baseLogger;
    private final Scheduler scheduler;
    private final Path dataDirectory;

    private PointsAPI pointsAPI;
    private ConfigManager config;
    private StafflistManager stafflistManager;
    private LuckPerms luckPerms;
    private RankManager rankManager;
    private PromotionManager promotionManager;
    private OfflinePlayerStore offlinePlayerStore;

    private LogHelper log; // eigenes Log-System

    // Für sauberes Herunterfahren des Pools
    private DataSource staffDataSource;

    @Inject
    public RankProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) {
        this.server = server;
        this.baseLogger = logger;
        this.scheduler = server.getScheduler();
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            copyResourceIfMissing("resources.yaml");
            copyResourceIfMissing("ranks.yaml");

            this.config = new ConfigManager(dataDirectory, baseLogger);

            // LogHelper anhand des Levels aus der Config initialisieren
            LogLevel configuredLevel = LogLevel.fromString(config.getLogLevel());
            this.log = new LogHelper(baseLogger, configuredLevel);
            log.info("Aktuelles Log-Level (aus resources.yaml): {}", configuredLevel);

            this.pointsAPI = config.loadAPI();

            this.luckPerms = LuckPermsProvider.get();
            log.info("LuckPerms API erfolgreich initialisiert.");

            // Stafflist-DataSource aus ConfigManager holen
            this.staffDataSource = config.createStafflistDataSource();

            // StafflistManager direkt mit Cache-TTL aus Config initialisieren
            int staffCacheTtl = config.getStaffCacheTtlSeconds();
            this.stafflistManager = new StafflistManager(staffDataSource, log, staffCacheTtl);

            this.offlinePlayerStore = new OfflinePlayerStore(dataDirectory, log);
            this.rankManager = new RankManager(dataDirectory, log, luckPerms);

            this.promotionManager = new PromotionManager(
                    luckPerms,
                    rankManager,
                    stafflistManager,
                    pointsAPI,
                    log,
                    config.getStaffGroupName(),
                    config.getDefaultGroupName()
            );

            SchedulerManager schedulerManager = new SchedulerManager(
                    server,
                    scheduler,
                    pointsAPI,
                    stafflistManager,
                    config,
                    promotionManager,
                    offlinePlayerStore,
                    log
            );
            schedulerManager.startTasks(this);

            server.getEventManager().register(this, new PlayerLoginListener(
                    promotionManager,
                    offlinePlayerStore,
                    stafflistManager,
                    log,
                    scheduler,
                    this,
                    luckPerms,
                    config
            ));

            // Staff-Gruppe syncen
            syncStaffGroupOnStartup();

            // -------------------------
            // Commands registrieren
            // -------------------------
            server.getCommandManager().register("addpoints",
                    new AddPointsCommand(server, luckPerms, pointsAPI, stafflistManager, offlinePlayerStore, config, log));

            server.getCommandManager().register("setpoints",
                    new SetPointsCommand(server, luckPerms, pointsAPI, stafflistManager, offlinePlayerStore, config, log));

            server.getCommandManager().register("getpoints",
                    new GetPointsCommand(server, luckPerms, pointsAPI, offlinePlayerStore, stafflistManager, config, log));

            server.getCommandManager().register("rankproxyreload",
                    new ReloadConfigCommand(config, log));

            server.getCommandManager().register("staffadd",
                    new StafflistAddCommand(server, stafflistManager, config, baseLogger, luckPerms));

            server.getCommandManager().register("staffremove",
                    new StafflistRemoveCommand(server, stafflistManager, config, baseLogger));

            server.getCommandManager().register("stafflist",
                    new StafflistListCommand(stafflistManager, config, baseLogger));

            server.getCommandManager().register("rankinfo",
                    new RankInfoCommand(server, pointsAPI, rankManager, stafflistManager, config, log, luckPerms));

            log.info("RankProxyPlugin erfolgreich gestartet (Rank-Sync: create-only; Staff via DB + LuckPerms-Sync).");

        } catch (Exception e) {
            baseLogger.error("Fehler beim Starten des Plugins", e);
        }
    }

    private void syncStaffGroupOnStartup() {
        String staffGroup = config.getStaffGroupName();
        log.info("Prüfe Staff-Mitglieder auf LuckPerms-Gruppe '{}'", staffGroup);

        for (Map.Entry<String, String> entry : stafflistManager.getAllStaff().entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                String name = entry.getValue();

                User user = luckPerms.getUserManager().loadUser(uuid).join();
                if (user == null) {
                    log.warn("StaffSync: Konnte User {} ({}) nicht laden.", name, uuid);
                    continue;
                }

                boolean hasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(n -> n.getGroupName().equalsIgnoreCase(staffGroup));

                if (!hasGroup) {
                    user.data().add(InheritanceNode.builder(staffGroup).build());
                    luckPerms.getUserManager().saveUser(user);
                    log.info("StaffSync: {} ({}) zur Gruppe '{}' hinzugefügt.", name, uuid, staffGroup);
                }
            } catch (Exception e) {
                log.error("StaffSync: Fehler beim Sync für {}: {}", entry.getValue(), entry.getKey(), e.getMessage());
            }
        }
    }

    private void copyResourceIfMissing(String resourceName) {
        try {
            Path target = dataDirectory.resolve(resourceName);
            if (!Files.exists(target)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                    if (in == null) {
                        baseLogger.warn("Resource '{}' nicht im JAR gefunden", resourceName);
                        return;
                    }
                    Files.createDirectories(dataDirectory);
                    Files.copy(in, target);
                    baseLogger.info("Default {} erstellt im {}", resourceName, target.toAbsolutePath());
                }
            }
        } catch (IOException e) {
            baseLogger.error("Fehler beim Kopieren der Ressource {}", resourceName, e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (offlinePlayerStore != null) {
            log.info("Saving offline player store...");
            offlinePlayerStore.save();
            log.info("Offline player store saved.");
        }
        // Stafflist Hikari-Pool schließen (falls vorhanden)
        if (staffDataSource instanceof HikariDataSource hikari) {
            try {
                log.info("Shutting down Stafflist Hikari pool...");
                hikari.close();
            } catch (Exception ex) {
                log.warn("Error while closing Stafflist Hikari pool: {}", ex.getMessage());
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
    public LogHelper getLog() { return log; }
}
