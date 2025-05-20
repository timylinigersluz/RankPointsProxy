package ch.ksrminecraft.RankProxyPlugin;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.commands.AddPointsCommand;
import ch.ksrminecraft.RankProxyPlugin.commands.GetPointsCommand;
import ch.ksrminecraft.RankProxyPlugin.commands.SetPointsCommand;
import ch.ksrminecraft.RankProxyPlugin.commands.ReloadConfigCommand;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;

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

@Plugin(id = "rangproxyplugin", name = "RangProxyPlugin", version = "1.0")
public class RankProxyPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private Scheduler scheduler;
    private PointsAPI pointsAPI;
    private ConfigManager config;

    @Inject
    public RankProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.scheduler = server.getScheduler();

        try {
            this.config = new ConfigManager(dataDirectory, logger);
        } catch (IllegalStateException e) {
            logger.error("Plugin initialization aborted: {}", e.getMessage());
            // Konfiguration wurde neu erstellt – Plugin startet nicht weiter
            return;
        }
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            this.pointsAPI = config.loadAPI();
        } catch (Exception e) {
            logger.error("Could not load PointsAPI. Plugin will not be registered.", e);
            return;
        }

        server.getCommandManager().register("addpoints", new AddPointsCommand(server, pointsAPI, logger, config.isDebug()));
        server.getCommandManager().register("setpoints", new SetPointsCommand(server, pointsAPI));
        server.getCommandManager().register("getpoints", new GetPointsCommand(server, pointsAPI));
        server.getCommandManager().register("reloadconfig", new ReloadConfigCommand(config));
        startPointTask();
    }

    private void startPointTask() {
        scheduler.buildTask(this, () -> {
            for (Player player : server.getAllPlayers()) {
                UUID uuid = player.getUniqueId();
                pointsAPI.addPoints(uuid, 1);

                if (config.isDebug()) {
                    logger.info("[Debug] Added 1 point to {}", player.getUsername());
                }
            }
        }).delay(1, TimeUnit.MINUTES).repeat(1, TimeUnit.MINUTES).schedule();
    }
}