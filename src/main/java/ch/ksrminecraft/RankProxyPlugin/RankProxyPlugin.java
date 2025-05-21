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
            // Grundkomponenten initialisieren
            this.config = new ConfigManager(dataDirectory, logger);
            this.pointsAPI = config.loadAPI();
            this.stafflistManager = new StafflistManager(pointsAPI.getConnection(), logger);
            this.luckPerms = LuckPermsProvider.get();
            this.offlinePlayerStore = new OfflinePlayerStore(dataDirectory);

            logger.info("LuckPerms API erfolgreich initialisiert.");

            // Ränge und Beförderungslogik
            this.rankManager = new RankManager(dataDirectory, logger, luckPerms);
            this.rankManager.syncRanksWithLuckPerms();

            this.promotionManager = new PromotionManager(pointsAPI, rankManager, luckPerms, logger, server);

            // Hintergrundtasks starten
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

            // Event Listener registrieren
            server.getEventManager().register(this, new PlayerLoginListener(promotionManager, offlinePlayerStore, logger, scheduler, this));

            // Befehle registrieren
            server.getCommandManager().register("addpoints",
                    new AddPointsCommand(pointsAPI, logger, config.isDebug(), stafflistManager, offlinePlayerStore));
            server.getCommandManager().register("setpoints",
                    new SetPointsCommand(pointsAPI, stafflistManager, offlinePlayerStore));
            server.getCommandManager().register("getpoints",
                    new GetPointsCommand(pointsAPI, offlinePlayerStore));
            server.getCommandManager().register("reloadconfig", new ReloadConfigCommand(config));
            server.getCommandManager().register("staffadd", new StafflistAddCommand(server, stafflistManager));
            server.getCommandManager().register("staffremove", new StafflistRemoveCommand(server, stafflistManager));
            server.getCommandManager().register("stafflist", new StafflistListCommand(stafflistManager));
            server.getCommandManager().register("rankinfo", new RankInfoCommand(pointsAPI, rankManager));

            logger.info("RankProxyPlugin erfolgreich gestartet.");

        } catch (Exception e) {
            logger.error("Fehler beim Starten des Plugins", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (offlinePlayerStore != null) {
            logger.info("Saving offline player store...");
            offlinePlayerStore.save();
            logger.info("Offline player store saved.");
        }
    }

    // Getter
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public PointsAPI getPointsAPI() {
        return pointsAPI;
    }

    public StafflistManager getStafflistManager() {
        return stafflistManager;
    }

    public ConfigManager getConfigManager() {
        return config;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public PromotionManager getPromotionManager() {
        return promotionManager;
    }

    public OfflinePlayerStore getOfflinePlayerStore() {
        return offlinePlayerStore;
    }
}
