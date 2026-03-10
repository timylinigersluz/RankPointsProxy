package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.CommandUtils;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class GetPointsCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final LuckPerms luckPerms;
    private final PointsAPI pointsAPI;
    private final OfflinePlayerStore offlineStore;
    private final StafflistManager stafflistManager;
    private final ConfigManager configManager;
    private final LogHelper log;

    public GetPointsCommand(ProxyServer proxy,
                            LuckPerms luckPerms,
                            PointsAPI pointsAPI,
                            OfflinePlayerStore offlineStore,
                            StafflistManager stafflistManager,
                            ConfigManager configManager,
                            LogHelper log) {
        this.proxy = proxy;
        this.luckPerms = luckPerms;
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
            if (!(source instanceof Player player)) {
                source.sendMessage(Component.text("§cNur Spieler können diesen Befehl ohne Argument verwenden."));
                log.debug("GetPointsCommand ohne Argument von Nicht-Spieler ausgeführt: {}", source);
                return;
            }

            uuid = player.getUniqueId();
            playerName = player.getUsername();
            log.debug("GetPointsCommand: Eigene Punkteabfrage von '{}' ({})", playerName, uuid);

        } else if (args.length == 1) {
            String targetName = args[0];
            log.debug("GetPointsCommand: Punkteabfrage für Spieler '{}'", targetName);

            Optional<UUID> uuidOpt = offlineStore.getUUID(targetName);
            if (uuidOpt.isEmpty()) {
                source.sendMessage(Component.text("§cSpieler '" + targetName + "' ist unbekannt."));
                log.debug("GetPointsCommand abgebrochen: Spieler '{}' ist im OfflineStore unbekannt", targetName);
                return;
            }

            uuid = uuidOpt.get();
            playerName = targetName;
            log.debug("GetPointsCommand: UUID für '{}' aufgelöst zu {}", playerName, uuid);

        } else {
            source.sendMessage(Component.text("§cVerwendung: /getpoints [Spielername]"));
            log.debug("GetPointsCommand mit ungültiger Argumentanzahl ausgeführt: {}", args.length);
            return;
        }

        try {
            int points = pointsAPI.getPoints(uuid);
            log.debug("GetPointsCommand: Abgerufene Punkte für '{}' ({}) = {}", playerName, uuid, points);

            if (stafflistManager.isStaff(uuid) && !configManager.isStaffPointsAllowed()) {
                source.sendMessage(Component.text("§eHinweis: " + playerName + " ist Staff. Punkte werden nicht für Ränge gezählt."));
                log.debug("GetPointsCommand: '{}' ({}) ist Staff, Punkte zählen nicht für Ränge (staff.give-points=false)",
                        playerName, uuid);
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("§aDu hast aktuell §b" + points + "§a Punkte."));
                log.info("GetPointsCommand: {} hat eigene Punkte abgefragt: {}", playerName, points);
            } else {
                source.sendMessage(Component.text("§e" + playerName + " §ahat aktuell §b" + points + "§a Punkte."));
                log.info("GetPointsCommand: Punkte für {} ({}) abgefragt: {}", playerName, uuid, points);
            }

        } catch (Exception e) {
            source.sendMessage(Component.text("§cFehler beim Abrufen der Punkte."));
            log.error("GetPointsCommand: Fehler beim Abrufen der Punkte für {}: {}", playerName, e.getMessage());
            log.debug("GetPointsCommand Exception für '{}'", playerName, e);
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
            return CommandUtils.suggestPlayerNames(proxy, luckPerms, args[0]);
        }
        return List.of();
    }
}