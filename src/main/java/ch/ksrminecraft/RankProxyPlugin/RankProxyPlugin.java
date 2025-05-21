package ch.ksrminecraft.RankProxyPlugin;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.commands.*;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(id = "rankproxyplugin", name = "RankProxyPlugin", version = "1.0")
public class RankProxyPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Scheduler scheduler;
    private PointsAPI pointsAPI;
    private ConfigManager config;
    private StafflistManager stafflistManager;

    @Inject
    public RankProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.scheduler = server.getScheduler();

        try {
            this.config = new ConfigManager(dataDirectory, logger);
        } catch (IllegalStateException e) {
            logger.error("Plugin initialization aborted: {}", e.getMessage());
            return;
        }
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            this.pointsAPI = config.loadAPI();
            this.stafflistManager = new StafflistManager(pointsAPI.getConnection(), logger);
        } catch (Exception e) {
            logger.error("Could not load PointsAPI or initialize StafflistManager. Plugin will not be registered.", e);
            return;
        }

        server.getCommandManager().register("addpoints", new AddPointsCommand(server, pointsAPI, logger, config.isDebug(), stafflistManager));
        server.getCommandManager().register("setpoints", new SetPointsCommand(server, pointsAPI, stafflistManager));
        server.getCommandManager().register("getpoints", new GetPointsCommand(server, pointsAPI));
        server.getCommandManager().register("reloadconfig", new ReloadConfigCommand(config));
        server.getCommandManager().register("staffadd", new StafflistAddCommand(server, stafflistManager));
        server.getCommandManager().register("staffremove", new StafflistRemoveCommand(server, stafflistManager));
        server.getCommandManager().register("stafflist", new StafflistListCommand(stafflistManager));

        startPointTask();
    }

    private void startPointTask() {
        int interval = config.getIntervalSeconds();
        int amount = config.getPointAmount();

        scheduler.buildTask(this, () -> {
            for (Player player : server.getAllPlayers()) {
                UUID uuid = player.getUniqueId();

                if (stafflistManager.isStaff(uuid)) {
                    if (config.isDebug()) {
                        logger.info("[Debug] Skipped {} (staff member)", player.getUsername());
                    }
                    continue;
                }

                pointsAPI.addPoints(uuid, amount);

                if (config.isDebug()) {
                    logger.info("[Debug] Added {} point(s) to {}", amount, player.getUsername());
                }
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }
}
