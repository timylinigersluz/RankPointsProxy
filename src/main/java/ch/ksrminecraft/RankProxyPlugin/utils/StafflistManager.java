package ch.ksrminecraft.RankProxyPlugin.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StafflistManager – holt sich für jede Operation eine frische Connection aus dem Pool (DataSource).
 * Fix für "No operations allowed after connection closed".
 * Zusätzlich: In-Memory-Cache (TTL) für schnelle isStaff()-Abfragen.
 */
public class StafflistManager {

    private final DataSource dataSource;
    private final LogHelper log;

    // Einfacher TTL-Cache aller Staff-UUIDs
    private final Set<UUID> staffCache = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastCacheLoad = new AtomicLong(0L);
    private volatile long cacheTtlMillis = Duration.ofSeconds(60).toMillis(); // Default 60s

    public StafflistManager(DataSource dataSource, LogHelper log) {
        this.dataSource = dataSource;
        this.log = log;
        try {
            ensureTableExists();
            refreshCacheIfExpired(true); // initialer Cache-Ladevorgang
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to check or create stafflist table", e);
        }
    }

    public StafflistManager(DataSource dataSource, LogHelper log, int cacheTtlSeconds) {
        this(dataSource, log);
        setCacheTtlSeconds(cacheTtlSeconds);
        log.info("[StafflistManager] Using staff cache TTL = {}s", cacheTtlSeconds);
    }

    public void setCacheTtlSeconds(int seconds) {
        if (seconds < 1) seconds = 1;
        this.cacheTtlMillis = Duration.ofSeconds(seconds).toMillis();
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
            log.info("[StafflistManager] Checked or created table 'stafflist'.");
        }
    }

    // ----------- Public API -----------

    public boolean addStaffMember(UUID uuid, String name) {
        final String sql = "INSERT IGNORE INTO stafflist (UUID, name) VALUES (?, ?)";
        try {
            boolean ok = addStaffMemberOnce(uuid, name, sql);
            if (ok) invalidateCache();
            return ok;
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] addStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = addStaffMemberOnce(uuid, name, sql);
                if (ok) invalidateCache();
                return ok;
            } catch (SQLException ex) {
                log.error("[StafflistManager] Failed to add staff member {} ({})", name, uuid, ex);
                return false;
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to add staff member {} ({})", name, uuid, e);
            return false;
        }
    }

    public boolean removeStaffMember(UUID uuid) {
        final String sql = "DELETE FROM stafflist WHERE UUID = ?";
        try {
            boolean ok = removeStaffMemberOnce(uuid, sql);
            if (ok) invalidateCache();
            return ok;
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] removeStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = removeStaffMemberOnce(uuid, sql);
                if (ok) invalidateCache();
                return ok;
            } catch (SQLException ex) {
                log.error("[StafflistManager] Failed to remove staff member {}", uuid, ex);
                return false;
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to remove staff member {}", uuid, e);
            return false;
        }
    }

    public boolean isStaff(UUID uuid) {
        try {
            refreshCacheIfExpired(false);
            return staffCache.contains(uuid);
        } catch (Exception e) {
            log.warn("[StafflistManager] Cache check failed, falling back to single query", e);
            return isStaffDb(uuid);
        }
    }

    public Map<String, String> getAllStaff() {
        final Map<String, String> staffMap = new HashMap<>();
        final String sql = "SELECT UUID, name FROM stafflist";
        try {
            getAllStaffOnce(staffMap, sql);
            refreshCacheFromMap(staffMap);
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] getAllStaff retry after connection issue");
            try {
                getAllStaffOnce(staffMap, sql);
                refreshCacheFromMap(staffMap);
            } catch (SQLException ex) {
                log.error("[StafflistManager] Failed to get all staff members", ex);
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to get all staff members", e);
        }
        return staffMap;
    }

    // ----------- Internals -----------

    private void invalidateCache() {
        lastCacheLoad.set(0L);
    }

    private void refreshCacheFromMap(Map<String, String> staffMap) {
        Set<UUID> fresh = new HashSet<>();
        for (String s : staffMap.keySet()) {
            try {
                fresh.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignore) {
                log.warn("[StafflistManager] Invalid UUID in staffMap: {}", s);
            }
        }
        staffCache.clear();
        staffCache.addAll(fresh);
        lastCacheLoad.set(System.currentTimeMillis());
    }

    private void refreshCacheIfExpired(boolean force) {
        long now = System.currentTimeMillis();
        long last = lastCacheLoad.get();
        if (!force && (now - last) < cacheTtlMillis) return;

        final String sql = "SELECT UUID FROM stafflist";
        Set<UUID> fresh = new HashSet<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    fresh.add(UUID.fromString(rs.getString(1)));
                } catch (IllegalArgumentException ignore) {
                    log.warn("[StafflistManager] Invalid UUID in DB ignored");
                }
            }
            staffCache.clear();
            staffCache.addAll(fresh);
            lastCacheLoad.set(now);
            log.debug("[StafflistManager] Staff cache reloaded ({} entries).", staffCache.size());
        } catch (SQLException e) {
            log.warn("[StafflistManager] Could not refresh staff cache", e);
        }
    }

    private boolean isStaffDb(UUID uuid) {
        final String sql = "SELECT 1 FROM stafflist WHERE UUID = ? LIMIT 1";
        try {
            return isStaffOnce(uuid, sql);
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] isStaff (db) retry after connection issue for {}", uuid);
            try {
                return isStaffOnce(uuid, sql);
            } catch (SQLException ex) {
                log.error("[StafflistManager] Failed to check if {} is staff", uuid, ex);
                return false;
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to check if {} is staff", uuid, e);
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

    private boolean removeStaffMemberOnce(UUID uuid, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
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

    private void getAllStaffOnce(Map<String, String> staffMap, String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                staffMap.put(rs.getString("UUID"), rs.getString("name"));
            }
        }
    }

    public boolean addStaffAndAssignGroup(UUID uuid, String name, LuckPerms luckPerms, String staffGroup) {
        boolean added = addStaffMember(uuid, name);
        if (!added) return false;

        try {
            var user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("[StafflistManager] Konnte LuckPerms-User für {} ({}) nicht laden.", name, uuid);
                return false;
            }

            boolean alreadyHasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                    .anyMatch(n -> n.getGroupName().equalsIgnoreCase(staffGroup));

            if (!alreadyHasGroup) {
                user.data().add(InheritanceNode.builder(staffGroup).build());
                luckPerms.getUserManager().saveUser(user);
                log.info("[StafflistManager] {} ({}) zur LuckPerms-Gruppe '{}' hinzugefügt.", name, uuid, staffGroup);
            } else {
                log.debug("[StafflistManager] {} ({}) war bereits in LuckPerms-Gruppe '{}'.", name, uuid, staffGroup);
            }
        } catch (Exception e) {
            log.error("[StafflistManager] Fehler beim Hinzufügen von {} ({}) zur Gruppe '{}'", name, uuid, staffGroup, e);
        }
        return true;
    }
}
