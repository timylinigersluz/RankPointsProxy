package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * PresenceManager – schreibt Online-/AFK-/LastSeen-/Server-Infos in MySQL.
 */
public class PresenceManager {

    private final DataSource dataSource;
    private final LogHelper log;

    public PresenceManager(DataSource dataSource, LogHelper log) {
        this.dataSource = dataSource;
        this.log = log;

        try {
            ensureTableExists();
        } catch (SQLException e) {
            log.error("[PresenceManager] Failed to check or create table 'player_presence'", e);
        }
    }

    private void ensureTableExists() throws SQLException {
        final String sql =
                "CREATE TABLE IF NOT EXISTS player_presence (" +
                        "  uuid        VARCHAR(36)  NOT NULL PRIMARY KEY," +
                        "  name        VARCHAR(100) NULL," +
                        "  is_online   TINYINT(1)   NOT NULL DEFAULT 0," +
                        "  is_afk      TINYINT(1)   NOT NULL DEFAULT 0," +
                        "  server      VARCHAR(64)  NULL," +
                        "  last_login  DATETIME     NULL," +
                        "  last_seen   DATETIME     NULL," +
                        "  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        "                           ON UPDATE CURRENT_TIMESTAMP," +
                        "  INDEX idx_online (is_online)," +
                        "  INDEX idx_seen   (last_seen)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
            log.info("[PresenceManager] Checked or created table 'player_presence'.");
        }
    }

    /**
     * Setzt Spieler "sichtbar online" (is_online=1) und aktualisiert last_login nur,
     * wenn ein echter Logout stattgefunden hat (last_seen >= last_login) oder last_login NULL ist.
     *
     * Damit wird Unvanish (während online) NICHT als "neuer Login" gezählt.
     */
    public void markOnline(UUID uuid, String name, String serverNameOrNull) {
        final String sql =
                "INSERT INTO player_presence (uuid, name, is_online, server, last_login) " +
                        "VALUES (?, ?, 1, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  name = VALUES(name), " +
                        "  server = VALUES(server), " +
                        "  is_online = 1, " +
                        "  is_afk = IF(is_online = 1, is_afk, 0), " +
                        "  last_login = IF(last_login IS NULL OR (last_seen IS NOT NULL AND last_seen >= last_login), NOW(), last_login)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, serverNameOrNull);

            ps.executeUpdate();
            log.trace("[PresenceManager] markOnline {} ({}) server={}", name, uuid, serverNameOrNull);

        } catch (SQLException e) {
            log.warn("[PresenceManager] markOnline failed for {} ({}): {}", name, uuid, e.getMessage());
        }
    }

    /**
     * Login während Vanish: last_login soll korrekt gesetzt werden, aber is_online/is_afk müssen 0 bleiben.
     */
    public void markVanishedLogin(UUID uuid, String name) {
        final String sql =
                "INSERT INTO player_presence (uuid, name, is_online, is_afk, server, last_login) " +
                        "VALUES (?, ?, 0, 0, NULL, NOW()) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  name = VALUES(name), " +
                        "  is_online = 0, " +
                        "  is_afk = 0, " +
                        "  server = NULL, " +
                        "  last_login = IF(last_login IS NULL OR (last_seen IS NOT NULL AND last_seen >= last_login), NOW(), last_login)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();

            log.trace("[PresenceManager] markVanishedLogin {} ({})", name, uuid);

        } catch (SQLException e) {
            log.warn("[PresenceManager] markVanishedLogin failed for {} ({}): {}", name, uuid, e.getMessage());
        }
    }

    /**
     * Spieler ist aktuell vanished: hart erzwingen, dass Website ihn als offline + nicht afk sieht.
     * last_login/last_seen werden NICHT verändert.
     */
    public void forceHidden(UUID uuid, String name) {
        final String sql =
                "INSERT INTO player_presence (uuid, name, is_online, is_afk, server) " +
                        "VALUES (?, ?, 0, 0, NULL) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  name = VALUES(name), " +
                        "  is_online = 0, " +
                        "  is_afk = 0, " +
                        "  server = NULL";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();

            log.trace("[PresenceManager] forceHidden {} ({})", name, uuid);

        } catch (SQLException e) {
            log.warn("[PresenceManager] forceHidden failed for {} ({}): {}", name, uuid, e.getMessage());
        }
    }

    /** Aktualisiert nur den Servernamen – aber nur, wenn is_online=1 (sichtbar). */
    public void updateServer(UUID uuid, String serverNameOrNull) {
        final String sql =
                "INSERT INTO player_presence (uuid, server) " +
                        "VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  server = IF(is_online = 1, VALUES(server), server)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, serverNameOrNull);

            ps.executeUpdate();
            log.trace("[PresenceManager] updateServer {} server={}", uuid, serverNameOrNull);

        } catch (SQLException e) {
            log.warn("[PresenceManager] updateServer failed for {}: {}", uuid, e.getMessage());
        }
    }

    /** Setzt AFK-Status – aber nur, wenn is_online=1 (sichtbar). */
    public void updateAfk(UUID uuid, boolean afk) {
        final String sql =
                "INSERT INTO player_presence (uuid, is_afk) " +
                        "VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  is_afk = IF(is_online = 1, VALUES(is_afk), 0)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, afk ? 1 : 0);

            ps.executeUpdate();
            log.trace("[PresenceManager] updateAfk {} -> {}", uuid, afk ? "AFK" : "aktiv");

        } catch (SQLException e) {
            log.warn("[PresenceManager] updateAfk failed for {}: {}", uuid, e.getMessage());
        }
    }

    /** Markiert Spieler als offline und setzt last_seen. AFK wird dabei auf 0 zurückgesetzt. */
    public void markOffline(UUID uuid) {
        final String sql =
                "INSERT INTO player_presence (uuid, is_online, is_afk, last_seen) " +
                        "VALUES (?, 0, 0, NOW()) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "  is_online = 0, " +
                        "  is_afk = 0, " +
                        "  server = NULL, " +
                        "  last_seen = NOW()";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            log.trace("[PresenceManager] markOffline {}", uuid);

        } catch (SQLException e) {
            log.warn("[PresenceManager] markOffline failed for {}: {}", uuid, e.getMessage());
        }
    }
}
