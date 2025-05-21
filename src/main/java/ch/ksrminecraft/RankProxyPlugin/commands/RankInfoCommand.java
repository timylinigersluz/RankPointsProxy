package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager;
import ch.ksrminecraft.RankProxyPlugin.utils.RankManager.RankProgressInfo;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class RankInfoCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final RankManager rankManager;

    public RankInfoCommand(PointsAPI pointsAPI, RankManager rankManager) {
        this.pointsAPI = pointsAPI;
        this.rankManager = rankManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Dieser Befehl ist nur für Spieler verfügbar."));
            return;
        }

        UUID uuid = player.getUniqueId();
        int points = pointsAPI.getPoints(uuid);

        Optional<RankProgressInfo> progressOpt = rankManager.getRankProgress(points);
        if (progressOpt.isEmpty()) {
            player.sendMessage(Component.text("§cKeine Ränge definiert."));
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
        } else {
            player.sendMessage(Component.text("§aDu hast den höchsten Rang erreicht!"));
        }
    }
}
