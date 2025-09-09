package ch.ksrminecraft.RankProxyPlugin.utils;

import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * StafflistManager – holt sich für jede Operation eine frische Connection aus dem Pool (DataSource).
 * Fix für "No operations allowed after connection closed".
 */
public class StafflistManager {

    private final DataSource dataSource;
    private final Logger logger;

    public StafflistManager(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
        try {
            ensureTableExists();
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to check or create stafflist table", e);
        }
    }

    private void ensureTableExists() throws SQLException {
        final String sql =
                "CREATE TABLE IF NOT EXISTS stafflist (" +
                        "  UUID VARCHAR(36) NOT NULL PRIMARY KEY," +
                        "  name VARCHAR(50) NOT NULL" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
            logger.info("[StafflistManager] Checked or created table 'stafflist'.");
        }
    }

    public boolean addStaffMember(UUID uuid, String name) {
        final String sql = "INSERT IGNORE INTO stafflist (UUID, name) VALUES (?, ?)";
        try {
            return addStaffMemberOnce(uuid, name, sql);
        } catch (SQLNonTransientConnectionException e) {
            // einmaliger Retry bei abgerissener Verbindung
            logger.warn("[StafflistManager] addStaffMember retry after connection issue for {}", uuid);
            try {
                return addStaffMemberOnce(uuid, name, sql);
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to add staff member {} ({}): {}", name, uuid, ex.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to add staff member {} ({}): {}", name, uuid, e.getMessage());
            return false;
        }
    }

    private boolean addStaffMemberOnce(UUID uuid, String name, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean removeStaffMember(UUID uuid) {
        final String sql = "DELETE FROM stafflist WHERE UUID = ?";
        try {
            return removeStaffMemberOnce(uuid, sql);
        } catch (SQLNonTransientConnectionException e) {
            logger.warn("[StafflistManager] removeStaffMember retry after connection issue for {}", uuid);
            try {
                return removeStaffMemberOnce(uuid, sql);
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to remove staff member {}: {}", uuid, ex.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to remove staff member {}: {}", uuid, e.getMessage());
            return false;
        }
    }

    private boolean removeStaffMemberOnce(UUID uuid, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean isStaff(UUID uuid) {
        final String sql = "SELECT 1 FROM stafflist WHERE UUID = ? LIMIT 1";
        try {
            return isStaffOnce(uuid, sql);
        } catch (SQLNonTransientConnectionException e) {
            logger.warn("[StafflistManager] isStaff retry after connection issue for {}", uuid);
            try {
                return isStaffOnce(uuid, sql);
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to check if {} is staff: {}", uuid, ex.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to check if {} is staff: {}", uuid, e.getMessage());
            return false;
        }
    }

    private boolean isStaffOnce(UUID uuid, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Map<String, String> getAllStaff() {
        final Map<String, String> staffMap = new HashMap<>();
        final String sql = "SELECT UUID, name FROM stafflist";
        try {
            getAllStaffOnce(staffMap, sql);
        } catch (SQLNonTransientConnectionException e) {
            logger.warn("[StafflistManager] getAllStaff retry after connection issue");
            try {
                getAllStaffOnce(staffMap, sql);
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to get all staff members: {}", ex.getMessage());
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to get all staff members: {}", e.getMessage());
        }
        return staffMap;
    }

    private void getAllStaffOnce(Map<String, String> staffMap, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                staffMap.put(rs.getString("UUID"), rs.getString("name"));
            }
        }
    }
}
