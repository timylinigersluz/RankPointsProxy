package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;
import com.velocitypowered.api.scheduler.Scheduler;
import java.util.concurrent.TimeUnit;


public class PlayerLoginListener {

    private final PromotionManager promotionManager;
    private final OfflinePlayerStore offlineStore;
    private final Logger logger;
    private final Scheduler scheduler;
    private final Object pluginInstance;

    public PlayerLoginListener(PromotionManager promotionManager, OfflinePlayerStore offlineStore, Logger logger, Scheduler scheduler, Object pluginInstance) {
        this.promotionManager = promotionManager;
        this.offlineStore = offlineStore;
        this.logger = logger;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
    }

    @Subscribe
    public void onPlayerLogin(ServerConnectedEvent event) {
        logger.debug("ServerConnectedEvent getriggert");
        Player player = event.getPlayer();

        offlineStore.record(player.getUsername(), player.getUniqueId());
        promotionManager.handleLogin(player); // zeigt ggf. gespeicherte Message

        // Verzögerung um 2 Sekunden)
        scheduler.buildTask(pluginInstance, () -> {
            logger.debug("Verzögerte Promotion-Prüfung nach Login für {}", player.getUsername());
            promotionManager.checkAndPromote(player.getUniqueId(), player.getUsername());
        }).delay(2000, TimeUnit.MILLISECONDS).schedule();
    }
}
