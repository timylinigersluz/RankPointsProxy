package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.AfkManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Empfängt AFK-Status-Nachrichten von den Paper-Servern (RankPointsAPI-Bridge)
 * über den Channel "rankproxy:afk" und aktualisiert den AfkManager.
 */
public class AfkMessageListener {

    private final ProxyServer proxy;
    private final AfkManager afkManager;
    private final Logger logger;
    private final ChannelIdentifier channel = MinecraftChannelIdentifier.from("rankproxy:afk");

    public AfkMessageListener(ProxyServer proxy, AfkManager afkManager, Logger logger) {
        this.proxy = proxy;
        this.afkManager = afkManager;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }

        byte[] data = event.getData();
        String message = new String(data, StandardCharsets.UTF_8);
        String[] parts = message.split(";");
        if (parts.length != 2) {
            logger.warn("[AFK] Ungültige Nachricht: {}", message);
            return;
        }

        try {
            UUID uuid = UUID.fromString(parts[0]);
            boolean isAfk = Boolean.parseBoolean(parts[1]);
            afkManager.setAfk(uuid, isAfk);

            // Optionales Debug-Log
            logger.info("[AFK] {} -> {}", uuid, isAfk ? "AFK" : "aktiv");
        } catch (IllegalArgumentException e) {
            logger.warn("[AFK] Fehler beim Parsen: {}", message, e);
        }
    }
}
