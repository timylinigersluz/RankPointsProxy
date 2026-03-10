package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Schreibt Online-, AFK-, LastSeen- und Server-Infos in MySQL.
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
            log.error("PresenceManager: Tabelle 'player_presence' konnte nicht geprüft oder erstellt werden: {}", e.getMessage());
            log.debug("PresenceManager Exception bei ensureTableExists", e);
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
            log.info("PresenceManager: Tabelle 'player_presence' geprüft/erstellt");
        }
    }

    /**
     * Setzt Spieler sichtbar online.
     * last_login wird nur bei echtem Neu-Login aktualisiert.
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

            log.trace("PresenceManager: markOnline {} ({}) server={}", name, uuid, serverNameOrNull);

        } catch (SQLException e) {
            log.warn("PresenceManager: markOnline fehlgeschlagen für {} ({}): {}", name, uuid, e.getMessage());
            log.debug("PresenceManager Exception bei markOnline für '{}'", name, e);
        }
    }

    /**
     * Login während Vanish:
     * last_login korrekt setzen, aber sichtbar offline halten.
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

            log.trace("PresenceManager: markVanishedLogin {} ({})", name, uuid);

        } catch (SQLException e) {
            log.warn("PresenceManager: markVanishedLogin fehlgeschlagen für {} ({}): {}", name, uuid, e.getMessage());
            log.debug("PresenceManager Exception bei markVanishedLogin für '{}'", name, e);
        }
    }

    /**
     * Erzwingt für vanished Spieler: offline + nicht AFK.
     * last_login/last_seen bleiben unverändert.
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

            log.trace("PresenceManager: forceHidden {} ({})", name, uuid);

        } catch (SQLException e) {
            log.warn("PresenceManager: forceHidden fehlgeschlagen für {} ({}): {}", name, uuid, e.getMessage());
            log.debug("PresenceManager Exception bei forceHidden für '{}'", name, e);
        }
    }

    /**
     * Aktualisiert nur den Servernamen, aber nur wenn is_online=1.
     */
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

            log.trace("PresenceManager: updateServer {} server={}", uuid, serverNameOrNull);

        } catch (SQLException e) {
            log.warn("PresenceManager: updateServer fehlgeschlagen für {}: {}", uuid, e.getMessage());
            log.debug("PresenceManager Exception bei updateServer für {}", uuid, e);
        }
    }

    /**
     * Setzt AFK-Status, aber nur wenn is_online=1.
     */
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

            log.trace("PresenceManager: updateAfk {} -> {}", uuid, afk ? "AFK" : "aktiv");

        } catch (SQLException e) {
            log.warn("PresenceManager: updateAfk fehlgeschlagen für {}: {}", uuid, e.getMessage());
            log.debug("PresenceManager Exception bei updateAfk für {}", uuid, e);
        }
    }

    /**
     * Markiert Spieler als offline und setzt last_seen.
     * AFK wird dabei auf 0 zurückgesetzt.
     */
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

            log.trace("PresenceManager: markOffline {}", uuid);

        } catch (SQLException e) {
            log.warn("PresenceManager: markOffline fehlgeschlagen für {}: {}", uuid, e.getMessage());
            log.debug("PresenceManager Exception bei markOffline für {}", uuid, e);
        }
    }
}