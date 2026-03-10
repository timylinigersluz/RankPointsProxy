package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.CommandUtils;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager.RankProgressInfo;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RankInfoCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final PointsAPI pointsAPI;
    private final RankManager rankManager;
    private final StafflistManager stafflistManager;
    private final ConfigManager config;
    private final LogHelper log;
    private final LuckPerms luckPerms;

    public RankInfoCommand(ProxyServer proxy,
                           PointsAPI pointsAPI,
                           RankManager rankManager,
                           StafflistManager stafflistManager,
                           ConfigManager config,
                           LogHelper log,
                           LuckPerms luckPerms) {
        this.proxy = proxy;
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
        String[] args = invocation.arguments();

        if (args.length == 0) {
            if (!(source instanceof Player self)) {
                source.sendMessage(Component.text("§cBitte gib einen Spielernamen an."));
                log.debug("RankInfoCommand ohne Argument von Nicht-Spieler ausgeführt: {}", source);
                return;
            }

            log.debug("RankInfoCommand: Eigene Rangabfrage von '{}' ({})", self.getUsername(), self.getUniqueId());
            showRankInfo(source, self.getUniqueId(), self.getUsername());
            return;
        }

        if (!source.hasPermission("rankproxyplugin.rankinfo.others")) {
            source.sendMessage(Component.text("§cDafür hast du keine Berechtigung."));
            log.debug("RankInfoCommand: {} wollte Ranginfo für andere ohne Berechtigung abrufen", source);
            return;
        }

        String nameArg = args[0];
        log.debug("RankInfoCommand: Fremde Rangabfrage für '{}'", nameArg);

        Optional<Player> online = proxy.getPlayer(nameArg);
        if (online.isPresent()) {
            Player target = online.get();
            log.debug("RankInfoCommand: Spieler '{}' ist online, verwende UUID {}", target.getUsername(), target.getUniqueId());
            showRankInfo(source, target.getUniqueId(), target.getUsername());
            return;
        }

        log.debug("RankInfoCommand: Spieler '{}' nicht online, versuche Lookup via LuckPerms", nameArg);

        CompletableFuture<UUID> uuidFuture = luckPerms.getUserManager().lookupUniqueId(nameArg);
        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                source.sendMessage(Component.text("§cSpieler '" + nameArg + "' nicht gefunden."));
                log.debug("RankInfoCommand: Kein UUID-Lookup-Ergebnis für '{}'", nameArg);
                return;
            }

            log.debug("RankInfoCommand: UUID für '{}' via LuckPerms gefunden: {}", nameArg, uuid);

            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                String lastName = (user != null && user.getUsername() != null) ? user.getUsername() : nameArg;
                log.debug("RankInfoCommand: Geladener Benutzer für UUID {} ist '{}'", uuid, lastName);
                showRankInfo(source, uuid, lastName);
            }).exceptionally(ex -> {
                source.sendMessage(Component.text("§cFehler beim Laden des Spielers '" + nameArg + "'."));
                log.error("RankInfoCommand: Fehler beim Laden des LuckPerms-Users für {}: {}", nameArg, ex.getMessage());
                log.debug("RankInfoCommand Exception beim loadUser für '{}'", nameArg, ex);
                return null;
            });
        }).exceptionally(ex -> {
            source.sendMessage(Component.text("§cFehler beim Suchen von '" + nameArg + "'."));
            log.error("RankInfoCommand: Fehler beim UUID-Lookup für {}: {}", nameArg, ex.getMessage());
            log.debug("RankInfoCommand Exception beim lookupUniqueId für '{}'", nameArg, ex);
            return null;
        });
    }

    private void showRankInfo(CommandSource viewer, UUID uuid, String name) {
        final int points;
        try {
            points = pointsAPI.getPoints(uuid);
            log.debug("RankInfoCommand: Punkte für '{}' ({}) = {}", name, uuid, points);
        } catch (Exception e) {
            viewer.sendMessage(Component.text("§cFehler beim Laden der Punkte von " + name + "."));
            log.error("RankInfoCommand: Fehler beim Abrufen der Punkte für {}: {}", name, e.getMessage());
            log.debug("RankInfoCommand Exception beim Punkteabruf für '{}'", name, e);
            return;
        }

        try {
            if (stafflistManager.isStaff(uuid)) {
                log.debug("RankInfoCommand: '{}' ({}) ist Staff", name, uuid);

                String trackName = safeStaffTrackName();
                String staffRank = resolveStaffRank(uuid, trackName);
                if (staffRank == null) {
                    staffRank = trackName;
                }

                viewer.sendMessage(Component.text("§aStaff-Rang von §e" + name + "§a: §e" + staffRank));
                viewer.sendMessage(Component.text("§aPunkte: §b" + points));

                log.info("RankInfoCommand: Staff-Ranginfo für {} ({}) angezeigt: Rang={}, Punkte={}",
                        name, uuid, staffRank, points);
                return;
            }
        } catch (Exception e) {
            log.warn("RankInfoCommand: Staff-Check für {} fehlgeschlagen: {}", name, e.getMessage());
            log.debug("RankInfoCommand Exception beim Staff-Check für '{}'", name, e);
        }

        Optional<RankProgressInfo> progressOpt = rankManager.getRankProgress(points);
        if (progressOpt.isEmpty()) {
            viewer.sendMessage(Component.text("§cKeine Ränge definiert."));
            log.warn("RankInfoCommand: Keine Ränge definiert, konnte Ranginfo für {} ({}) nicht anzeigen", name, uuid);
            return;
        }

        RankProgressInfo info = progressOpt.get();
        String current = (info.currentRank != null) ? info.currentRank.name : "Keine";
        String next = (info.nextRank != null) ? info.nextRank.name : "Keiner";
        int remaining = info.pointsUntilNext;

        viewer.sendMessage(Component.text("§aAktueller Rang von §e" + name + "§a: §e" + current));
        viewer.sendMessage(Component.text("§aNächster Rang: §e" + next));
        if (info.nextRank != null) {
            viewer.sendMessage(Component.text("§aNoch §e" + remaining + " §aPunkte bis zum nächsten Rang."));
        } else {
            viewer.sendMessage(Component.text("§a§e" + name + " §ahat den höchsten Rang erreicht!"));
        }

        log.info("RankInfoCommand: Ranginfo für {} ({}) angezeigt: current={}, next={}, remaining={}",
                name, uuid, current, next, remaining);
    }

    private String resolveStaffRank(UUID uuid, String trackName) {
        if (luckPerms == null || trackName == null || trackName.isBlank()) {
            log.debug("RankInfoCommand: resolveStaffRank abgebrochen für {} - luckPerms oder trackName ungültig", uuid);
            return null;
        }

        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) {
            log.debug("RankInfoCommand: Staff-Track '{}' existiert nicht", trackName);
            return null;
        }

        List<String> trackGroups = track.getGroups();
        if (trackGroups == null || trackGroups.isEmpty()) {
            log.debug("RankInfoCommand: Staff-Track '{}' enthält keine Gruppen", trackName);
            return null;
        }

        User user = luckPerms.getUserManager().loadUser(uuid).join();
        if (user == null) {
            log.debug("RankInfoCommand: Kein LuckPerms-User für UUID {} gefunden", uuid);
            return null;
        }

        String best = null;
        int bestIndex = -1;

        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            String groupName = node.getGroupName();
            int idx = trackGroups.indexOf(groupName);

            log.trace("RankInfoCommand: Prüfe Inheritance-Node '{}' für UUID {}, TrackIndex={}",
                    groupName, uuid, idx);

            if (idx >= 0 && idx > bestIndex) {
                bestIndex = idx;
                best = groupName;
            }
        }

        log.debug("RankInfoCommand: Ermittelter Staff-Rang für {} = {}", uuid, best);
        return best;
    }

    private String safeStaffTrackName() {
        try {
            return config.getStaffGroupName();
        } catch (Throwable t) {
            log.warn("RankInfoCommand: Konnte Staff-Track nicht aus Config laden, verwende Fallback 'staff'");
            log.debug("RankInfoCommand Exception in safeStaffTrackName", t);
            return "staff";
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return CommandUtils.suggestPlayerNames(proxy, luckPerms, args[0]);
        }
        return List.of();
    }
}