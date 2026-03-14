package ch.ksrminecraft.RankProxyPlugin.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class StaffPermissionService {

    public record PermissionSyncResult(boolean success, boolean changed) {}

    private final LuckPerms luckPerms;
    private final LogHelper log;

    /**
     * Echte Player-Ränge, dynamisch aus ranks.yaml
     * z. B. beginner, iron, bronze, ...
     */
    private final List<String> playerTrackGroups;

    /**
     * Echte Staff-Ränge, dynamisch aus resources.yaml
     * z. B. moderator, builder, admin, tec, owner
     */
    private final List<String> staffRanks;

    /**
     * Reine Info / Logging
     */
    private final String playerTrackName;
    private final String staffTrackName;

    /**
     * Technische Default-Gruppen der beiden Laufbahnen
     */
    private final String playerDefaultGroup;
    private final String staffDefaultGroup;

    public StaffPermissionService(LuckPerms luckPerms,
                                  LogHelper log,
                                  List<String> playerTrackGroups,
                                  List<String> staffRanks,
                                  String playerTrackName,
                                  String staffTrackName,
                                  String playerDefaultGroup,
                                  String staffDefaultGroup) {
        this.luckPerms = luckPerms;
        this.log = log;
        this.playerTrackGroups = List.copyOf(playerTrackGroups);
        this.staffRanks = List.copyOf(staffRanks);
        this.playerTrackName = playerTrackName;
        this.staffTrackName = staffTrackName;
        this.playerDefaultGroup = playerDefaultGroup;
        this.staffDefaultGroup = staffDefaultGroup;
    }

    /**
     * Staff hinzufügen.
     *
     * Logik:
     * - Player-Default entfernen
     * - alle Player-Ränge entfernen
     * - Staff-Default hinzufügen
     * - nur wenn noch kein echter Staffrang vorhanden ist:
     *   niedrigsten Staffrang setzen
     */
    public PermissionSyncResult promoteToStaff(UUID uuid, String name) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("StaffPermissionService: Konnte LuckPerms-User für {} ({}) nicht laden", name, uuid);
                return new PermissionSyncResult(false, false);
            }

            if (staffRanks.isEmpty()) {
                log.error("StaffPermissionService: Keine Staff-Ränge konfiguriert. Staff-Promotion für {} ({}) abgebrochen",
                        name, uuid);
                return new PermissionSyncResult(false, false);
            }

            boolean changed = false;

            // Player-Laufbahn entfernen
            changed |= removeGroupIfPresent(user, playerDefaultGroup, uuid);

            for (String group : playerTrackGroups) {
                changed |= removeGroupIfPresent(user, group, uuid);
            }

            // Staff-Default setzen
            changed |= addGroupIfMissing(user, staffDefaultGroup, uuid);

            String lowestStaffRank = staffRanks.get(0);

            Set<String> directGroups = getDirectGroupNames(user);
            Set<String> effectiveGroups = getEffectiveGroupNames(user);

            log.debug("StaffPermissionService: promoteToStaff {} ({})", name, uuid);
            log.debug("StaffPermissionService: playerTrackName={}, staffTrackName={}", playerTrackName, staffTrackName);
            log.debug("StaffPermissionService: playerDefaultGroup={}, staffDefaultGroup={}",
                    playerDefaultGroup, staffDefaultGroup);
            log.debug("StaffPermissionService: directGroups={}", directGroups);
            log.debug("StaffPermissionService: effectiveGroups={}", effectiveGroups);
            log.debug("StaffPermissionService: configuredStaffRanks={}", staffRanks);

            if (!hasAnyRealStaffRank(user)) {
                changed |= addGroupIfMissing(user, lowestStaffRank, uuid);
                log.info("StaffPermissionService: {} ({}) hatte keinen echten Staffrang – '{}' wird gesetzt",
                        name, uuid, lowestStaffRank);
            } else {
                log.info("StaffPermissionService: {} ({}) hat bereits einen echten Staffrang – kein Fallback auf '{}' nötig",
                        name, uuid, lowestStaffRank);
            }

            log.debug("StaffPermissionService: Gruppen nach promoteToStaff für {} ({}): {}",
                    name, uuid, getDirectGroupNames(user));

            if (changed) {
                luckPerms.getUserManager().saveUser(user).join();
                log.info("StaffPermissionService: {} ({}) erfolgreich in Staff-Laufbahn '{}' verschoben",
                        name, uuid, staffTrackName);
            } else {
                log.info("StaffPermissionService: {} ({}) war bereits korrekt in der Staff-Laufbahn '{}'",
                        name, uuid, staffTrackName);
            }

            luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));
            return new PermissionSyncResult(true, changed);

        } catch (Exception e) {
            log.error("StaffPermissionService: Fehler beim Staff-Hinzufügen für {} ({}): {}", name, uuid, e.getMessage());
            log.debug("StaffPermissionService Exception bei promoteToStaff für {}", uuid, e);
            return new PermissionSyncResult(false, false);
        }
    }

    /**
     * Staff entfernen.
     *
     * Logik:
     * - Staff-Default entfernen
     * - alle Staff-Ränge entfernen
     * - Player-Default hinzufügen
     */
    public PermissionSyncResult demoteFromStaff(UUID uuid, String name) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("StaffPermissionService: Konnte LuckPerms-User für {} ({}) nicht laden", name, uuid);
                return new PermissionSyncResult(false, false);
            }

            boolean changed = false;

            // Staff-Laufbahn entfernen
            changed |= removeGroupIfPresent(user, staffDefaultGroup, uuid);

            for (String group : staffRanks) {
                changed |= removeGroupIfPresent(user, group, uuid);
            }

            // Player-Default setzen
            changed |= addGroupIfMissing(user, playerDefaultGroup, uuid);

            log.debug("StaffPermissionService: demoteFromStaff {} ({}) -> directGroups={}",
                    name, uuid, getDirectGroupNames(user));

            if (changed) {
                luckPerms.getUserManager().saveUser(user).join();
                log.info("StaffPermissionService: {} ({}) erfolgreich aus Staff-Laufbahn '{}' entfernt",
                        name, uuid, staffTrackName);
            } else {
                log.info("StaffPermissionService: {} ({}) war bereits korrekt nicht mehr Staff", name, uuid);
            }

            luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));
            return new PermissionSyncResult(true, changed);

        } catch (Exception e) {
            log.error("StaffPermissionService: Fehler beim Staff-Entfernen für {} ({}): {}", name, uuid, e.getMessage());
            log.debug("StaffPermissionService Exception bei demoteFromStaff für {}", uuid, e);
            return new PermissionSyncResult(false, false);
        }
    }

    public Set<String> getCurrentGroups(UUID uuid) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                return Set.of();
            }
            return getDirectGroupNames(user);

        } catch (Exception e) {
            log.error("StaffPermissionService: Fehler beim Auslesen der Gruppen für {}: {}", uuid, e.getMessage());
            log.debug("StaffPermissionService Exception bei getCurrentGroups für {}", uuid, e);
            return Set.of();
        }
    }

    public String getPlayerTrackName() {
        return playerTrackName;
    }

    public String getStaffTrackName() {
        return staffTrackName;
    }

    public String getPlayerDefaultGroup() {
        return playerDefaultGroup;
    }

    public String getStaffDefaultGroup() {
        return staffDefaultGroup;
    }

    public List<String> getPlayerTrackGroups() {
        return playerTrackGroups;
    }

    public List<String> getStaffRanks() {
        return staffRanks;
    }

    /**
     * Erkennt, ob der User bereits einen echten Staffrang besitzt.
     *
     * Wichtig:
     * - default_staff zählt NICHT als echter Staffrang
     * - direkte + geerbte Gruppen werden berücksichtigt
     * - Reihenfolge/Quelle der Staffränge kommt aus resources.yaml
     */
    private boolean hasAnyRealStaffRank(User user) {
        if (staffRanks.isEmpty()) {
            return false;
        }

        Set<String> realStaffGroups = new LinkedHashSet<>();
        for (String rank : staffRanks) {
            if (rank != null && !rank.isBlank()) {
                realStaffGroups.add(rank.toLowerCase(Locale.ROOT));
            }
        }

        Set<String> effectiveGroups = getEffectiveGroupNames(user);
        boolean match = effectiveGroups.stream().anyMatch(realStaffGroups::contains);

        log.debug("StaffPermissionService: Staffrang-Prüfung für {} -> effectiveGroups={}, realStaffGroups={}, result={}",
                user.getUniqueId(), effectiveGroups, realStaffGroups, match);

        return match;
    }

    private Set<String> getDirectGroupNames(User user) {
        Set<String> groups = new LinkedHashSet<>();

        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            String groupName = node.getGroupName();
            if (groupName != null) {
                groups.add(groupName.toLowerCase(Locale.ROOT));
            }
        }

        return groups;
    }

    private Set<String> getEffectiveGroupNames(User user) {
        Set<String> groups = new LinkedHashSet<>();
        groups.addAll(getDirectGroupNames(user));

        try {
            if (user.getQueryOptions() != null) {
                for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
                    if (group != null && group.getName() != null) {
                        groups.add(group.getName().toLowerCase(Locale.ROOT));
                    }
                }
            } else {
                log.warn("StaffPermissionService: QueryOptions für User {} sind null", user.getUniqueId());
            }
        } catch (Exception e) {
            log.warn("StaffPermissionService: Konnte inherited groups für {} nicht prüfen: {}",
                    user.getUniqueId(), e.getMessage());
            log.debug("StaffPermissionService Exception bei getEffectiveGroupNames für {}", user.getUniqueId(), e);
        }

        return groups;
    }

    private boolean addGroupIfMissing(User user, String groupName, UUID uuid) {
        if (groupName == null || groupName.isBlank()) {
            log.warn("StaffPermissionService: addGroupIfMissing mit leerem Gruppennamen für {}", uuid);
            return false;
        }

        boolean hasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(groupName));

        if (hasGroup) {
            return false;
        }

        boolean success = user.data().add(InheritanceNode.builder(groupName).build()).wasSuccessful();
        if (success) {
            log.debug("StaffPermissionService: Gruppe '{}' zu {} hinzugefügt", groupName, uuid);
        }
        return success;
    }

    private boolean removeGroupIfPresent(User user, String groupName, UUID uuid) {
        if (groupName == null || groupName.isBlank()) {
            return false;
        }

        boolean changed = false;

        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (node.getGroupName().equalsIgnoreCase(groupName)) {
                if (user.data().remove(node).wasSuccessful()) {
                    changed = true;
                    log.debug("StaffPermissionService: Gruppe '{}' von {} entfernt", groupName, uuid);
                }
            }
        }

        return changed;
    }
}