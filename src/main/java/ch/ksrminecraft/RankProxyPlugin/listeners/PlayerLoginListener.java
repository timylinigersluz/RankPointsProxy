package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PendingStaffEventStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PresenceManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PremiumVanishHook;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionMessageSender;
import ch.ksrminecraft.RankProxyPlugin.utils.StaffPermissionService;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.Scheduler;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerLoginListener {

    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlineStore;
    private final StafflistManager stafflistManager;
    private final StaffPermissionService staffPermissionService;
    private final PendingStaffEventStore pendingStaffEventStore;
    private final Scheduler scheduler;
    private final Object pluginInstance;
    private final LogHelper log;
    private final PresenceManager presence;
    private final PremiumVanishHook premiumVanish;

    public PlayerLoginListener(PromotionManager promotionManager,
                               OfflinePlayerStore offlineStore,
                               StafflistManager stafflistManager,
                               StaffPermissionService staffPermissionService,
                               PendingStaffEventStore pendingStaffEventStore,
                               LogHelper log,
                               Scheduler scheduler,
                               Object pluginInstance,
                               PresenceManager presence,
                               PremiumVanishHook premiumVanish) {
        this.promotionManager = promotionManager;
        this.offlineStore = offlineStore;
        this.stafflistManager = stafflistManager;
        this.staffPermissionService = staffPermissionService;
        this.pendingStaffEventStore = pendingStaffEventStore;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
        this.log = log;
        this.presence = presence;
        this.premiumVanish = premiumVanish;
    }

    @Subscribe
    public void onPlayerLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername();

        boolean firstJoinToProxy = event.getPreviousServer().isEmpty();

        log.debug("ServerConnectedEvent getriggert für {} (firstJoinToProxy={})", name, firstJoinToProxy);

        offlineStore.record(name, uuid);

        String targetServer = event.getServer().getServerInfo().getName();

        boolean vanished = (premiumVanish != null && premiumVanish.isVanished(uuid));
        if (vanished) {
            presence.forceHidden(uuid, name);
        } else {
            presence.markOnline(uuid, name, targetServer);
        }

        scheduler.buildTask(pluginInstance, () -> {
            if (premiumVanish != null && premiumVanish.isVanished(uuid)) {
                return;
            }
            String current = player.getCurrentServer()
                    .map(cs -> cs.getServerInfo().getName())
                    .orElse(null);
            if (current != null) {
                presence.updateServer(uuid, current);
            }
        }).delay(300, TimeUnit.MILLISECONDS).schedule();

        PendingStaffEventStore.PendingStaffEventType pendingEvent = pendingStaffEventStore.consume(uuid);
        if (pendingEvent != null) {
            switch (pendingEvent) {
                case APPOINTMENT -> {
                    PromotionMessageSender.sendStaffAppointment(player, scheduler, pluginInstance);
                    log.info("→ Nachgeholtes Staff-Appointment-Event für {} ({}) beim Login gesendet.", name, uuid);
                }
                case REMOVAL -> {
                    PromotionMessageSender.sendStaffRemoval(player, scheduler, pluginInstance);
                    log.info("→ Nachgeholtes Staff-Removal-Event für {} ({}) beim Login gesendet.", name, uuid);
                }
            }
        }

        try {
            if (stafflistManager.isStaff(uuid)) {
                log.info("→ Spieler {} ist in der Stafflist. Synchronisiere Staff-Laufbahn beim Join.", name);

                StaffPermissionService.PermissionSyncResult result =
                        staffPermissionService.promoteToStaff(uuid, name);

                if (!result.success()) {
                    log.warn("→ Staff-Synchronisation beim Join für {} ({}) fehlgeschlagen.", name, uuid);
                } else if (result.changed()) {
                    log.info("→ Staff-Synchronisation beim Join für {} ({}) durchgeführt.", name, uuid);
                } else {
                    log.debug("→ Staff-Synchronisation beim Join für {} ({}) nicht nötig, bereits korrekt.", name, uuid);
                }

                return;
            }
        } catch (Exception e) {
            log.warn("Konnte Stafflist für {} nicht prüfen – überspringe vorsorglich Promotion. Fehler: {}", name, e.getMessage());
            return;
        }

        if (firstJoinToProxy) {
            scheduler.buildTask(pluginInstance, () -> {
                log.debug("Verzögerte Promotion-/Demotion-Prüfung 5s nach erstem Login für {}", name);
                promotionManager.handleLogin(player);
            }).delay(5, TimeUnit.SECONDS).schedule();
        } else {
            log.debug("Keine Join-Promotionprüfung für {}: nur Backend-Serverwechsel.", name);
        }
    }
}