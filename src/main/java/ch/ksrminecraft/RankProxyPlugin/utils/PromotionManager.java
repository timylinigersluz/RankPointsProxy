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
 * - Stafflist-Spieler sind ausgeschlossen
 * - Alle Aktionen werden mit LogHelper protokolliert
 */
public class PromotionManager {

    private final LuckPerms luckPerms;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final PointsAPI pointsApi;
    private final LogHelper log;

    public PromotionManager(
            LuckPerms luckPerms,
            RankManager rankManager,
            StafflistManager stafflistManager,
            PointsAPI pointsApi,
            LogHelper log
    ) {
        this.luckPerms = luckPerms;
        this.rankManager = rankManager;
        this.stafflistManager = stafflistManager;
        this.pointsApi = pointsApi;
        this.log = log;
    }

    // Komfort-Wrapper: falls noch mit Player-Objekt gearbeitet wird
    public void handleLogin(Player player) {
        if (player != null) {
            handleLogin(player.getUniqueId(), player.getUsername());
        }
    }

    private boolean isStaffExcluded(UUID uuid, String nameForLog) {
        try {
            if (stafflistManager != null && stafflistManager.isStaff(uuid)) {
                log.info("→ RankSync übersprungen für {}: steht auf der Stafflist.", nameForLog);
                return true;
            }
        } catch (Exception e) {
            // Fail-safe: Wenn Prüfung fehlschlägt, vorsorglich keine Änderung machen
            log.warn("Konnte Stafflist für {} nicht prüfen – überspringe Promotion. Fehler: {}", nameForLog, e.getMessage());
            return true;
        }
        return false;
    }

    /**
     * Hauptlogik für Promotion/Demotion.
     * Wird bei Login / Serverwechsel / zyklischem Check / manuellem Trigger aufgerufen.
     */
    public void handleLogin(UUID uuid, String playerName) {
        log.debug("→ handleLogin() aufgerufen für {}", playerName);

        // 1) Ausschluss: Stafflist
        if (isStaffExcluded(uuid, playerName)) {
            log.debug("→ Kein Rangabgleich für {} (Stafflist).", playerName);
            return;
        }

        // 2) Punkte laden
        final int points;
        try {
            points = pointsApi.getPoints(uuid);
            log.debug("→ Punkte für {} geladen: {}", playerName, points);
        } catch (Exception e) {
            log.warn("→ Konnte Punkte für {} nicht laden – überspringe Promotion. Fehler: {}", playerName, e.getMessage());
            return;
        }

        // 3) Zielrang bestimmen
        var optRank = rankManager.getRankForPoints(points);
        if (optRank.isEmpty()) {
            log.debug("→ Keine Ränge definiert – überspringe Promotion für {}.", playerName);
            return;
        }
        var targetRank = optRank.get();
        String targetGroup = targetRank.name;

        // 4) Aktuelle LuckPerms-Gruppen prüfen
        User user;
        try {
            user = luckPerms.getUserManager().loadUser(uuid).join();
        } catch (Exception e) {
            log.error("→ Konnte LuckPerms-User für {} nicht laden: {}", playerName, e.getMessage());
            return;
        }

        boolean alreadyInTarget = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .anyMatch(g -> g.equalsIgnoreCase(targetGroup));

        if (alreadyInTarget) {
            log.debug("→ {} ist bereits in der Zielgruppe '{}'. Keine Änderung.", playerName, targetGroup);
            return;
        }

        // 5) Nur deine Rang-Gruppen entfernen
        var rankGroupNames = rankManager.getRankList().stream()
                .map(r -> r.name.toLowerCase())
                .toList();

        user.getNodes(NodeType.INHERITANCE).stream()
                .filter(n -> rankGroupNames.contains(n.getGroupName().toLowerCase()))
                .forEach(n -> user.data().remove(n));

        // 6) Zielgruppe hinzufügen
        user.data().add(InheritanceNode.builder(targetGroup).build());

        // 7) Änderungen speichern
        luckPerms.getUserManager().saveUser(user);

        log.info("→ Promotion erkannt für {}: → {}", playerName, targetGroup);
    }
}
