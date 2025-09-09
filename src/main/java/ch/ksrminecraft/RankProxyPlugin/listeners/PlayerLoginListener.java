package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.Scheduler;

import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerLoginListener {

    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlineStore;
    private final StafflistManager stafflistManager;
    private final Logger logger;
    private final Scheduler scheduler;
    private final Object pluginInstance;

    public PlayerLoginListener(PromotionManager promotionManager,
                               OfflinePlayerStore offlineStore,
                               StafflistManager stafflistManager,
                               Logger logger,
                               Scheduler scheduler,
                               Object pluginInstance) {
        this.promotionManager = promotionManager;
        this.offlineStore = offlineStore;
        this.stafflistManager = stafflistManager;
        this.logger = logger;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
    }

    @Subscribe
    public void onPlayerLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername();

        logger.debug("ServerConnectedEvent getriggert für {}", name);

        // Spieler im Offline-Store aktualisieren
        offlineStore.record(name, uuid);

        // Staff: keinerlei Promotion/Demotion durch RankProxyPlugin
        try {
            if (stafflistManager.isStaff(uuid)) {
                logger.info("→ Spieler {} ist in der Stafflist. Überspringe Promotion/Title.", name);
                return;
            }
        } catch (Exception e) {
            // Fail-safe: Wenn der Staff-Check fehlschlägt, keine Änderungen vornehmen
            logger.warn("Konnte Stafflist für {} nicht prüfen – überspringe vorsorglich Promotion.", name, e);
            return;
        }

        // Sofortige Prüfung
        promotionManager.handleLogin(player);

        // Optional: leichte Verzögerung für späte Daten anderer Plugins
        scheduler.buildTask(pluginInstance, () -> {
            logger.debug("Verzögerte Promotion-Prüfung nach Login für {}", name);
            promotionManager.handleLogin(player);
        }).delay(2000, TimeUnit.MILLISECONDS).schedule();
    }
}
