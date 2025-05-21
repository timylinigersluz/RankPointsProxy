package ch.ksrminecraft.RankProxyPlugin.utils;

import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PendingPromotionStore {

    public enum PromotionType {
        PROMOTION, DEMOTION
    }

    private static class PendingMessage {
        public final Component message;
        public final PromotionType type;

        public PendingMessage(Component message, PromotionType type) {
            this.message = message;
            this.type = type;
        }
    }

    private final Map<UUID, PendingMessage> pending = new HashMap<>();

    public void setPendingMessage(UUID uuid, Component message, PromotionType type) {
        pending.put(uuid, new PendingMessage(message, type));
    }

    public boolean hasPendingMessage(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public Component getPendingMessage(UUID uuid) {
        return pending.containsKey(uuid) ? pending.get(uuid).message : null;
    }

    public PromotionType getPromotionType(UUID uuid) {
        return pending.containsKey(uuid) ? pending.get(uuid).type : PromotionType.PROMOTION;
    }

    public void clearPendingMessage(UUID uuid) {
        pending.remove(uuid);
    }
}
