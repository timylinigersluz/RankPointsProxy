package ch.ksrminecraft.RankProxyPlugin.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingStaffEventStore {

    public enum PendingStaffEventType {
        APPOINTMENT,
        REMOVAL
    }

    private final Map<UUID, PendingStaffEventType> pendingEvents = new ConcurrentHashMap<>();

    public void setPending(UUID uuid, PendingStaffEventType type) {
        pendingEvents.put(uuid, type);
    }

    public PendingStaffEventType consume(UUID uuid) {
        return pendingEvents.remove(uuid);
    }

    public boolean hasPending(UUID uuid) {
        return pendingEvents.containsKey(uuid);
    }

    public void clear(UUID uuid) {
        pendingEvents.remove(uuid);
    }
}