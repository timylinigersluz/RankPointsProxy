package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.AfkManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PresenceManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PremiumVanishHook;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Empfängt AFK-Status-Nachrichten von den Paper-Servern (RankPointsAPI-Bridge)
 * über den Channel "rankproxy:afk" und aktualisiert den AfkManager + Presence-DB.
 */
public class AfkMessageListener {

    private final ProxyServer proxy;
    private final AfkManager afkManager;
    private final Logger logger;
    private final PresenceManager presence;
    private final PremiumVanishHook premiumVanish; // kann null sein

    private final ChannelIdentifier channel = MinecraftChannelIdentifier.from("rankproxy:afk");

    public AfkMessageListener(ProxyServer proxy, AfkManager afkManager, Logger logger, PresenceManager presence, PremiumVanishHook premiumVanish) {
        this.proxy = proxy;
        this.afkManager = afkManager;
        this.logger = logger;
        this.presence = presence;
        this.premiumVanish = premiumVanish;
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

            // Wenn vanished: immer AFK=0 erzwingen und nicht als online/afk anzeigen
            if (premiumVanish != null && premiumVanish.isVanished(uuid)) {
                afkManager.setAfk(uuid, false);
                if (presence != null) {
                    // falls der Spieler bereits in DB sichtbar/afk war -> hart korrigieren
                    presence.updateAfk(uuid, false);
                    // optional zusätzlich "hidden" erzwingen (Name kennen wir hier nicht zuverlässig)
                    presence.forceHidden(uuid, null);
                }
                return;
            }

            boolean old = afkManager.isAfk(uuid);
            if (old == isAfk) {
                return; // keine Änderung
            }

            afkManager.setAfk(uuid, isAfk);

            if (presence != null) {
                presence.updateAfk(uuid, isAfk);
            }

            logger.info("[AFK] {} -> {}", uuid, isAfk ? "AFK" : "aktiv");
        } catch (IllegalArgumentException e) {
            logger.warn("[AFK] Fehler beim Parsen: {}", message, e);
        }
    }
}
