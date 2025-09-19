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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

        // eigener RankInfo
        if (args.length == 0) {
            if (!(source instanceof Player self)) {
                source.sendMessage(Component.text("§cBitte gib einen Spielernamen an."));
                return;
            }
            showRankInfo(source, self.getUniqueId(), self.getUsername());
            return;
        }

        // fremder RankInfo nur mit Permission
        if (!source.hasPermission("rankproxyplugin.rankinfo.others")) {
            source.sendMessage(Component.text("§cDafür hast du keine Berechtigung."));
            return;
        }

        String nameArg = args[0];

        // Online zuerst
        Optional<Player> online = proxy.getPlayer(nameArg);
        if (online.isPresent()) {
            Player target = online.get();
            showRankInfo(source, target.getUniqueId(), target.getUsername());
            return;
        }

        // Offline via LuckPerms
        CompletableFuture<UUID> uuidFuture = luckPerms.getUserManager().lookupUniqueId(nameArg);
        uuidFuture.thenAccept(uuid -> {
            if (uuid == null) {
                source.sendMessage(Component.text("§cSpieler '" + nameArg + "' nicht gefunden."));
                return;
            }
            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                String lastName = (user != null && user.getUsername() != null) ? user.getUsername() : nameArg;
                showRankInfo(source, uuid, lastName);
            });
        });
    }

    private void showRankInfo(CommandSource viewer, UUID uuid, String name) {
        final int points;
        try {
            points = pointsAPI.getPoints(uuid);
        } catch (Exception e) {
            viewer.sendMessage(Component.text("§cFehler beim Laden der Punkte von " + name + "."));
            log.error("RankInfoCommand: Fehler beim Abrufen der Punkte für {}: {}", name, e.getMessage(), e);
            return;
        }

        // Staff-Pfad
        try {
            if (stafflistManager.isStaff(uuid)) {
                String trackName = safeStaffTrackName();
                String staffRank = resolveStaffRank(uuid, trackName);
                if (staffRank == null) staffRank = trackName;

                viewer.sendMessage(Component.text("§aStaff-Rang von §e" + name + "§a: §e" + staffRank));
                viewer.sendMessage(Component.text("§aPunkte: §b" + points));
                return;
            }
        } catch (Exception e) {
            log.warn("RankInfoCommand: Staff-Check für {} fehlgeschlagen: {}", name, e.getMessage());
        }

        // Normaler Rang
        Optional<RankProgressInfo> progressOpt = rankManager.getRankProgress(points);
        if (progressOpt.isEmpty()) {
            viewer.sendMessage(Component.text("§cKeine Ränge definiert."));
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
    }

    private String resolveStaffRank(UUID uuid, String trackName) {
        if (luckPerms == null || trackName == null || trackName.isBlank()) return null;
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) return null;

        List<String> trackGroups = track.getGroups();
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
        return best;
    }

    private String safeStaffTrackName() {
        try {
            return config.getStaffGroupName();
        } catch (Throwable t) {
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
