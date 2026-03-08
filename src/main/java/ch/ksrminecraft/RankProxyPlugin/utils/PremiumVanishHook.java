package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PremiumVanishHook
 * - Liest periodisch premiumvanish_playerdata (oder konfigurierbare Tabelle)
 * - Cached alle vanished UUIDs
 */
public class PremiumVanishHook {

    private final DataSource dataSource;
    private final LogHelper log;
    private final String tableName;

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public PremiumVanishHook(DataSource dataSource, LogHelper log, String tableName) {
        this.dataSource = dataSource;
        this.log = log;
        this.tableName = (tableName == null || tableName.isBlank()) ? "premiumvanish_playerdata" : tableName;
    }

    public boolean isVanished(UUID uuid) {
        if (uuid == null) return false;
        return vanished.contains(uuid);
    }

    /**
     * Reload vanished UUIDs from DB.
     * Expected schema: UUID (varchar 36), Vanished (tinyint 0/1)
     */
    public void refreshNow() {
        final String sql = "SELECT UUID FROM " + tableName + " WHERE Vanished = 1";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            Set<UUID> fresh = ConcurrentHashMap.newKeySet();
            while (rs.next()) {
                String raw = rs.getString(1);
                try {
                    fresh.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignore) {
                    log.warn("[PremiumVanish] Invalid UUID in table {}: {}", tableName, raw);
                }
            }

            vanished.clear();
            vanished.addAll(fresh);

            log.debug("[PremiumVanish] Refreshed vanished cache: {} vanished player(s).", vanished.size());
        } catch (Exception e) {
            log.warn("[PremiumVanish] refreshNow failed: {}", e.getMessage());
        }
    }
}
