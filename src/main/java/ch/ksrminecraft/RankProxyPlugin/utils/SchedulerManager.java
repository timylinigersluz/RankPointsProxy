package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SchedulerManager {

    private final ProxyServer server;
    private final Scheduler scheduler;
    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final ConfigManager config;
    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlinePlayerStore;
    private final Logger logger;

    public SchedulerManager(ProxyServer server,
                            Scheduler scheduler,
                            PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            ConfigManager config,
                            PromotionManager promotionManager,
                            OfflinePlayerStore offlinePlayerStore,
                            Logger logger) {
        this.server = server;
        this.scheduler = scheduler;
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.config = config;
        this.promotionManager = promotionManager;
        this.offlinePlayerStore = offlinePlayerStore;
        this.logger = logger;
    }

    public void startTasks(Object pluginInstance) {
        startPointTask(pluginInstance);
        startPromotionTask(pluginInstance);
        startAutosaveTask(pluginInstance);
    }

    private void startPointTask(Object pluginInstance) {
        int interval = config.getIntervalSeconds();
        int amount = config.getPointAmount();

        logger.info("[Scheduler] Starting point reward task every {}s ({} point(s) per interval)", interval, amount);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                if (config.isDebug()) logger.info("[Debug] Running point task...");
                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    boolean isStaff;
                    try {
                        isStaff = stafflistManager.isStaff(uuid);
                    } catch (Exception e) {
                        logger.warn("[PointsTask] Staff-Check für {} fehlgeschlagen – überspringe.", player.getUsername(), e);
                        continue;
                    }

                    if (isStaff) {
                        if (config.isDebug()) logger.info("[Debug] Skipped {} (staff member)", player.getUsername());
                        continue;
                    }

                    pointsAPI.addPoints(uuid, amount);

                    if (config.isDebug()) {
                        logger.info("[Debug] Added {} point(s) to {}", amount, player.getUsername());
                    }
                }
            } catch (Throwable t) {
                // Verhindert, dass der Task "stirbt", falls mal was hochfliegt
                logger.error("[PointsTask] Unhandled exception in task", t);
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }

    private void startPromotionTask(Object pluginInstance) {
        int promotionInterval = config.getPromotionIntervalSeconds(); // aus ConfigManager

        logger.info("[Scheduler] Starting promotion check task every {}s", promotionInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                if (config.isDebug()) logger.info("[Debug] Running promotion task...");
                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    boolean isStaff;
                    try {
                        isStaff = stafflistManager.isStaff(uuid);
                    } catch (Exception e) {
                        logger.warn("[PromotionTask] Staff-Check für {} fehlgeschlagen – überspringe.", player.getUsername(), e);
                        continue;
                    }

                    if (isStaff) {
                        if (config.isDebug()) logger.info("[Debug] Skipped promotion for {} (staff member)", player.getUsername());
                        continue;
                    }

                    // Einheitlich: Promotion-Logik immer über handleLogin(UUID, Name)
                    promotionManager.handleLogin(uuid, player.getUsername());
                }
            } catch (Throwable t) {
                logger.error("[PromotionTask] Unhandled exception in task", t);
            }
        }).delay(promotionInterval, TimeUnit.SECONDS).repeat(promotionInterval, TimeUnit.SECONDS).schedule();
    }

    private void startAutosaveTask(Object pluginInstance) {
        int autosaveInterval = config.getAutosaveIntervalSeconds(); // aus ConfigManager

        logger.info("[Scheduler] Starting offline player autosave task every {}s", autosaveInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                offlinePlayerStore.save();
                if (config.isDebug()) logger.info("[Debug] OfflinePlayerStore saved to disk.");
            } catch (Throwable t) {
                logger.error("[AutosaveTask] Unhandled exception while saving OfflinePlayerStore", t);
            }
        }).delay(autosaveInterval, TimeUnit.SECONDS).repeat(autosaveInterval, TimeUnit.SECONDS).schedule();
    }
}
