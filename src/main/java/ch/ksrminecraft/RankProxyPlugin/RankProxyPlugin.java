package ch.ksrminecraft.RankProxyPlugin;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.commands.AddPointsCommand;
import ch.ksrminecraft.RankProxyPlugin.commands.GetPointsCommand;
import ch.ksrminecraft.RankProxyPlugin.commands.SetPointsCommand;
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
    private final Scheduler scheduler;
    private final PointsAPI pointsAPI;

    @Inject
    private Logger logger;

    @Inject
    public RankProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory, Scheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;

        ConfigManager config = new ConfigManager(dataDirectory, logger);
        this.pointsAPI = config.loadAPI();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getCommandManager().register("addpoints", new AddPointsCommand(server, pointsAPI));
        server.getCommandManager().register("setpoints", new SetPointsCommand(server, pointsAPI));
        server.getCommandManager().register("getpoints", new GetPointsCommand(server, pointsAPI));
        startPointTask();
    }

    private void startPointTask() {
        scheduler.buildTask(this, () -> {
            for (Player player : server.getAllPlayers()) {
                UUID uuid = player.getUniqueId();
                pointsAPI.addPoints(uuid, 1);
            }
        }).delay(1, TimeUnit.MINUTES).repeat(1, TimeUnit.MINUTES).schedule();
    }
}