package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.*;

public class GetPointsCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final OfflinePlayerStore offlineStore;
    private final StafflistManager stafflistManager;
    private final ConfigManager configManager;
    private final LogHelper log; // wird über Main übergeben

    public GetPointsCommand(PointsAPI pointsAPI,
                            OfflinePlayerStore offlineStore,
                            StafflistManager stafflistManager,
                            ConfigManager configManager,
                            LogHelper log) {
        this.pointsAPI = pointsAPI;
        this.offlineStore = offlineStore;
        this.stafflistManager = stafflistManager;
        this.configManager = configManager;
        this.log = log;
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

            // Hinweis für Staff, falls Punkte eigentlich gesperrt sind
            if (stafflistManager.isStaff(uuid) && !configManager.isStaffPointsAllowed()) {
                source.sendMessage(Component.text("§eHinweis: " + playerName + " ist Staff. Punkte werden nicht für Ränge gezählt."));
                log.debug("Queried points for Staff {} ({}). Points={} (config: staff.give-points=false)", playerName, uuid, points);
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("§aDu hast aktuell §b" + points + "§a Punkte."));
                log.info("Player {} queried own points: {}", playerName, points);
            } else {
                source.sendMessage(Component.text("§e" + playerName + " §ahat aktuell §b" + points + "§a Punkte."));
                log.info("Queried points for {} ({}): {}", playerName, uuid, points);
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("§cFehler beim Abrufen der Punkte."));
            log.error("Failed to get points for {}: {}", playerName, e.getMessage());
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
