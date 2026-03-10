package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.AfkManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.PresenceManager;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.UUID;

public class PlayerDisconnectListener {

    private final PresenceManager presence;
    private final AfkManager afkManager;
    private final LogHelper log;

    public PlayerDisconnectListener(PresenceManager presence, AfkManager afkManager, LogHelper log) {
        this.presence = presence;
        this.afkManager = afkManager;
        this.log = log;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (afkManager != null) {
            afkManager.clear(uuid);
        }

        if (presence != null) {
            presence.markOffline(uuid);
        }

        log.debug("DisconnectEvent: {} ({}) -> offline", p.getUsername(), uuid);
    }
}