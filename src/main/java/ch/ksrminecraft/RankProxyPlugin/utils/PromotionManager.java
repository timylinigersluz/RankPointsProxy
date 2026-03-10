package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.messaging.MessagingService;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kümmert sich um automatische Promotion/Demotion anhand von Punkten.
 *
 * Staff-Wechsel werden zentral über StaffPermissionService geregelt
 * und hier bewusst nicht behandelt.
 */
public class PromotionManager {

    private final LuckPerms luckPerms;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final PointsAPI pointsApi;
    private final LogHelper log;
    private final String defaultGroupName;
    private final ProxyServer server;
    private final Scheduler scheduler;
    private final Object pluginInstance;

    public PromotionManager(
            LuckPerms luckPerms,
            RankManager rankManager,
            StafflistManager stafflistManager,
            PointsAPI pointsApi,
            LogHelper log,
            String defaultGroupName,
            ProxyServer server,
            Scheduler scheduler,
            Object pluginInstance
    ) {
        this.luckPerms = luckPerms;
        this.rankManager = rankManager;
        this.stafflistManager = stafflistManager;
        this.pointsApi = pointsApi;
        this.log = log;
        this.defaultGroupName = defaultGroupName;
        this.server = server;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
    }

    /**
     * Komfort-Wrapper für Online-Spieler.
     */
    public void handleLogin(Player player) {
        if (player != null) {
            handleLogin(player.getUniqueId(), player.getUsername());
        }
    }

    /**
     * Hauptlogik für Promotion/Demotion anhand von Punkten.
     * Staff wird hier bewusst übersprungen.
     */
    public void handleLogin(UUID uuid, String playerName) {
        log.debug("PromotionManager.handleLogin aufgerufen für {} ({})", playerName, uuid);

        final User user;
        try {
            user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                log.warn("PromotionManager: LuckPerms-User für {} ({}) konnte nicht geladen werden", playerName, uuid);
                return;
            }
        } catch (Exception e) {
            log.error("PromotionManager: Konnte LuckPerms-User für {} nicht laden: {}", playerName, e.getMessage());
            log.debug("PromotionManager Exception beim Laden des Users für '{}'", playerName, e);
            return;
        }

        // Staff wird hier bewusst nicht behandelt
        try {
            if (stafflistManager != null && stafflistManager.isStaff(uuid)) {
                log.debug("PromotionManager: {} ({}) ist Staff, normale Promotion-Logik wird übersprungen", playerName, uuid);
                return;
            }
        } catch (Exception e) {
            log.warn("PromotionManager: Konnte Stafflist für {} nicht prüfen – Promotion wird vorsorglich übersprungen: {}",
                    playerName, e.getMessage());
            log.debug("PromotionManager Exception beim Staff-Check für '{}'", playerName, e);
            return;
        }

        // Punkte laden
        final int points;
        try {
            points = pointsApi.getPoints(uuid);
            log.debug("PromotionManager: Punkte für {} geladen: {}", playerName, points);
        } catch (Exception e) {
            log.warn("PromotionManager: Konnte Punkte für {} nicht laden. Standardgruppe '{}' wird gesetzt. Fehler: {}",
                    playerName, defaultGroupName, e.getMessage());
            log.debug("PromotionManager Exception beim Punkteabruf für '{}'", playerName, e);

            ensureDefaultGroup(user, playerName);
            return;
        }

        // Zielrang bestimmen
        var optRank = rankManager.getRankForPoints(points);
        if (optRank.isEmpty()) {
            log.info("PromotionManager: Keine passenden Ränge definiert – Standardgruppe '{}' wird gesetzt für {}",
                    defaultGroupName, playerName);
            ensureDefaultGroup(user, playerName);
            return;
        }

        var targetRank = optRank.get();
        String targetGroup = targetRank.name;

        // aktuellen Rang ermitteln
        String currentRank = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .filter(group -> rankManager.getRankList().stream()
                        .anyMatch(rank -> rank.name.equalsIgnoreCase(group)))
                .findFirst()
                .orElse(null);

        log.trace("PromotionManager: currentRank={}, targetGroup={} für {}", currentRank, targetGroup, playerName);

