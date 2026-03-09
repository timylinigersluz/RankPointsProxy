package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StafflistManager – frische DB-Verbindungen + Cache + Change Detection
 *
 * Verantwortung:
 * - Stafflist-Tabelle sicherstellen
 * - Staff-Mitglieder in DB hinzufügen/entfernen
 * - Cache aktuell halten
 * - Änderungen (added/removed) erkennen
 *
 * Keine LuckPerms-Logik mehr:
 * Diese liegt zentral im StaffPermissionService.
 */
public class StafflistManager {

    public static class StaffChanges {
        private final Map<UUID, String> added;
        private final Map<UUID, String> removed;

        public StaffChanges(Map<UUID, String> added, Map<UUID, String> removed) {
            this.added = added;
            this.removed = removed;
        }

        public Map<UUID, String> added() {
            return added;
        }

        public Map<UUID, String> removed() {
            return removed;
        }

        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    private final DataSource dataSource;
    private final LogHelper log;

    private final Set<UUID> staffCache = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> staffNameCache = new ConcurrentHashMap<>();

    private final AtomicLong lastCacheLoad = new AtomicLong(0L);
    private volatile long cacheTtlMillis = Duration.ofSeconds(60).toMillis();

    public StafflistManager(DataSource dataSource, LogHelper log) {
        this.dataSource = dataSource;
        this.log = log;
        try {
            ensureTableExists();
            refreshCacheIfExpired(true);
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
        if (seconds < 1) {
            seconds = 1;
        }
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

    public boolean addStaffMember(UUID uuid, String name) {
        final String sql = "INSERT IGNORE INTO stafflist (UUID, name) VALUES (?, ?)";

        try {
            boolean ok = addStaffMemberOnce(uuid, name, sql);
            if (ok) {
                staffCache.add(uuid);
                staffNameCache.put(uuid, name);
                lastCacheLoad.set(System.currentTimeMillis());
            }
            return ok;
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] addStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = addStaffMemberOnce(uuid, name, sql);
                if (ok) {
                    staffCache.add(uuid);
                    staffNameCache.put(uuid, name);
                    lastCacheLoad.set(System.currentTimeMillis());
                }
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
            if (ok) {
                staffCache.remove(uuid);
                staffNameCache.remove(uuid);
                lastCacheLoad.set(System.currentTimeMillis());
            }
            return ok;
        } catch (SQLNonTransientConnectionException e) {
            log.warn("[StafflistManager] removeStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = removeStaffMemberOnce(uuid, sql);
                if (ok) {
                    staffCache.remove(uuid);
                    staffNameCache.remove(uuid);
                    lastCacheLoad.set(System.currentTimeMillis());
                }
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

    public boolean removeStaffByName(String name) {
        UUID uuid = getUUIDByName(name);
        if (uuid == null) {
            return false;
        }

        final String sql = "DELETE FROM stafflist WHERE LOWER(name) = LOWER(?)";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                staffCache.remove(uuid);
                staffNameCache.remove(uuid);
                lastCacheLoad.set(System.currentTimeMillis());
            }
            return ok;
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to remove staff by name '{}'", name, e);
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

    public UUID getUUIDByName(String name) {
        final String sql = "SELECT UUID FROM stafflist WHERE LOWER(name) = LOWER(?) LIMIT 1";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("UUID"));
                }
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to get UUID for name '{}'", name, e);
        }

        return null;
    }

    public List<String> getAllStaffNames() {
        List<String> names = new ArrayList<>();
        final String sql = "SELECT name FROM stafflist ORDER BY name ASC";

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            log.error("[StafflistManager] Failed to get all staff names", e);
        }

        return names;
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

    /**
     * Erkennt neue UND entfernte Staff-Einträge seit dem letzten bekannten Stand.
     */
    public StaffChanges pollStaffChanges() {
        Map<UUID, String> dbMap = fetchAllStaffEntries();

        Set<UUID> previousStaff = new HashSet<>(staffCache);
        Map<UUID, String> previousNames = new HashMap<>(staffNameCache);

        Map<UUID, String> added = new HashMap<>();
        Map<UUID, String> removed = new HashMap<>();

        for (Map.Entry<UUID, String> entry : dbMap.entrySet()) {
            if (!previousStaff.contains(entry.getKey())) {
                added.put(entry.getKey(), entry.getValue());
            }
        }

        for (UUID oldUuid : previousStaff) {
            if (!dbMap.containsKey(oldUuid)) {
                removed.put(oldUuid, previousNames.getOrDefault(oldUuid, oldUuid.toString()));
            }
        }

        staffCache.clear();
        staffCache.addAll(dbMap.keySet());

        staffNameCache.clear();
        staffNameCache.putAll(dbMap);

        lastCacheLoad.set(System.currentTimeMillis());

        if (!added.isEmpty()) {
            log.info("[StafflistManager] {} neue Staff-Einträge erkannt.", added.size());
        }
        if (!removed.isEmpty()) {
            log.info("[StafflistManager] {} entfernte Staff-Einträge erkannt.", removed.size());
        }

        return new StaffChanges(added, removed);
    }

    // =====================================================
    // Internals
    // =====================================================

    private void refreshCacheFromMap(Map<String, String> staffMap) {
        Set<UUID> fresh = new HashSet<>();
        Map<UUID, String> names = new HashMap<>();

        for (Map.Entry<String, String> entry : staffMap.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                fresh.add(uuid);
                names.put(uuid, entry.getValue());
            } catch (IllegalArgumentException ignore) {
                log.warn("[StafflistManager] Invalid UUID in staffMap: {}", entry.getKey());
            }
        }

        staffCache.clear();
        staffCache.addAll(fresh);

        staffNameCache.clear();
        staffNameCache.putAll(names);

        lastCacheLoad.set(System.currentTimeMillis());
    }

    private void refreshCacheIfExpired(boolean force) {
        long now = System.currentTimeMillis();
        long last = lastCacheLoad.get();

        if (!force && (now - last) < cacheTtlMillis) {
            return;
        }

        Map<UUID, String> dbMap = fetchAllStaffEntries();

        staffCache.clear();
        staffCache.addAll(dbMap.keySet());

        staffNameCache.clear();
        staffNameCache.putAll(dbMap);

        lastCacheLoad.set(now);
        log.debug("[StafflistManager] Staff cache reloaded ({} entries).", staffCache.size());
    }

    private Map<UUID, String> fetchAllStaffEntries() {
        final String sql = "SELECT UUID, name FROM stafflist";
        Map<UUID, String> result = new HashMap<>();

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("UUID"));
                    String name = rs.getString("name");
                    result.put(uuid, name);
                } catch (IllegalArgumentException ignore) {
                    log.warn("[StafflistManager] Invalid UUID in DB ignored");
                }
            }

        } catch (SQLException e) {
            log.warn("[StafflistManager] Could not fetch staff entries", e);
        }

        return result;
    }

    private boolean isStaffDb(UUID uuid) {
        final String sql = "SELECT 1 FROM stafflist WHERE UUID = ? LIMIT 1";

        try {
            return isStaffOnce(uuid, sql);
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
}