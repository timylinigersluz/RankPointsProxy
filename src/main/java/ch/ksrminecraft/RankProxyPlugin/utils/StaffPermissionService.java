package ch.ksrminecraft.RankProxyPlugin.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class StaffPermissionService {

    public record PermissionSyncResult(boolean success, boolean changed) {}

    private static final String BASE_DEFAULT_GROUP = "default";
    private static final String BASE_STAFF_GROUP = "staff";

    /**
     * Technische Gruppen im Staff-Track, die NICHT als eigentlicher Staff-Rang gelten.
     * Diese werden beim Ermitteln des tiefsten "echten" Staff-Rangs übersprungen.
     */
    private static final Set<String> STAFF_TRACK_GROUPS_TO_SKIP = Set.of(
            "staff",
            "default_staff"
    );

    private final LuckPerms luckPerms;
    private final LogHelper log;
    private final List<String> playerTrackGroups;
    private final String staffTrackName;
    private final String defaultTrackName;

    public StaffPermissionService(LuckPerms luckPerms,
                                  LogHelper log,
                                  List<String> playerTrackGroups,
                                  String staffTrackName,
                                  String defaultTrackName) {
        this.luckPerms = luckPerms;
        this.log = log;
        this.playerTrackGroups = List.copyOf(playerTrackGroups);
        this.staffTrackName = staffTrackName;
        this.defaultTrackName = defaultTrackName;
    }

    /**
     * Staff-Hinzufügen:
     * - default entfernen
     * - normale Laufbahn/Track-Gruppe entfernen (z. B. "player")
     * - normalen Spieler-Rang entfernen
     * - staff hinzufügen
     * - tiefsten echten Staff-Rang aus LuckPerms-Track hinzufügen,
     *   aber nur falls noch kein echter Staff-Rang vorhanden ist
     */
    public PermissionSyncResult promoteToStaff(UUID uuid, String name) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("[StaffPermissionService] Konnte LuckPerms-User für {} ({}) nicht laden.", name, uuid);
                return new PermissionSyncResult(false, false);
            }

            boolean changed = false;

            // Basisgruppe default entfernen
            changed |= removeGroupIfPresent(user, BASE_DEFAULT_GROUP, uuid);

            // normale Laufbahn-/Track-Gruppe entfernen (z. B. "player")
            changed |= removeGroupIfPresent(user, defaultTrackName, uuid);

            // alle Player-Ränge aus ranks.yaml entfernen
            for (String group : playerTrackGroups) {
                changed |= removeGroupIfPresent(user, group, uuid);
            }

            // Basisgruppe staff hinzufügen
            changed |= addGroupIfMissing(user, BASE_STAFF_GROUP, uuid);

            // tiefsten echten Staff-Rang aus LuckPerms-Track ermitteln
            String lowestRealStaffRank = getLowestRealGroupFromTrack(staffTrackName);
            if (lowestRealStaffRank == null) {
                log.error("[StaffPermissionService] Staff-Track '{}' konnte nicht gelesen werden oder enthält keinen echten Staff-Rang.", staffTrackName);
                return new PermissionSyncResult(false, false);
            }

            // nur hinzufügen, wenn noch KEIN echter Staff-Rang vorhanden ist
            if (!hasAnyRealStaffTrackRank(user, staffTrackName)) {
                changed |= addGroupIfMissing(user, lowestRealStaffRank, uuid);
            } else {
                log.debug("[StaffPermissionService] {} ({}) hat bereits einen echten Staff-Rang im Track '{}'.", name, uuid, staffTrackName);
            }

            log.debug("[StaffPermissionService] Gruppen nach promoteToStaff für {} ({}): {}", name, uuid,
                    user.getNodes(NodeType.INHERITANCE).stream()
                            .map(InheritanceNode::getGroupName)
                            .filter(groupName -> groupName != null)
                            .map(groupName -> groupName.toLowerCase(Locale.ROOT))
                            .sorted()
                            .toList());

            if (changed) {
                luckPerms.getUserManager().saveUser(user).join();
                log.info("[StaffPermissionService] {} ({}) erfolgreich in Staff-Laufbahn '{}' verschoben.", name, uuid, staffTrackName);
            } else {
                log.info("[StaffPermissionService] {} ({}) war bereits korrekt in der Staff-Laufbahn.", name, uuid);
            }

            // Wichtig: Auch ohne Änderungen ein Update pushen, damit andere Instanzen / Systeme
            // den korrekten Zustand sicher mitbekommen.
            luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));

            return new PermissionSyncResult(true, changed);
        } catch (Exception e) {
            log.error("[StaffPermissionService] Fehler beim Staff-Hinzufügen für {} ({})", name, uuid, e);
            return new PermissionSyncResult(false, false);
        }
    }

    /**
     * Staff-Entfernen:
     * - staff entfernen
     * - alle Staff-Ränge aus Staff-Track entfernen
     * - default hinzufügen
     * - KEINEN normalen Spieler-Rang direkt setzen
     */
    public PermissionSyncResult demoteFromStaff(UUID uuid, String name) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("[StaffPermissionService] Konnte LuckPerms-User für {} ({}) nicht laden.", name, uuid);
                return new PermissionSyncResult(false, false);
            }

            boolean changed = false;

            // Basisgruppe staff entfernen
            changed |= removeGroupIfPresent(user, BASE_STAFF_GROUP, uuid);

            // alle Gruppen aus dem Staff-Track entfernen (inkl. technischer Gruppen)
            List<String> staffTrackGroups = getGroupsFromTrack(staffTrackName);
            for (String group : staffTrackGroups) {
                changed |= removeGroupIfPresent(user, group, uuid);
            }

            // Basisgruppe default hinzufügen
            changed |= addGroupIfMissing(user, BASE_DEFAULT_GROUP, uuid);

            log.debug("[StaffPermissionService] Gruppen nach demoteFromStaff für {} ({}): {}", name, uuid,
                    user.getNodes(NodeType.INHERITANCE).stream()
                            .map(InheritanceNode::getGroupName)
                            .filter(groupName -> groupName != null)
                            .map(groupName -> groupName.toLowerCase(Locale.ROOT))
                            .sorted()
                            .toList());

            if (changed) {
                luckPerms.getUserManager().saveUser(user).join();
                log.info("[StaffPermissionService] {} ({}) erfolgreich aus Staff-Laufbahn '{}' entfernt.", name, uuid, staffTrackName);
            } else {
                log.info("[StaffPermissionService] {} ({}) war bereits korrekt nicht mehr Staff.", name, uuid);
            }

            // Auch ohne Änderungen pushen, damit andere Instanzen / Systeme
            // den korrekten Zustand sicher mitbekommen.
            luckPerms.getMessagingService().ifPresent(service -> service.pushUserUpdate(user));

            return new PermissionSyncResult(true, changed);
        } catch (Exception e) {
            log.error("[StaffPermissionService] Fehler beim Staff-Entfernen für {} ({})", name, uuid, e);
            return new PermissionSyncResult(false, false);
        }
    }

    public Set<String> getCurrentGroups(UUID uuid) {
        try {
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                return Set.of();
            }

            Set<String> groups = new LinkedHashSet<>();
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                groups.add(node.getGroupName().toLowerCase(Locale.ROOT));
            }
            return groups;
        } catch (Exception e) {
            log.error("[StaffPermissionService] Fehler beim Auslesen der Gruppen für {}", uuid, e);
            return Set.of();
        }
    }

    public String getStaffTrackName() {
        return staffTrackName;
    }

    public String getDefaultTrackName() {
        return defaultTrackName;
    }

    public List<String> getPlayerTrackGroups() {
        return playerTrackGroups;
    }

    private String getLowestRealGroupFromTrack(String trackName) {
        List<String> groups = getGroupsFromTrack(trackName);

        for (String group : groups) {
            if (!isSkippedTechnicalStaffGroup(group)) {
                return group;
            }
        }

        return null;
    }

    private boolean hasAnyRealStaffTrackRank(User user, String trackName) {
        Set<String> realStaffGroups = new LinkedHashSet<>();

        for (String group : getGroupsFromTrack(trackName)) {
            if (!isSkippedTechnicalStaffGroup(group)) {
                realStaffGroups.add(group.toLowerCase(Locale.ROOT));
            }
        }

        if (realStaffGroups.isEmpty()) {
            return false;
        }

        return user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .filter(groupName -> groupName != null)
                .map(groupName -> groupName.toLowerCase(Locale.ROOT))
                .anyMatch(realStaffGroups::contains);
    }

    private boolean isSkippedTechnicalStaffGroup(String groupName) {
        return groupName != null
                && STAFF_TRACK_GROUPS_TO_SKIP.contains(groupName.toLowerCase(Locale.ROOT));
    }

    private List<String> getGroupsFromTrack(String trackName) {
        List<String> result = new ArrayList<>();

        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            log.warn("[StaffPermissionService] LuckPerms-Track '{}' nicht gefunden.", trackName);
            return result;
        }

        for (String groupName : track.getGroups()) {
            Group group = luckPerms.getGroupManager().getGroup(groupName);
            if (group != null) {
                result.add(group.getName());
            } else {
                log.warn("[StaffPermissionService] Gruppe '{}' aus Track '{}' existiert nicht.", groupName, trackName);
            }
        }

        return result;
    }

    private boolean addGroupIfMissing(User user, String groupName, UUID uuid) {
        boolean hasGroup = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(groupName));

        if (hasGroup) {
            return false;
        }

        boolean success = user.data().add(InheritanceNode.builder(groupName).build()).wasSuccessful();
        if (success) {
            log.info("[StaffPermissionService] Gruppe '{}' zu {} hinzugefügt.", groupName, uuid);
        }
        return success;
    }

    private boolean removeGroupIfPresent(User user, String groupName, UUID uuid) {
        boolean changed = false;

        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            if (node.getGroupName().equalsIgnoreCase(groupName)) {
                if (user.data().remove(node).wasSuccessful()) {
                    changed = true;
                    log.info("[StaffPermissionService] Gruppe '{}' von {} entfernt.", groupName, uuid);
                }
            }
        }

        return changed;
    }
}