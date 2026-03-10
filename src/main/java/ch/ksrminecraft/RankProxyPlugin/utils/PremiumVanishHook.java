package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liest periodisch PremiumVanish-Daten aus der Datenbank
 * und cached alle aktuell vanished UUIDs.
 */
public class PremiumVanishHook {

    private final DataSource dataSource;
    private final LogHelper log;
    private final String tableName;

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public PremiumVanishHook(DataSource dataSource, LogHelper log, String tableName) {
        this.dataSource = dataSource;
        this.log = log;
        this.tableName = (tableName == null || tableName.isBlank())
                ? "premiumvanish_playerdata"
                : tableName;
    }

    public boolean isVanished(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return vanished.contains(uuid);
    }

    /**
     * Lädt alle vanished UUIDs neu aus der Datenbank.
     * Erwartetes Schema: UUID (varchar 36), Vanished (tinyint 0/1)
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
                    log.warn("PremiumVanishHook: Ungültige UUID in Tabelle {}: {}", tableName, raw);
                }
            }

            vanished.clear();
            vanished.addAll(fresh);

            log.debug("PremiumVanishHook: Vanish-Cache aktualisiert: {} Spieler", vanished.size());

        } catch (Exception e) {
            log.warn("PremiumVanishHook: refreshNow fehlgeschlagen: {}", e.getMessage());
            log.debug("PremiumVanishHook Exception bei refreshNow", e);
        }
    }
}