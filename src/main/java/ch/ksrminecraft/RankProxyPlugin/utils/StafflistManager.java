package ch.ksrminecraft.RankProxyPlugin.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Frische DB-Verbindungen + Cache + Change Detection für die Stafflist.
 *
 * Verantwortung:
 * - Stafflist-Tabelle sicherstellen
 * - Staff-Mitglieder in DB hinzufügen/entfernen
 * - Cache aktuell halten
 * - Änderungen (added/removed) erkennen
 *
 * Keine LuckPerms-Logik:
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

    /**
     * Snapshot für Ladevorgänge, bei denen zwischen
     * "leer" und "DB-Fehler" unterschieden werden muss.
     */
    public static class StaffLoadSnapshot {
        private final boolean success;
        private final Map<String, String> entries;

        public StaffLoadSnapshot(boolean success, Map<String, String> entries) {
            this.success = success;
            this.entries = entries;
        }

        public boolean success() {
            return success;
        }

        public Map<String, String> entries() {
            return entries;
        }
    }

    /**
     * Internes Ergebnis für DB-Fetches.
     */
    private static class StaffFetchResult {
        private final boolean success;
        private final Map<UUID, String> entries;

        private StaffFetchResult(boolean success, Map<UUID, String> entries) {
            this.success = success;
            this.entries = entries;
        }

        public boolean success() {
            return success;
        }

        public Map<UUID, String> entries() {
            return entries;
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
            log.error("StafflistManager: Tabelle 'stafflist' konnte nicht geprüft oder erstellt werden: {}", e.getMessage());
            log.debug("StafflistManager Exception im Konstruktor", e);
        }
    }

    public StafflistManager(DataSource dataSource, LogHelper log, int cacheTtlSeconds) {
        this(dataSource, log);
        setCacheTtlSeconds(cacheTtlSeconds);
        log.info("StafflistManager: Staff-Cache-TTL = {}s", cacheTtlSeconds);
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
            log.info("StafflistManager: Tabelle 'stafflist' geprüft/erstellt");
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
                log.debug("StafflistManager: {} ({}) zur Stafflist hinzugefügt", name, uuid);
            }
            return ok;

        } catch (SQLNonTransientConnectionException e) {
            log.warn("StafflistManager: addStaffMember Retry nach Verbindungsproblem für {}", uuid);
            log.debug("StafflistManager Connection Exception bei addStaffMember für {}", uuid, e);

            try {
                boolean ok = addStaffMemberOnce(uuid, name, sql);
                if (ok) {
                    staffCache.add(uuid);
                    staffNameCache.put(uuid, name);
                    lastCacheLoad.set(System.currentTimeMillis());
                    log.debug("StafflistManager: {} ({}) nach Retry zur Stafflist hinzugefügt", name, uuid);
                }
                return ok;
            } catch (SQLException ex) {
                log.error("StafflistManager: Fehler beim Hinzufügen von {} ({}): {}", name, uuid, ex.getMessage());
                log.debug("StafflistManager Exception im Retry addStaffMember für {}", uuid, ex);
                return false;
            }

        } catch (SQLException e) {
            log.error("StafflistManager: Fehler beim Hinzufügen von {} ({}): {}", name, uuid, e.getMessage());
            log.debug("StafflistManager Exception bei addStaffMember für {}", uuid, e);
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
                log.debug("StafflistManager: {} aus Stafflist entfernt", uuid);
            }
            return ok;

        } catch (SQLNonTransientConnectionException e) {
            log.warn("StafflistManager: removeStaffMember Retry nach Verbindungsproblem für {}", uuid);
            log.debug("StafflistManager Connection Exception bei removeStaffMember für {}", uuid, e);

            try {
                boolean ok = removeStaffMemberOnce(uuid, sql);
                if (ok) {
                    staffCache.remove(uuid);
                    staffNameCache.remove(uuid);
                    lastCacheLoad.set(System.currentTimeMillis());
                    log.debug("StafflistManager: {} nach Retry aus Stafflist entfernt", uuid);
                }
                return ok;
            } catch (SQLException ex) {
                log.error("StafflistManager: Fehler beim Entfernen von {}: {}", uuid, ex.getMessage());
                log.debug("StafflistManager Exception im Retry removeStaffMember für {}", uuid, ex);
                return false;
            }

        } catch (SQLException e) {
            log.error("StafflistManager: Fehler beim Entfernen von {}: {}", uuid, e.getMessage());
            log.debug("StafflistManager Exception bei removeStaffMember für {}", uuid, e);
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
                log.debug("StafflistManager: '{}' ({}) per removeStaffByName entfernt", name, uuid);
            }
            return ok;

        } catch (SQLException e) {
            log.error("StafflistManager: Fehler beim Entfernen per Name '{}': {}", name, e.getMessage());
            log.debug("StafflistManager Exception bei removeStaffByName für '{}'", name, e);
            return false;
        }
    }

    public boolean isStaff(UUID uuid) {
        try {
            refreshCacheIfExpired(false);
            return staffCache.contains(uuid);
        } catch (Exception e) {
            log.warn("StafflistManager: Cache-Prüfung fehlgeschlagen, Fallback auf Einzelabfrage für {}", uuid);
            log.debug("StafflistManager Exception bei isStaff Cache-Check für {}", uuid, e);
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
            log.error("StafflistManager: Fehler beim Laden der UUID für '{}': {}", name, e.getMessage());
            log.debug("StafflistManager Exception bei getUUIDByName für '{}'", name, e);
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
            log.error("StafflistManager: Fehler beim Laden aller Staff-Namen: {}", e.getMessage());
            log.debug("StafflistManager Exception bei getAllStaffNames", e);
        }

        return names;
    }

    /**
     * Rückwärtskompatible Methode.
     * Bei DB-Fehler wird weiterhin eine leere Map zurückgegeben,
     * aber der Cache wird NICHT überschrieben.
     */
    public Map<String, String> getAllStaff() {
        StaffFetchResult fetchResult = fetchAllStaffEntries();

        if (!fetchResult.success()) {
            log.warn("StafflistManager: getAllStaff liefert wegen DB-Fehler eine leere Map zurück. Bestehender Cache bleibt erhalten.");
            return new HashMap<>();
        }

        Map<String, String> staffMap = new HashMap<>();
        for (Map.Entry<UUID, String> entry : fetchResult.entries().entrySet()) {
            staffMap.put(entry.getKey().toString(), entry.getValue());
        }

        refreshCacheFromUuidMap(fetchResult.entries());
        return staffMap;
    }

    /**
     * Sichere Variante für Startup-Sync:
     * unterscheidet zwischen "leer" und "DB-Fehler".
     */
    public StaffLoadSnapshot loadAllStaffSnapshot() {
        StaffFetchResult fetchResult = fetchAllStaffEntries();

        if (!fetchResult.success()) {
            return new StaffLoadSnapshot(false, Map.of());
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<UUID, String> entry : fetchResult.entries().entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }

        return new StaffLoadSnapshot(true, result);
    }

    /**
     * Erkennt neue und entfernte Staff-Einträge seit dem letzten bekannten Stand.
     *
     * Wichtig:
     * Bei DB-Fehler wird NICHT auf einen leeren Stand umgestellt.
     * Stattdessen bleibt der bestehende Cache erhalten.
     */
    public StaffChanges pollStaffChanges() {
        StaffFetchResult fetchResult = fetchAllStaffEntries();

        if (!fetchResult.success()) {
            log.warn("StafflistManager: pollStaffChanges wird wegen DB-Fehler übersprungen. Bestehender Cache bleibt erhalten.");
            return new StaffChanges(Map.of(), Map.of());
        }

        Map<UUID, String> dbMap = fetchResult.entries();

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

        refreshCacheFromUuidMap(dbMap);

        if (!added.isEmpty()) {
            log.info("StafflistManager: {} neue Staff-Einträge erkannt", added.size());
        }
        if (!removed.isEmpty()) {
            log.info("StafflistManager: {} entfernte Staff-Einträge erkannt", removed.size());
        }

        return new StaffChanges(added, removed);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private void refreshCacheFromMap(Map<String, String> staffMap) {
        Set<UUID> fresh = new HashSet<>();
        Map<UUID, String> names = new HashMap<>();

        for (Map.Entry<String, String> entry : staffMap.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                fresh.add(uuid);
                names.put(uuid, entry.getValue());
            } catch (IllegalArgumentException ignore) {
                log.warn("StafflistManager: Ungültige UUID im staffMap ignoriert: {}", entry.getKey());
            }
        }

        staffCache.clear();
        staffCache.addAll(fresh);

        staffNameCache.clear();
        staffNameCache.putAll(names);

        lastCacheLoad.set(System.currentTimeMillis());
    }

    private void refreshCacheFromUuidMap(Map<UUID, String> dbMap) {
        staffCache.clear();
        staffCache.addAll(dbMap.keySet());

        staffNameCache.clear();
        staffNameCache.putAll(dbMap);

        lastCacheLoad.set(System.currentTimeMillis());
    }

    private void refreshCacheIfExpired(boolean force) {
        long now = System.currentTimeMillis();
        long last = lastCacheLoad.get();

        if (!force && (now - last) < cacheTtlMillis) {
            return;
        }

        StaffFetchResult fetchResult = fetchAllStaffEntries();

        if (!fetchResult.success()) {
            log.warn("StafflistManager: Staff-Cache konnte nicht aktualisiert werden – alter Cache bleibt erhalten");
            return;
        }

        Map<UUID, String> dbMap = fetchResult.entries();
        refreshCacheFromUuidMap(dbMap);

        lastCacheLoad.set(now);
        log.debug("StafflistManager: Staff-Cache neu geladen ({} Einträge)", staffCache.size());
    }

    private StaffFetchResult fetchAllStaffEntries() {
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
                    log.warn("StafflistManager: Ungültige UUID in DB ignoriert");
                }
            }

            return new StaffFetchResult(true, result);

        } catch (SQLException e) {
            log.warn("StafflistManager: Staff-Einträge konnten nicht geladen werden: {}", e.getMessage());
            log.debug("StafflistManager Exception bei fetchAllStaffEntries", e);
            return new StaffFetchResult(false, Map.of());
        }
    }

    private boolean isStaffDb(UUID uuid) {
        final String sql = "SELECT 1 FROM stafflist WHERE UUID = ? LIMIT 1";

        try {
            return isStaffOnce(uuid, sql);
        } catch (SQLException e) {
            log.error("StafflistManager: Fehler beim Staff-Check für {}: {}", uuid, e.getMessage());
            log.debug("StafflistManager Exception bei isStaffDb für {}", uuid, e);
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