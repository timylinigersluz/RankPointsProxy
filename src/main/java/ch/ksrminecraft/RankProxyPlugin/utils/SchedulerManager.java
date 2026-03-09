package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SchedulerManager {

    private final ProxyServer server;
    private final Scheduler scheduler;
    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final ConfigManager config;
    private final PromotionManager promotionManager;
    private final StaffPermissionService staffPermissionService;
    private final PendingStaffEventStore pendingStaffEventStore;
    private final OfflinePlayerStore offlinePlayerStore;
    private final LogHelper log;
    private final AfkManager afkManager;

    public SchedulerManager(ProxyServer server,
                            Scheduler scheduler,
                            PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            ConfigManager config,
                            PromotionManager promotionManager,
                            StaffPermissionService staffPermissionService,
                            PendingStaffEventStore pendingStaffEventStore,
                            OfflinePlayerStore offlinePlayerStore,
                            LogHelper log) {
        this(server, scheduler, pointsAPI, stafflistManager, config, promotionManager, staffPermissionService, pendingStaffEventStore, offlinePlayerStore, log, null);
    }

    public SchedulerManager(ProxyServer server,
                            Scheduler scheduler,
                            PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            ConfigManager config,
                            PromotionManager promotionManager,
                            StaffPermissionService staffPermissionService,
                            PendingStaffEventStore pendingStaffEventStore,
                            OfflinePlayerStore offlinePlayerStore,
                            LogHelper log,
                            AfkManager afkManager) {
        this.server = server;
        this.scheduler = scheduler;
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.config = config;
        this.promotionManager = promotionManager;
        this.staffPermissionService = staffPermissionService;
        this.pendingStaffEventStore = pendingStaffEventStore;
        this.offlinePlayerStore = offlinePlayerStore;
        this.log = log;
        this.afkManager = afkManager;
    }

    public void startTasks(Object pluginInstance) {
        startPointTask(pluginInstance);
        startPromotionTask(pluginInstance);
        startAutosaveTask(pluginInstance);
        startStaffSyncTask(pluginInstance);
    }

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
                        if (stafflistManager.isStaff(uuid) && !config.isStaffPointsAllowed()) {
                            log.debug("[PointsTask] Skipped {} (staff member, give-points=false)", player.getUsername());
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("[PointsTask] Staff-Check für {} fehlgeschlagen – überspringe. Fehler: {}", player.getUsername(), e.getMessage());
                        continue;
                    }

                    if (afkManager != null && afkManager.isAfk(uuid)) {
                        log.debug("[PointsTask] Skipped {} (AFK)", player.getUsername());
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

    private void startStaffSyncTask(Object pluginInstance) {
        int interval = Math.max(5, config.getStaffSyncIntervalSeconds());

        log.info("[Scheduler] Starting staff sync task every {}s", interval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                StafflistManager.StaffChanges changes = stafflistManager.pollStaffChanges();
                if (changes.isEmpty()) {
                    return;
                }

                for (Map.Entry<UUID, String> entry : changes.added().entrySet()) {
                    UUID uuid = entry.getKey();
                    String name = entry.getValue();

                    log.info("[StaffSyncTask] Neuer Staff-Eintrag erkannt: {} ({})", name, uuid);

                    StaffPermissionService.PermissionSyncResult result =
                            staffPermissionService.promoteToStaff(uuid, name);

                    if (!result.success()) {
                        log.warn("[StaffSyncTask] LuckPerms-Umstellung beim Hinzufügen von {} ({}) fehlgeschlagen.", name, uuid);
                        continue;
                    }

                    if (!result.changed()) {
                        log.debug("[StaffSyncTask] {} ({}) war bereits korrekt Staff.", name, uuid);
                    }

                    server.getPlayer(uuid).ifPresentOrElse(player -> {
                        PromotionMessageSender.sendStaffAppointment(player, scheduler, pluginInstance);
                        log.info("[StaffSyncTask] Staff-Event an online Spieler {} gesendet.", name);
                    }, () -> {
                        pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.APPOINTMENT);
                        log.info("[StaffSyncTask] {} ({}) ist offline – Staff-Event wird beim nächsten Login nachgeholt.", name, uuid);
                    });
                }

                for (Map.Entry<UUID, String> entry : changes.removed().entrySet()) {
                    UUID uuid = entry.getKey();
                    String name = entry.getValue();

                    log.info("[StaffSyncTask] Entfernter Staff-Eintrag erkannt: {} ({})", name, uuid);

                    StaffPermissionService.PermissionSyncResult result =
                            staffPermissionService.demoteFromStaff(uuid, name);

                    if (!result.success()) {
                        log.warn("[StaffSyncTask] LuckPerms-Umstellung beim Entfernen von {} ({}) fehlgeschlagen.", name, uuid);
                        continue;
                    }

                    if (!result.changed()) {
                        log.debug("[StaffSyncTask] {} ({}) war bereits korrekt nicht mehr Staff.", name, uuid);
                    }

                    server.getPlayer(uuid).ifPresentOrElse(player -> {
                        PromotionMessageSender.sendStaffRemoval(player, scheduler, pluginInstance);
                        log.info("[StaffSyncTask] Staff-Removal-Event an online Spieler {} gesendet.", name);
                    }, () -> {
                        pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.REMOVAL);
                        log.info("[StaffSyncTask] {} ({}) ist offline – Staff-Removal-Event wird beim nächsten Login nachgeholt.", name, uuid);
                    });
                }

            } catch (Throwable t) {
                log.error("[StaffSyncTask] Unhandled exception", t);
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }
}