        // prüfen, ob Spieler schon im Zielrang ist
        boolean alreadyInTarget = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(targetGroup));

        if (alreadyInTarget) {
            log.debug("PromotionManager: {} ist bereits in der Zielgruppe '{}'", playerName, targetGroup);
            return;
        }

        boolean isDemotion = isDemotion(currentRank, targetGroup);

        // Race-Condition-Schutz:
        // Falls der Spieler inzwischen Staff geworden ist, abbrechen.
        try {
            if (stafflistManager != null && stafflistManager.isStaff(uuid)) {
                log.debug("PromotionManager: {} wurde während der Promotion-Prüfung Staff. Normale Rangvergabe wird abgebrochen",
                        playerName);
                return;
            }
        } catch (Exception e) {
            log.warn("PromotionManager: Konnte Stafflist für {} vor dem Schreiben nicht erneut prüfen – Promotion wird abgebrochen: {}",
                    playerName, e.getMessage());
            log.debug("PromotionManager Exception beim zweiten Staff-Check für '{}'", playerName, e);
            return;
        }

        // alte Ranggruppen entfernen
        var rankGroupNames = getRankGroupNamesLowercase();

        user.getNodes(NodeType.INHERITANCE).stream()
                .filter(n -> rankGroupNames.contains(n.getGroupName().toLowerCase()))
                .forEach(user.data()::remove);

        // neue Ranggruppe setzen
        user.data().add(InheritanceNode.builder(targetGroup).build());

        try {
            luckPerms.getUserManager().saveUser(user).join();
            log.debug("PromotionManager: LuckPerms-User für {} gespeichert", playerName);
        } catch (Exception e) {
            log.error("PromotionManager: Konnte LuckPerms-Änderungen für {} nicht speichern: {}", playerName, e.getMessage());
            log.debug("PromotionManager Exception beim saveUser für '{}'", playerName, e);
            return;
        }

        // LuckPerms Messaging
        pushLuckPermsUserUpdate(user, playerName);

        if (isDemotion) {
            log.info("PromotionManager: Demotion für {} -> {}", playerName, targetGroup);
        } else {
            log.info("PromotionManager: Promotion für {} -> {}", playerName, targetGroup);
        }

        // Nachricht nur bei Online-Spielern
        server.getPlayer(uuid).ifPresent(player -> {
            if (isDemotion) {
                PromotionMessageSender.sendDemotion(player, targetGroup, scheduler, pluginInstance);
            } else {
                PromotionMessageSender.sendPromotion(player, targetGroup, scheduler, pluginInstance);
            }
        });
    }

    private void pushLuckPermsUserUpdate(User user, String playerName) {
        try {
            var messagingOpt = luckPerms.getMessagingService();

            if (messagingOpt.isPresent()) {
                MessagingService messagingService = messagingOpt.get();
                messagingService.pushUserUpdate(user);
                log.debug("PromotionManager: LuckPerms pushUserUpdate für {} ausgelöst", playerName);
            } else {
                log.warn("PromotionManager: Kein LuckPerms MessagingService verfügbar – User-Update für {} konnte nicht gepusht werden",
                        playerName);
            }
        } catch (Exception e) {
            log.warn("PromotionManager: Konnte LuckPerms User-Update für {} nicht pushen: {}", playerName, e.getMessage());
            log.debug("PromotionManager Exception bei pushUserUpdate für '{}'", playerName, e);
        }
    }

    private Set<String> getRankGroupNamesLowercase() {
        return rankManager.getRankList().stream()
                .map(r -> r.name.toLowerCase())
                .collect(Collectors.toSet());
    }

    private boolean isDemotion(String currentRank, String targetRank) {
        if (currentRank == null || targetRank == null) {
            return false;
        }

        int currentIndex = getRankIndex(currentRank);
        int targetIndex = getRankIndex(targetRank);

        if (currentIndex == -1 || targetIndex == -1) {
            return false;
        }

        return targetIndex < currentIndex;
    }

    private int getRankIndex(String rankName) {
        for (int i = 0; i < rankManager.getRankList().size(); i++) {
            if (rankManager.getRankList().get(i).name.equalsIgnoreCase(rankName)) {
                return i;
            }
        }
        return -1;
    }

    private void ensureDefaultGroup(User user, String playerName) {
        boolean inDefault = user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(n -> n.getGroupName().equalsIgnoreCase(defaultGroupName));

        if (!inDefault) {
            user.data().add(InheritanceNode.builder(defaultGroupName).build());

            try {
                luckPerms.getUserManager().saveUser(user).join();
                pushLuckPermsUserUpdate(user, playerName);
                log.info("PromotionManager: {} wurde in die Standard-Laufbahn '{}' gesetzt", playerName, defaultGroupName);
            } catch (Exception e) {
                log.error("PromotionManager: Konnte Standard-Laufbahn '{}' für {} nicht speichern: {}",
                        defaultGroupName, playerName, e.getMessage());
                log.debug("PromotionManager Exception bei ensureDefaultGroup für '{}'", playerName, e);
            }
        } else {
            log.debug("PromotionManager: {} ist bereits in der Standard-Laufbahn '{}'", playerName, defaultGroupName);
        }
    }
}