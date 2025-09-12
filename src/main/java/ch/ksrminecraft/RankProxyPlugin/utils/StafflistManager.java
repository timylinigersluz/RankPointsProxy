package ch.ksrminecraft.RankProxyPlugin.utils;

import org.slf4j.Logger;

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
    private final Logger logger;

    // Einfacher TTL-Cache aller Staff-UUIDs
    private final Set<UUID> staffCache = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastCacheLoad = new AtomicLong(0L);
    private volatile long cacheTtlMillis = Duration.ofSeconds(60).toMillis(); // Default 60s

    /**
     * Standard-Konstruktor (TTL default 60s).
     */
    public StafflistManager(DataSource dataSource, Logger logger) {
        this.dataSource = dataSource;
        this.logger = logger;
        try {
            ensureTableExists();
            // Initialer Cache-Ladevorgang (non-fatal bei Fehler)
            refreshCacheIfExpired(true);
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to check or create stafflist table", e);
        }
    }

    /**
     * Komfort-Konstruktor mit explizitem TTL (Sekunden) aus der Config.
     */
    public StafflistManager(DataSource dataSource, Logger logger, int cacheTtlSeconds) {
        this(dataSource, logger);
        setCacheTtlSeconds(cacheTtlSeconds);
        logger.info("[StafflistManager] Using staff cache TTL = {}s", cacheTtlSeconds);
    }

    /**
     * Optional konfigurierbar, z.B. aus resources.yaml laden.
     */
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
            logger.info("[StafflistManager] Checked or created table 'stafflist'.");
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
            logger.warn("[StafflistManager] addStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = addStaffMemberOnce(uuid, name, sql);
                if (ok) invalidateCache();
                return ok;
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to add staff member {} ({}): {}", name, uuid, ex.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to add staff member {} ({}): {}", name, uuid, e.getMessage());
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
            logger.warn("[StafflistManager] removeStaffMember retry after connection issue for {}", uuid);
            try {
                boolean ok = removeStaffMemberOnce(uuid, sql);
                if (ok) invalidateCache();
                return ok;
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to remove staff member {}: {}", uuid, ex.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to remove staff member {}: {}", uuid, e.getMessage());
            return false;
        }
    }

    /**
     * Schneller Check mit TTL-Cache. Lädt bei Bedarf periodisch die gesamte Staffliste.
     */
    public boolean isStaff(UUID uuid) {
        try {
            refreshCacheIfExpired(false);
            return staffCache.contains(uuid);
        } catch (Exception e) {
            // Fallback: Wenn Cache nicht nutzbar ist, mache Einzelabfrage
            logger.warn("[StafflistManager] Cache check failed, falling back to single query: {}", e.getMessage());
            return isStaffDb(uuid);
        }
    }

    /**
     * Liefert alle Staff-Mitglieder (UUID->Name) aus der DB.
     * Aktualisiert nebenbei den Cache.
     */
    public Map<String, String> getAllStaff() {
        final Map<String, String> staffMap = new HashMap<>();
        final String sql = "SELECT UUID, name FROM stafflist";
        try {
            getAllStaffOnce(staffMap, sql);
            // Cache aktualisieren
            Set<UUID> fresh = new HashSet<>();
            for (String s : staffMap.keySet()) {
                try { fresh.add(UUID.fromString(s)); } catch (IllegalArgumentException ignore) {}
            }
            staffCache.clear();
            staffCache.addAll(fresh);
            lastCacheLoad.set(System.currentTimeMillis());
        } catch (SQLNonTransientConnectionException e) {
            logger.warn("[StafflistManager] getAllStaff retry after connection issue");
            try {
                getAllStaffOnce(staffMap, sql);
                Set<UUID> fresh = new HashSet<>();
                for (String s : staffMap.keySet()) {
                    try { fresh.add(UUID.fromString(s)); } catch (IllegalArgumentException ignore) {}
                }
                staffCache.clear();
                staffCache.addAll(fresh);
                lastCacheLoad.set(System.currentTimeMillis());
            } catch (SQLException ex) {
                logger.error("[StafflistManager] Failed to get all staff members: {}", ex.getMessage());
            }
        } catch (SQLException e) {
            logger.error("[StafflistManager] Failed to get all staff members: {}", e.getMessage());
        }
        return staffMap;
    }

    // ----------- Internals -----------

    private void invalidateCache() {
        lastCacheLoad.set(0L);
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
                String s = rs.getString(1);
                try { fresh.add(UUID.fromString(s)); } catch (IllegalArgumentException ignore) {}
            }
            staffCache.clear();
            staffCache.addAll(fresh);
            lastCacheLoad.set(now);
            logger.debug("[StafflistManager] Staff cache reloaded ({} entries).", staffCache.size());
        } catch (SQLException e) {
            // Cache unverändert lassen; nächster Versuch beim nächsten Zugriff
            logger.warn("[StafflistManager] Could not refresh staff cache: {}", e.getMessage());
        }
    }

    private boolean isStaffDb(UUID uuid) {
        final String sql = "SELECT 1 FROM stafflist WHERE UUID = ? LIMIT 1";
        try {
            return isStaffOnce(uuid, sql);
        } catch (SQLNonTransientConnectionException e) {
            logger.warn("[StafflistManager] isStaff (db) retry after connection issue for {}", uuid);
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
