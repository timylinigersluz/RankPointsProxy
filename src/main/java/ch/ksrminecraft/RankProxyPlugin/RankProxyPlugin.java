package ch.ksrminecraft.RankProxyPlugin;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.commands.*;
import ch.ksrminecraft.RankProxyPlugin.listeners.*;
import ch.ksrminecraft.RankProxyPlugin.utils.*;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.Scheduler;

import com.zaxxer.hikari.HikariDataSource;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private LogHelper log;
    private StaffPermissionService staffPermissionService;
    private PendingStaffEventStore pendingStaffEventStore;

    private AfkManager afkManager;
    private PresenceManager presenceManager;

    private DataSource premiumVanishDataSource;
    private PremiumVanishHook premiumVanishHook;

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

            LogLevel configuredLevel = LogLevel.fromString(config.getLogLevel());
            this.log = new LogHelper(baseLogger, configuredLevel);
            log.info("Aktuelles Log-Level (aus resources.yaml): {}", configuredLevel);

            this.pointsAPI = config.loadAPI();

            this.luckPerms = LuckPermsProvider.get();
            log.info("LuckPerms API erfolgreich initialisiert.");

            this.staffDataSource = config.createStafflistDataSource();
            int staffCacheTtl = config.getStaffCacheTtlSeconds();
            this.stafflistManager = new StafflistManager(staffDataSource, log, staffCacheTtl);

            this.presenceManager = new PresenceManager(staffDataSource, log);
            this.pendingStaffEventStore = new PendingStaffEventStore();

            if (config.isPremiumVanishEnabled()) {
                this.premiumVanishDataSource = config.createPremiumVanishDataSource();
                this.premiumVanishHook = new PremiumVanishHook(
                        premiumVanishDataSource,
                        log,
                        config.getPremiumVanishTable()
                );

                premiumVanishHook.refreshNow();

                int refreshSeconds = Math.max(2, config.getPremiumVanishRefreshSeconds());
                log.info("[PremiumVanish] Enabled. Refresh every {}s (table={}).",
                        refreshSeconds, config.getPremiumVanishTable());

                scheduler.buildTask(this, () -> {
                            try {
                                premiumVanishHook.refreshNow();

                                server.getAllPlayers().forEach(p -> {
                                    UUID uuid = p.getUniqueId();
                                    String name = p.getUsername();

                                    boolean vanished = premiumVanishHook.isVanished(uuid);
                                    if (vanished) {
                                        presenceManager.forceHidden(uuid, name);
                                        if (afkManager != null) {
                                            afkManager.setAfk(uuid, false);
                                        }
                                    } else {
                                        String srv = p.getCurrentServer()
                                                .map(cs -> cs.getServerInfo().getName())
                                                .orElse(null);
                                        presenceManager.markOnline(uuid, name, srv);
                                    }
                                });
                            } catch (Throwable t) {
                                log.warn("[PremiumVanish] Refresh/reconcile failed: {}", t.getMessage());
                            }
                        }).delay(refreshSeconds, TimeUnit.SECONDS)
                        .repeat(refreshSeconds, TimeUnit.SECONDS)
                        .schedule();
            } else {
                log.info("[PremiumVanish] Disabled (premiumvanish.enabled=false).");
            }

            this.offlinePlayerStore = new OfflinePlayerStore(dataDirectory, log);
            this.rankManager = new RankManager(dataDirectory, log, luckPerms);

            this.promotionManager = new PromotionManager(
                    luckPerms,
                    rankManager,
                    stafflistManager,
                    pointsAPI,
                    log,
                    config.getDefaultTrackName(),
                    server,
                    scheduler,
                    this
            );

            List<String> playerTrackGroups = loadPlayerTrackGroups();
            this.staffPermissionService = new StaffPermissionService(
                    luckPerms,
                    log,
                    playerTrackGroups,
                    config.getStaffTrackName(),
                    config.getDefaultTrackName()
            );

            log.info("StaffPermissionService initialisiert. Player-Track aus ranks.yaml: {}", playerTrackGroups);

            this.afkManager = new AfkManager();
            MinecraftChannelIdentifier afkChannel = MinecraftChannelIdentifier.from("rankproxy:afk");
            server.getChannelRegistrar().register(afkChannel);

            server.getEventManager().register(
                    this,
                    new AfkMessageListener(server, afkManager, baseLogger, presenceManager, premiumVanishHook)
            );
            log.info("AFK-System aktiviert (Channel: rankproxy:afk).");

            SchedulerManager schedulerManager = new SchedulerManager(
                    server,
                    scheduler,
                    pointsAPI,
                    stafflistManager,
                    config,
                    promotionManager,
                    staffPermissionService,
                    pendingStaffEventStore,
                    offlinePlayerStore,
                    log,
                    afkManager
            );
            schedulerManager.startTasks(this);

            server.getEventManager().register(this, new PlayerLoginListener(
                    promotionManager,
                    offlinePlayerStore,
                    stafflistManager,
                    staffPermissionService,
                    pendingStaffEventStore,
                    log,
                    scheduler,
                    this,
                    presenceManager,
                    premiumVanishHook
            ));

            server.getEventManager().register(this, new PlayerDisconnectListener(
                    presenceManager,
                    afkManager,
                    log
            ));

            syncStaffGroupOnStartup();

            server.getCommandManager().register("addpoints",
                    new AddPointsCommand(
                            server,
                            luckPerms,
                            pointsAPI,
                            stafflistManager,
                            offlinePlayerStore,
                            config,
                            promotionManager,
                            log
                    )
            );

            server.getCommandManager().register("setpoints",
                    new SetPointsCommand(
                            server,
                            luckPerms,
                            pointsAPI,
                            stafflistManager,
                            offlinePlayerStore,
                            config,
                            promotionManager,
                            log
                    )
            );

            server.getCommandManager().register("getpoints",
                    new GetPointsCommand(
                            server,
                            luckPerms,
                            pointsAPI,
                            offlinePlayerStore,
                            stafflistManager,
                            config,
                            log
                    )
            );

            server.getCommandManager().register("rankproxyreload",
                    new ReloadConfigCommand(config, log)
            );

            server.getCommandManager().register(
                    "staffadd",
                    new StafflistAddCommand(
                            server,
                            stafflistManager,
                            staffPermissionService,
                            pendingStaffEventStore,
                            config,
                            baseLogger,
                            scheduler,
                            this
                    )
            );

            server.getCommandManager().register(
                    "staffremove",
                    new StafflistRemoveCommand(
                            server,
                            stafflistManager,
                            staffPermissionService,
                            pendingStaffEventStore,
                            config,
                            baseLogger,
                            scheduler,
                            this
                    )
            );

            server.getCommandManager().register("stafflist",
                    new StafflistListCommand(
                            stafflistManager,
                            config,
                            baseLogger
                    )
            );

            server.getCommandManager().register("rankinfo",
                    new RankInfoCommand(
                            server,
                            pointsAPI,
                            rankManager,
                            stafflistManager,
                            config,
                            log,
                            luckPerms
                    )
            );

            log.info("RankProxyPlugin erfolgreich gestartet (inkl. AFK, Rank-Sync, Presence-Tracking, PremiumVanish optional).");

        } catch (Exception e) {
            baseLogger.error("Fehler beim Starten des Plugins", e);
        }
    }

    private void syncStaffGroupOnStartup() {
        log.info("Prüfe Staff-Mitglieder beim Start auf korrekte Staff-Laufbahn...");

        for (Map.Entry<String, String> entry : stafflistManager.getAllStaff().entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                String name = entry.getValue();

                StaffPermissionService.PermissionSyncResult result =
                        staffPermissionService.promoteToStaff(uuid, name);

                if (!result.success()) {
                    log.warn("StaffSync: Synchronisation für {} ({}) fehlgeschlagen.", name, uuid);
                } else if (result.changed()) {
                    log.info("StaffSync: {} ({}) erfolgreich auf Staff-Laufbahn synchronisiert.", name, uuid);
                } else {
                    log.debug("StaffSync: {} ({}) war bereits korrekt in der Staff-Laufbahn.", name, uuid);
                }
            } catch (Exception e) {
                log.error("StaffSync: Fehler beim Sync für {}: {}", entry.getValue(), entry.getKey(), e.getMessage());
            }
        }
    }

    private List<String> loadPlayerTrackGroups() {
        List<String> ranks = new ArrayList<>();
        Path ranksFile = dataDirectory.resolve("ranks.yaml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(ranksFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            for (CommentedConfigurationNode rankNode : root.node("ranks").childrenList()) {
                String name = rankNode.node("name").getString();
                if (name != null && !name.isBlank()) {
                    ranks.add(name);
                }
            }
        } catch (Exception e) {
            log.error("Fehler beim Laden der Player-Laufbahn aus ranks.yaml", e);
        }

        return ranks;
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

        if (staffDataSource instanceof HikariDataSource hikari) {
            try {
                log.info("Shutting down Stafflist/Presence Hikari pool...");
                hikari.close();
            } catch (Exception ex) {
                log.warn("Error while closing Stafflist/Presence Hikari pool: {}", ex.getMessage());
            }
        }

        if (premiumVanishDataSource instanceof HikariDataSource hikari) {
            try {
                log.info("Shutting down PremiumVanish Hikari pool...");
                hikari.close();
            } catch (Exception ex) {
                log.warn("Error while closing PremiumVanish pool: {}", ex.getMessage());
            }
        }
    }

    public LuckPerms getLuckPerms() { return luckPerms; }
    public PointsAPI getPointsAPI() { return pointsAPI; }
    public StafflistManager getStafflistManager() { return stafflistManager; }
    public ConfigManager getConfigManager() { return config; }
    public RankManager getRankManager() { return rankManager; }
    public PromotionManager getPromotionManager() { return promotionManager; }
    public OfflinePlayerStore getOfflinePlayerStore() { return offlinePlayerStore; }
    public LogHelper getLog() { return log; }
    public AfkManager getAfkManager() { return afkManager; }
    public PresenceManager getPresenceManager() { return presenceManager; }
    public PremiumVanishHook getPremiumVanishHook() { return premiumVanishHook; }
    public StaffPermissionService getStaffPermissionService() { return staffPermissionService; }
    public PendingStaffEventStore getPendingStaffEventStore() { return pendingStaffEventStore; }
}