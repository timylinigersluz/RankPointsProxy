package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * SchedulerManager – steuert wiederkehrende Tasks (Punkte, Promotion, Autosave).
 * Nutzt LogHelper für konsistentes Logging nach Config-Log-Level.
 */
public class SchedulerManager {

    private final ProxyServer server;
    private final Scheduler scheduler;
    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final ConfigManager config;
    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlinePlayerStore;
    private final LogHelper log;

    public SchedulerManager(ProxyServer server,
                            Scheduler scheduler,
                            PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            ConfigManager config,
                            PromotionManager promotionManager,
                            OfflinePlayerStore offlinePlayerStore,
                            LogHelper log) {
        this.server = server;
        this.scheduler = scheduler;
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.config = config;
        this.promotionManager = promotionManager;
        this.offlinePlayerStore = offlinePlayerStore;
        this.log = log;
    }

    public void startTasks(Object pluginInstance) {
        startPointTask(pluginInstance);
        startPromotionTask(pluginInstance);
        startAutosaveTask(pluginInstance);
    }

    // -------------------------------------------------------------------------
    // Punkte-Task
    // -------------------------------------------------------------------------
    private void startPointTask(Object pluginInstance) {
        int interval = config.getIntervalSeconds();
        int amount = config.getPointAmount();

        log.info("[Scheduler] Starting point reward task every {}s ({} point(s) per interval)", interval, amount);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                log.debug("[PointsTask] Running point task...");
                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    try {
                        if (stafflistManager.isStaff(uuid)) {
                            log.debug("[PointsTask] Skipped {} (staff member)", player.getUsername());
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("[PointsTask] Staff-Check für {} fehlgeschlagen – überspringe. Fehler: {}", player.getUsername(), e.getMessage());
                        continue;
                    }

                    pointsAPI.addPoints(uuid, amount);
                    log.debug("[PointsTask] Added {} point(s) to {}", amount, player.getUsername());
                }
            } catch (Throwable t) {
                log.error("[PointsTask] Unhandled exception in task", t);
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }

    // -------------------------------------------------------------------------
    // Promotion-Task
    // -------------------------------------------------------------------------
    private void startPromotionTask(Object pluginInstance) {
        int promotionInterval = config.getPromotionIntervalSeconds();

        log.info("[Scheduler] Starting promotion check task every {}s", promotionInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                log.debug("[PromotionTask] Running promotion task...");
                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    try {
                        if (stafflistManager.isStaff(uuid)) {
                            log.debug("[PromotionTask] Skipped promotion for {} (staff member)", player.getUsername());
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("[PromotionTask] Staff-Check für {} fehlgeschlagen – überspringe. Fehler: {}", player.getUsername(), e.getMessage());
                        continue;
                    }

                    promotionManager.handleLogin(uuid, player.getUsername());
                }
            } catch (Throwable t) {
                log.error("[PromotionTask] Unhandled exception", t);
            }
        }).delay(promotionInterval, TimeUnit.SECONDS).repeat(promotionInterval, TimeUnit.SECONDS).schedule();
    }

    // -------------------------------------------------------------------------
    // Autosave-Task
    // -------------------------------------------------------------------------
    private void startAutosaveTask(Object pluginInstance) {
        int autosaveInterval = config.getAutosaveIntervalSeconds();

        log.info("[Scheduler] Starting offline player autosave task every {}s", autosaveInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                offlinePlayerStore.save();
                log.debug("[AutosaveTask] OfflinePlayerStore saved to disk.");
            } catch (Throwable t) {
                log.error("[AutosaveTask] Unhandled exception while saving OfflinePlayerStore", t);
            }
        }).delay(autosaveInterval, TimeUnit.SECONDS).repeat(autosaveInterval, TimeUnit.SECONDS).schedule();
    }
}
