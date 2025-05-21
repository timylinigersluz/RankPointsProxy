package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.*;

public class GetPointsCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final OfflinePlayerStore offlineStore;

    public GetPointsCommand(PointsAPI pointsAPI, OfflinePlayerStore offlineStore) {
        this.pointsAPI = pointsAPI;
        this.offlineStore = offlineStore;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        UUID uuid;
        String playerName;

        if (args.length == 0) {
            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("§cNur Spieler können diesen Befehl ohne Argument verwenden."));
                return;
            }
            Player player = (Player) source;
            uuid = player.getUniqueId();
            playerName = player.getUsername();
        } else if (args.length == 1) {
            String targetName = args[0];
            Optional<UUID> uuidOpt = offlineStore.getUUID(targetName);
            if (uuidOpt.isEmpty()) {
                source.sendMessage(Component.text("§cSpieler '" + targetName + "' ist unbekannt."));
                return;
            }
            uuid = uuidOpt.get();
            playerName = targetName;
        } else {
            source.sendMessage(Component.text("§cVerwendung: /getpoints [Spielername]"));
            return;
        }

        try {
            int points = pointsAPI.getPoints(uuid);
            if (args.length == 0) {
                source.sendMessage(Component.text("§aDu hast aktuell §b" + points + "§a Punkte."));
            } else {
                source.sendMessage(Component.text("§e" + playerName + " §ahat aktuell §b" + points + "§a Punkte."));
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("§cFehler beim Abrufen der Punkte."));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.getpoints");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return offlineStore.getAllNamesStartingWith(args[0]);
        }
        return List.of();
    }
}
