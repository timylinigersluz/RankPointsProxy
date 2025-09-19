package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;

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

    public PlayerLoginListener(PromotionManager promotionManager,
                               OfflinePlayerStore offlineStore,
                               StafflistManager stafflistManager,
                               LogHelper log,
                               Scheduler scheduler,
                               Object pluginInstance,
                               LuckPerms luckPerms,
                               ConfigManager config) {
        this.promotionManager = promotionManager;
        this.offlineStore = offlineStore;
        this.stafflistManager = stafflistManager;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
        this.log = log;
        this.luckPerms = luckPerms;
        this.config = config;
    }

    @Subscribe
    public void onPlayerLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername();

        log.debug("ServerConnectedEvent getriggert für {}", name);

        // Spieler im Offline-Store aktualisieren
        offlineStore.record(name, uuid);

        try {
            if (stafflistManager.isStaff(uuid)) {
                log.info("→ Spieler {} ist in der Stafflist.", name);

                // Prüfen ob Spieler in Staff-Laufbahn ist
                String staffGroup = config.getStaffGroupName();
                User user = luckPerms.getUserManager().loadUser(uuid).join();

                boolean hasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(node -> node.getGroupName().equalsIgnoreCase(staffGroup));

                if (!hasGroup) {
                    user.data().add(InheritanceNode.builder(staffGroup).build());
                    luckPerms.getUserManager().saveUser(user);
                    log.info("→ Spieler {} war nicht in LuckPerms-Gruppe '{}', wurde automatisch hinzugefügt.", name, staffGroup);
                } else {
                    log.debug("→ Spieler {} ist bereits in der LuckPerms-Gruppe '{}'.", name, staffGroup);
                }

                // Staff: keine Promotion/Demotion durch RankProxyPlugin
                return;
            }
        } catch (Exception e) {
            log.warn("Konnte Stafflist/LuckPerms für {} nicht prüfen – überspringe vorsorglich Promotion. Fehler: {}", name, e.getMessage());
            return;
        }

        // Sofortige Prüfung für Nicht-Staff
        promotionManager.handleLogin(player);

        // Optional: leichte Verzögerung für späte Daten anderer Plugins
        scheduler.buildTask(pluginInstance, () -> {
            log.debug("Verzögerte Promotion-Prüfung nach Login für {}", name);
            promotionManager.handleLogin(player);
        }).delay(2000, TimeUnit.MILLISECONDS).schedule();
    }
}
