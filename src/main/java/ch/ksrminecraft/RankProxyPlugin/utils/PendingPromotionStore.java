package ch.ksrminecraft.RankProxyPlugin.utils;

import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PendingPromotionStore
 *
 * Zwischenspeicher für Nachrichten, die bei einer Promotion oder Demotion
 * (z. B. beim nächsten Login) an Spieler geschickt werden sollen.
 * - Nutzt Map<UUID, PendingMessage>
 * - Alle Zugriffe werden mit LogHelper protokolliert
 */
public class PendingPromotionStore {

    public enum PromotionType {
        PROMOTION, DEMOTION
    }

    private static class PendingMessage {
        final Component message;
        final PromotionType type;

        PendingMessage(Component message, PromotionType type) {
            this.message = message;
            this.type = type;
        }
    }

    private final Map<UUID, PendingMessage> pending = new HashMap<>();
    private final LogHelper log;

    public PendingPromotionStore(LogHelper log) {
        this.log = log;
    }

    public void setPendingMessage(UUID uuid, Component message, PromotionType type) {
        pending.put(uuid, new PendingMessage(message, type));
        log.debug("[PendingPromotionStore] Nachricht gesetzt für {} ({}).", uuid, type);
    }

    public boolean hasPendingMessage(UUID uuid) {
        boolean result = pending.containsKey(uuid);
        log.trace("[PendingPromotionStore] hasPendingMessage({}) -> {}", uuid, result);
        return result;
    }

    public Component getPendingMessage(UUID uuid) {
        PendingMessage pm = pending.get(uuid);
        if (pm != null) {
            log.trace("[PendingPromotionStore] Nachricht für {} gefunden.", uuid);
            return pm.message;
        } else {
            log.trace("[PendingPromotionStore] Keine Nachricht für {} gefunden.", uuid);
            return null;
        }
    }

    public PromotionType getPromotionType(UUID uuid) {
        PendingMessage pm = pending.get(uuid);
        PromotionType type = (pm != null) ? pm.type : PromotionType.PROMOTION;
        log.trace("[PendingPromotionStore] getPromotionType({}) -> {}", uuid, type);
        return type;
    }

    public void clearPendingMessage(UUID uuid) {
        if (pending.remove(uuid) != null) {
            log.debug("[PendingPromotionStore] Nachricht für {} entfernt.", uuid);
        } else {
            log.trace("[PendingPromotionStore] Keine Nachricht für {} zum Entfernen.", uuid);
        }
    }
}
