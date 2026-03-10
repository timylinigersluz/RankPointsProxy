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
        this(server, scheduler, pointsAPI, stafflistManager, config, promotionManager,
                staffPermissionService, pendingStaffEventStore, offlinePlayerStore, log, null);
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

        log.info("SchedulerManager: Starte Punkte-Task alle {}s ({} Punkt(e) pro Intervall)", interval, amount);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                log.debug("SchedulerManager: Punkte-Task läuft");

                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    try {
                        if (stafflistManager.isStaff(uuid) && !config.isStaffPointsAllowed()) {
                            log.trace("SchedulerManager: Punkte übersprungen für {} (Staff, give-points=false)",
                                    player.getUsername());
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("SchedulerManager: Staff-Check für {} fehlgeschlagen – Spieler wird in diesem Durchlauf übersprungen: {}",
                                player.getUsername(), e.getMessage());
                        log.debug("SchedulerManager Exception im Punkte-Task beim Staff-Check für '{}'",
                                player.getUsername(), e);
                        continue;
                    }

                    if (afkManager != null && afkManager.isAfk(uuid)) {
                        log.trace("SchedulerManager: Punkte übersprungen für {} (AFK)", player.getUsername());
                        continue;
                    }

                    pointsAPI.addPoints(uuid, amount);
                    log.trace("SchedulerManager: {} Punkt(e) an {} vergeben", amount, player.getUsername());
                }
            } catch (Throwable t) {
                log.error("SchedulerManager: Unbehandelte Exception im Punkte-Task: {}", t.getMessage());
                log.debug("SchedulerManager Throwable im Punkte-Task", t);
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }

    private void startPromotionTask(Object pluginInstance) {
        int promotionInterval = config.getPromotionIntervalSeconds();

        log.info("SchedulerManager: Starte Promotion-Task alle {}s", promotionInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                log.debug("SchedulerManager: Promotion-Task läuft");

                for (Player player : server.getAllPlayers()) {
                    UUID uuid = player.getUniqueId();

                    try {
                        if (stafflistManager.isStaff(uuid)) {
                            log.trace("SchedulerManager: Promotion übersprungen für {} (Staff)",
                                    player.getUsername());
                            continue;
                        }
                    } catch (Exception e) {
                        log.warn("SchedulerManager: Staff-Check für {} fehlgeschlagen – Promotion wird in diesem Durchlauf übersprungen: {}",
                                player.getUsername(), e.getMessage());
                        log.debug("SchedulerManager Exception im Promotion-Task beim Staff-Check für '{}'",
                                player.getUsername(), e);
                        continue;
                    }

                    promotionManager.handleLogin(uuid, player.getUsername());
                }
            } catch (Throwable t) {
                log.error("SchedulerManager: Unbehandelte Exception im Promotion-Task: {}", t.getMessage());
                log.debug("SchedulerManager Throwable im Promotion-Task", t);
            }
        }).delay(promotionInterval, TimeUnit.SECONDS).repeat(promotionInterval, TimeUnit.SECONDS).schedule();
    }

    private void startAutosaveTask(Object pluginInstance) {
        int autosaveInterval = config.getAutosaveIntervalSeconds();

        log.info("SchedulerManager: Starte Autosave-Task alle {}s", autosaveInterval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                offlinePlayerStore.save();
                log.debug("SchedulerManager: OfflinePlayerStore gespeichert");
            } catch (Throwable t) {
                log.error("SchedulerManager: Unbehandelte Exception beim Autosave: {}", t.getMessage());
                log.debug("SchedulerManager Throwable im Autosave-Task", t);
            }
        }).delay(autosaveInterval, TimeUnit.SECONDS).repeat(autosaveInterval, TimeUnit.SECONDS).schedule();
    }

    private void startStaffSyncTask(Object pluginInstance) {
        int interval = Math.max(5, config.getStaffSyncIntervalSeconds());

        log.info("SchedulerManager: Starte Staff-Sync-Task alle {}s", interval);

        scheduler.buildTask(pluginInstance, () -> {
            try {
                StafflistManager.StaffChanges changes = stafflistManager.pollStaffChanges();

                if (changes.isEmpty()) {
                    log.trace("SchedulerManager: Keine Staff-Änderungen erkannt");
                    return;
                }

                for (Map.Entry<UUID, String> entry : changes.added().entrySet()) {
                    UUID uuid = entry.getKey();
                    String name = entry.getValue();

                    log.info("SchedulerManager: Neuer Staff-Eintrag erkannt: {} ({})", name, uuid);

                    StaffPermissionService.PermissionSyncResult result =
                            staffPermissionService.promoteToStaff(uuid, name);

                    if (!result.success()) {
                        log.warn("SchedulerManager: LuckPerms-Umstellung beim Hinzufügen von {} ({}) fehlgeschlagen",
                                name, uuid);
                        continue;
                    }

                    if (!result.changed()) {
                        log.debug("SchedulerManager: {} ({}) war bereits korrekt Staff", name, uuid);
                    }

                    server.getPlayer(uuid).ifPresentOrElse(player -> {
                        PromotionMessageSender.sendStaffAppointment(player, scheduler, pluginInstance);
                        log.info("SchedulerManager: Staff-Event an online Spieler {} gesendet", name);
                    }, () -> {
                        pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.APPOINTMENT);
                        log.info("SchedulerManager: {} ({}) ist offline – Staff-Event wird beim nächsten Login nachgeholt",
                                name, uuid);
                    });
                }

                for (Map.Entry<UUID, String> entry : changes.removed().entrySet()) {
                    UUID uuid = entry.getKey();
                    String name = entry.getValue();

                    log.info("SchedulerManager: Entfernter Staff-Eintrag erkannt: {} ({})", name, uuid);

                    StaffPermissionService.PermissionSyncResult result =
                            staffPermissionService.demoteFromStaff(uuid, name);

                    if (!result.success()) {
                        log.warn("SchedulerManager: LuckPerms-Umstellung beim Entfernen von {} ({}) fehlgeschlagen",
                                name, uuid);
                        continue;
                    }

                    if (!result.changed()) {
                        log.debug("SchedulerManager: {} ({}) war bereits korrekt nicht mehr Staff", name, uuid);
                    }

                    server.getPlayer(uuid).ifPresentOrElse(player -> {
                        PromotionMessageSender.sendStaffRemoval(player, scheduler, pluginInstance);
                        log.info("SchedulerManager: Staff-Removal-Event an online Spieler {} gesendet", name);
                    }, () -> {
                        pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.REMOVAL);
                        log.info("SchedulerManager: {} ({}) ist offline – Staff-Removal-Event wird beim nächsten Login nachgeholt",
                                name, uuid);
                    });
                }

            } catch (Throwable t) {
                log.error("SchedulerManager: Unbehandelte Exception im Staff-Sync-Task: {}", t.getMessage());
                log.debug("SchedulerManager Throwable im Staff-Sync-Task", t);
            }
        }).delay(interval, TimeUnit.SECONDS).repeat(interval, TimeUnit.SECONDS).schedule();
    }
}