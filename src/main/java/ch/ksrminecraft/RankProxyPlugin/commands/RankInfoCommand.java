package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager.RankProgressInfo;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

import net.kyori.adventure.text.Component;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RankInfoCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final ConfigManager config;
    private final LogHelper log;
    private final LuckPerms luckPerms;

    public RankInfoCommand(PointsAPI pointsAPI,
                           RankManager rankManager,
                           StafflistManager stafflistManager,
                           ConfigManager config,
                           LogHelper log,
                           LuckPerms luckPerms) {
        this.pointsAPI = pointsAPI;
        this.rankManager = rankManager;
        this.stafflistManager = stafflistManager;
        this.config = config;
        this.log = log;
        this.luckPerms = luckPerms;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("§cDieser Befehl ist nur für Spieler verfügbar."));
            log.warn("RankInfoCommand wurde von {} ausgeführt, aber Quelle ist kein Spieler.", source);
            return;
        }

        UUID uuid = player.getUniqueId();
        final int points;
        try {
            points = pointsAPI.getPoints(uuid);
        } catch (Exception e) {
            player.sendMessage(Component.text("§cFehler beim Laden deiner Punkte."));
            log.error("RankInfoCommand: Fehler beim Abrufen der Punkte für {}: {}", player.getUsername(), e.getMessage(), e);
            return;
        }

        // ---- Staff-Zweig: Konkreten Rang aus der Staff-Laufbahn anzeigen, nur Punkte ausgeben ----
        try {
            if (stafflistManager.isStaff(uuid)) {
                String trackName = safeStaffTrackName();
                String staffRank = resolveStaffRank(uuid, trackName);
                if (staffRank == null) {
                    // Fallback, falls kein Rang aus der Laufbahn ermittelt werden konnte
                    staffRank = trackName;
                }

                player.sendMessage(Component.text("§aDein Staff-Rang: §e" + staffRank));
                player.sendMessage(Component.text("§aDeine Punkte: §b" + points));
                log.info("RankInfoCommand (Staff): {} hat Staff-Rang '{}' und {} Punkte.",
                        player.getUsername(), staffRank, points);
                return; // Keine Anzeige von „nächster Rang“ etc. bei Staff
            }
        } catch (Exception e) {
            // Wenn der Staff-Check fehlschlägt, fahre mit normalem Zweig fort
            log.warn("RankInfoCommand: Staff-Check für {} fehlgeschlagen, fahre mit normalem Rang fort. Fehler: {}",
                    player.getUsername(), e.getMessage());
        }

        // ---- Normaler Spieler-Zweig ----
        Optional<RankProgressInfo> progressOpt = rankManager.getRankProgress(points);
        if (progressOpt.isEmpty()) {
            player.sendMessage(Component.text("§cKeine Ränge definiert."));
            log.warn("RankInfoCommand: Spieler {} hat keine passenden Ränge ({} Punkte).", player.getUsername(), points);
            return;
        }

        RankProgressInfo info = progressOpt.get();
        String current = (info.currentRank != null) ? info.currentRank.name : "Keine";
        String next = (info.nextRank != null) ? info.nextRank.name : "Keiner";
        int remaining = info.pointsUntilNext;

        player.sendMessage(Component.text("§aDein aktueller Rang: §e" + current));
        player.sendMessage(Component.text("§aNächster Rang: §e" + next));
        if (info.nextRank != null) {
            player.sendMessage(Component.text("§aNoch §e" + remaining + " §aPunkte bis zum nächsten Rang."));
            log.info("RankInfoCommand: {} hat Rang '{}' und benötigt {} Punkte bis '{}'.",
                    player.getUsername(), current, remaining, next);
        } else {
            player.sendMessage(Component.text("§aDu hast den höchsten Rang erreicht!"));
            log.info("RankInfoCommand: {} hat den höchsten Rang '{}' erreicht.", player.getUsername(), current);
        }
    }

    /**
     * Ermittelt den konkreten Staff-Rang des Users innerhalb der konfigurierten Staff-Laufbahn.
     * Nimmt den am weitesten „oben“ liegenden Rang innerhalb der Track-Reihenfolge.
     */
    private String resolveStaffRank(UUID uuid, String trackName) {
        if (luckPerms == null || trackName == null || trackName.isBlank()) return null;

        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            log.warn("RankInfoCommand: Staff-Track '{}' existiert nicht in LuckPerms.", trackName);
            return null;
        }

        List<String> trackGroups = track.getGroups(); // in Reihenfolge der Laufbahn
        if (trackGroups == null || trackGroups.isEmpty()) return null;

        User user = luckPerms.getUserManager().loadUser(uuid).join();
        if (user == null) return null;

        String best = null;
        int bestIndex = -1;

        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            String g = node.getGroupName();
            int idx = trackGroups.indexOf(g);
            if (idx >= 0 && idx > bestIndex) {
                bestIndex = idx;
                best = g;
            }
        }
        return best; // kann null sein, wenn Spieler zwar Staff ist, aber (noch) keine Gruppe der Laufbahn besitzt
    }

    private String safeStaffTrackName() {
        try {
            return config.getStaffGroupName(); // Erwarteter Config-Key, z.B. staff.track: "staff"
        } catch (Throwable t) {
            // Fallback für ältere Config-Versionen
            return "staff";
        }
    }
}
