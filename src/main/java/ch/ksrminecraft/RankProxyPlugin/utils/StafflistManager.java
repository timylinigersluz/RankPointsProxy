package ch.ksrminecraft.RankProxyPlugin.utils;

import org.slf4j.Logger;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StafflistManager {

    private final Connection connection;
    private final Logger logger;

    public StafflistManager(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;

        try {
            ensureTableExists();
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to check or create stafflist table", e);
        }
    }

    private void ensureTableExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS stafflist (" +
                "UUID VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(50) NOT NULL" +
                ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
            logger.info("[StafflistManager] Checked or created table 'stafflist'.");
        }
    }

    public boolean addStaffMember(UUID uuid, String name) {
        String sql = "INSERT IGNORE INTO stafflist (UUID, name) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to add staff member {} ({}): {}", name, uuid, e.getMessage());
            return false;
        }
    }

    public boolean removeStaffMember(UUID uuid) {
        String sql = "DELETE FROM stafflist WHERE UUID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to remove staff member {}: {}", uuid, e.getMessage());
            return false;
        }
    }

    public boolean isStaff(UUID uuid) {
        String sql = "SELECT UUID FROM stafflist WHERE UUID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to check if {} is staff: {}", uuid, e.getMessage());
            return false;
        }
    }

    public Map<String, String> getAllStaff() {
        Map<String, String> staffMap = new HashMap<>();

        String sql = "SELECT UUID, name FROM stafflist";

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString("UUID");
                String name = rs.getString("name");
                staffMap.put(uuid, name);
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to get all staff members: {}", e.getMessage());
        }

        return staffMap;
    }
}
