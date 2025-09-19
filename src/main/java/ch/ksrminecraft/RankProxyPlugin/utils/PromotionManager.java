package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.UUID;

/**
 * PromotionManager
 *
 * Kümmert sich um automatische Promotion/Demotion von Spielern
 * basierend auf den Punkten aus der PointsAPI und den definierten Rängen.
 * - Stafflist-Spieler sind ausgeschlossen von Punktelogik,
 *   aber sie werden automatisch in die Staff-Laufbahn gesetzt.
 * - Alle anderen kommen mindestens in die Standard-Laufbahn "player".
 */
public class PromotionManager {

    private final LuckPerms luckPerms;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final PointsAPI pointsApi;
    private final LogHelper log;
    private final String staffGroupName;
    private final String defaultGroupName;

    public PromotionManager(
            LuckPerms luckPerms,
            RankManager rankManager,
            StafflistManager stafflistManager,
            PointsAPI pointsApi,
            LogHelper log,
            String staffGroupName,
            String defaultGroupName
    ) {
        this.luckPerms = luckPerms;
        this.rankManager = rankManager;
        this.stafflistManager = stafflistManager;
        this.pointsApi = pointsApi;
        this.log = log;
        this.staffGroupName = staffGroupName;
        this.defaultGroupName = defaultGroupName;
    }

    // Komfort-Wrapper
    public void handleLogin(Player player) {
        if (player != null) {
            handleLogin(player.getUniqueId(), player.getUsername());
        }
    }

    /**
     * Hauptlogik für Promotion/Demotion.
     * Wird bei Login / Serverwechsel / zyklischem Check / manuellem Trigger aufgerufen.
     */
    public void handleLogin(UUID uuid, String playerName) {
        log.debug("→ handleLogin() aufgerufen für {}", playerName);

        User user;
        try {
            user = luckPerms.getUserManager().loadUser(uuid).join();
        } catch (Exception e) {
            log.error("→ Konnte LuckPerms-User für {} nicht laden: {}", playerName, e.getMessage());
            return;
        }

        // --- Staff ---
        try {
            if (stafflistManager != null && stafflistManager.isStaff(uuid)) {
                boolean inStaffGroup = user.getNodes(NodeType.INHERITANCE).stream()
                        .anyMatch(n -> n.getGroupName().equalsIgnoreCase(staffGroupName));

                if (!inStaffGroup) {
                    user.data().add(InheritanceNode.builder(staffGroupName).build());
                    luckPerms.getUserManager().saveUser(user);
                    log.info("→ {} zur Staff-Laufbahn '{}' hinzugefügt.", playerName, staffGroupName);
                } else {
                    log.debug("→ {} ist bereits in der Staff-Laufbahn '{}'.", playerName, staffGroupName);
                }
                return; // Staff: keine Punkte-Logik
            }
        } catch (Exception e) {
            log.warn("Konnte Stafflist für {} nicht prüfen – überspringe vorsorglich Promotion. Fehler: {}",
                    playerName, e.getMessage());
            return;
        }

        // --- Punkte laden ---
        final int points;
        try {
            points = pointsApi.getPoints(uuid);
            log.debug("→ Punkte für {} geladen: {}", playerName, points);
        } catch (Exception e) {
            log.warn("→ Konnte Punkte für {} nicht laden. Standardgruppe '{}' wird gesetzt. Fehler: {}",
                    playerName, defaultGroupName, e.getMessage());
            ensureDefaultGroup(user, playerName);
            return;
        }

        // --- Zielrang bestimmen ---
        var optRank = rankManager.getRankForPoints(points);
        if (optRank.isEmpty()) {
            log.info("→ Keine passenden Ränge definiert – Standardgruppe '{}' wird gesetzt für {}.",
                    defaultGroupName, playerName);
            ensureDefaultGroup(user, playerName);
            return;
        }

        var targetRank = optRank.get();
        String targetGroup = targetRank.name;

        // --- Prüfen, ob Spieler schon dort ist ---
        boolean alreadyInTarget = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(targetGroup));

        if (alreadyInTarget) {
            log.debug("→ {} ist bereits in der Zielgruppe '{}'.", playerName, targetGroup);
            return;
        }

        // --- Alte Ranggruppen entfernen ---
        var rankGroupNames = rankManager.getRankList().stream()
                .map(r -> r.name.toLowerCase())
                .toList();

        user.getNodes(NodeType.INHERITANCE).stream()
                .filter(n -> rankGroupNames.contains(n.getGroupName().toLowerCase()))
                .forEach(user.data()::remove);

        // --- Neue Gruppe hinzufügen ---
        user.data().add(InheritanceNode.builder(targetGroup).build());

        // --- Speichern ---
        luckPerms.getUserManager().saveUser(user);

        log.info("→ Promotion erkannt für {}: → {}", playerName, targetGroup);
    }

    private void ensureDefaultGroup(User user, String playerName) {
        boolean inDefault = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(defaultGroupName));

        if (!inDefault) {
            user.data().add(InheritanceNode.builder(defaultGroupName).build());
            luckPerms.getUserManager().saveUser(user);
            log.info("→ {} wurde in die Standard-Laufbahn '{}' gesetzt.", playerName, defaultGroupName);
        } else {
            log.debug("→ {} ist bereits in der Standard-Laufbahn '{}'.", playerName, defaultGroupName);
        }
    }
}
