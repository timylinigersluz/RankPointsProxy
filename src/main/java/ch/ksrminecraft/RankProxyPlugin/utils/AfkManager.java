package ch.ksrminecraft.RankProxyPlugin.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager {
    private final Map<UUID, Boolean> afkMap = new ConcurrentHashMap<>();

    public void setAfk(UUID uuid, boolean afk) {
        afkMap.put(uuid, afk);
    }

    public boolean isAfk(UUID uuid) {
        return afkMap.getOrDefault(uuid, false);
    }

    public void clear(UUID uuid) {
        afkMap.remove(uuid);
    }
}
