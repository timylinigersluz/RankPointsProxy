package ch.ksrminecraft.RankProxyPlugin.listeners;

import ch.ksrminecraft.RankProxyPlugin.utils.AfkManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.PresenceManager;
import ch.ksrminecraft.RankProxyPlugin.utils.PremiumVanishHook;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Empfängt AFK-Status-Nachrichten von den Paper-Servern (RankPointsAPI-Bridge)
 * über den Channel "rankproxy:afk" und aktualisiert den AfkManager + Presence-DB.
 */
public class AfkMessageListener {

    private final ProxyServer proxy;
    private final AfkManager afkManager;
    private final LogHelper log;
    private final PresenceManager presence;
    private final PremiumVanishHook premiumVanish; // kann null sein

    private final ChannelIdentifier channel = MinecraftChannelIdentifier.from("rankproxy:afk");

    public AfkMessageListener(ProxyServer proxy,
                              AfkManager afkManager,
                              LogHelper log,
                              PresenceManager presence,
                              PremiumVanishHook premiumVanish) {
        this.proxy = proxy;
        this.afkManager = afkManager;
        this.log = log;
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
        log.trace("AfkMessageListener: empfangene Plugin-Message '{}'", message);

        String[] parts = message.split(";");
        if (parts.length != 2) {
            log.warn("AfkMessageListener: Ungültige AFK-Nachricht: {}", message);
            return;
        }

        try {
            UUID uuid = UUID.fromString(parts[0]);
            boolean isAfk = Boolean.parseBoolean(parts[1]);

            log.debug("AfkMessageListener: empfangen für {} -> isAfk={}", uuid, isAfk);

            // Wenn vanished: immer AFK=0 erzwingen und nicht als online/afk anzeigen
            if (premiumVanish != null && premiumVanish.isVanished(uuid)) {
                log.debug("AfkMessageListener: {} ist vanished, erzwinge hidden / nicht AFK", uuid);

                afkManager.setAfk(uuid, false);

                if (presence != null) {
                    presence.updateAfk(uuid, false);
                    presence.forceHidden(uuid, null);
                }
                return;
            }

            boolean old = afkManager.isAfk(uuid);
            if (old == isAfk) {
                log.trace("AfkMessageListener: keine AFK-Änderung für {} (weiterhin {})", uuid, isAfk);
                return;
            }

            afkManager.setAfk(uuid, isAfk);

            if (presence != null) {
                presence.updateAfk(uuid, isAfk);
            }

            log.info("AFK-Status geändert: {} -> {}", uuid, isAfk ? "AFK" : "aktiv");
        } catch (IllegalArgumentException e) {
            log.warn("AfkMessageListener: Fehler beim Parsen der Nachricht '{}': {}", message, e.getMessage());
            log.debug("AfkMessageListener Exception beim Parsen", e);
        }
    }
}