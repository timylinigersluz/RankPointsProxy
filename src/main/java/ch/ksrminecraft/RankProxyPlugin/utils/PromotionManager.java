package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;

import java.util.UUID;

public class PromotionManager {

    private final LuckPerms luckPerms;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final PointsAPI pointsApi;
    private final Logger logger;

    public PromotionManager(
            LuckPerms luckPerms,
            RankManager rankManager,
            StafflistManager stafflistManager,
            PointsAPI pointsApi,
            Logger logger
    ) {
        this.luckPerms = luckPerms;
        this.rankManager = rankManager;
        this.stafflistManager = stafflistManager;
        this.pointsApi = pointsApi;
        this.logger = logger;
    }

    // Komfort: falls irgendwo noch mit Player gearbeitet wird
    public void handleLogin(Player player) {
        if (player == null) return;
        handleLogin(player.getUniqueId(), player.getUsername());
    }

    private boolean isStaffExcluded(UUID uuid, String nameForLog) {
        try {
            if (stafflistManager != null && stafflistManager.isStaff(uuid)) {
                logger.info("→ RankSync übersprungen für {}: steht auf der Stafflist.", nameForLog);
                return true;
            }
        } catch (Exception e) {
            // Fail-safe: wenn Prüfung fehlschlägt, vorsorglich keine Änderung machen
            logger.warn("Konnte Stafflist für {} nicht prüfen – überspringe vorsorglich Promotion.", nameForLog, e);
            return true;
        }
        return false;
    }

    /**
     * Aufruf bei Login / Serverwechsel / zyklischer Check (Scheduler) / manueller Trigger.
     * Führt nur eine Promotion/Demotion durch, wenn der Spieler NICHT auf der Stafflist ist.
     */
    public void handleLogin(UUID uuid, String playerName) {
        logger.info("→ handleLogin() aufgerufen für {}", playerName);

        // 1) Ausschluss: Stafflist
        if (isStaffExcluded(uuid, playerName)) {
            logger.info("→ Kein Rangabgleich für {} (Stafflist).", playerName);
            return;
        }

        // 2) Punkte laden
        final int points;
        try {
            points = pointsApi.getPoints(uuid);
        } catch (Exception e) {
            logger.warn("→ Konnte Punkte für {} nicht laden – überspringe Promotion.", playerName, e);
            return;
        }

        // 3) Zielrang bestimmen
        var optRank = rankManager.getRankForPoints(points);
        if (optRank.isEmpty()) {
            logger.info("→ Keine Ränge definiert – überspringe Promotion für {}.", playerName);
            return;
        }
        var targetRank = optRank.get();
        String targetGroup = targetRank.name;

        // 4) Aktuelle LP-Gruppen prüfen
        User user = luckPerms.getUserManager().loadUser(uuid).join();
        boolean alreadyInTarget = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .anyMatch(g -> g.equalsIgnoreCase(targetGroup));

        if (alreadyInTarget) {
            logger.info("→ {} ist bereits in der Zielgruppe '{}'. Keine Änderung.", playerName, targetGroup);
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

        // 7) Speichern
        luckPerms.getUserManager().saveUser(user);

        logger.info("→ Promotion erkannt für {}: → {}", playerName, targetGroup);
    }
}
