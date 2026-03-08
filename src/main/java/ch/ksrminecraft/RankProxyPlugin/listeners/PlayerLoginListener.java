package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PresenceManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PremiumVanishHook;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.Scheduler;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerLoginListener {

    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlineStore;
    private final StafflistManager stafflistManager;
    private final Scheduler scheduler;
    private final Object pluginInstance;
    private final LogHelper log;
    private final LuckPerms luckPerms;
    private final ConfigManager config;
    private final PresenceManager presence;
    private final PremiumVanishHook premiumVanish;

    public PlayerLoginListener(PromotionManager promotionManager,
                               OfflinePlayerStore offlineStore,
                               StafflistManager stafflistManager,
                               LogHelper log,
                               Scheduler scheduler,
                               Object pluginInstance,
                               LuckPerms luckPerms,
                               ConfigManager config,
                               PresenceManager presence,
                               PremiumVanishHook premiumVanish) {
        this.promotionManager = promotionManager;
        this.offlineStore = offlineStore;
        this.stafflistManager = stafflistManager;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
        this.log = log;
        this.luckPerms = luckPerms;
        this.config = config;
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

        // Spieler im Offline-Store aktualisieren
        offlineStore.record(name, uuid);

        // Zielserver aus dem Event
        String targetServer = event.getServer().getServerInfo().getName();

        // PremiumVanish: wenn vanished -> is_online/is_afk müssen 0 bleiben
        boolean vanished = (premiumVanish != null && premiumVanish.isVanished(uuid));
        if (vanished) {
            presence.forceHidden(uuid, name);
        } else {
            presence.markOnline(uuid, name, targetServer);
        }

        // Kurzer Nachzug für den aktuell verbundenen Server
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

        // Staff-Sonderfall
        try {
            if (stafflistManager.isStaff(uuid)) {
                log.info("→ Spieler {} ist in der Stafflist.", name);

                String staffGroup = config.getStaffGroupName();
                User user = luckPerms.getUserManager().loadUser(uuid).join();

                if (user == null) {
                    log.warn("→ Konnte LuckPerms-User für {} ({}) nicht laden – überspringe.", name, uuid);
                    return;
                }

                boolean hasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(node -> node.getGroupName().equalsIgnoreCase(staffGroup));

                if (!hasGroup) {
                    user.data().add(InheritanceNode.builder(staffGroup).build());
                    luckPerms.getUserManager().saveUser(user).join();

                    luckPerms.getMessagingService().ifPresent(service -> {
                        service.pushUserUpdate(user);
                    });

                    log.info("→ Spieler {} war nicht in LuckPerms-Gruppe '{}', wurde automatisch hinzugefügt.", name, staffGroup);
                } else {
                    log.debug("→ Spieler {} ist bereits in der LuckPerms-Gruppe '{}'.", name, staffGroup);
                }

                return; // Staff: keine Promotion/Demotion
            }
        } catch (Exception e) {
            log.warn("Konnte Stafflist/LuckPerms für {} nicht prüfen – überspringe vorsorglich Promotion. Fehler: {}", name, e.getMessage());
            return;
        }

        // Promotion/Demotion nur beim ERSTEN Join auf den Proxy, nicht bei Backend-Wechseln
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