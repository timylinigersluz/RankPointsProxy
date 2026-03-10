package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.CommandUtils;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionManager;
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

public class AddPointsCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final LuckPerms luckPerms;
    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final OfflinePlayerStore offlineStore;
    private final ConfigManager configManager;
    private final PromotionManager promotionManager;
    private final LogHelper log;

    public AddPointsCommand(ProxyServer proxy,
                            LuckPerms luckPerms,
                            PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            OfflinePlayerStore offlineStore,
                            ConfigManager configManager,
                            PromotionManager promotionManager,
                            LogHelper log) {
        this.proxy = proxy;
        this.luckPerms = luckPerms;
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.offlineStore = offlineStore;
        this.configManager = configManager;
        this.promotionManager = promotionManager;
        this.log = log;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 2) {
            source.sendMessage(Component.text("§cUsage: /addpoints <playername> <amount>"));
            return;
        }

        String targetName = args[0];
        log.debug("AddPointsCommand ausgeführt von {} für Spieler '{}' mit Argument '{}'",
                source, targetName, args[1]);

        Optional<UUID> uuidOpt = offlineStore.getUUID(targetName);
        if (uuidOpt.isEmpty()) {
            source.sendMessage(Component.text("§cSpieler '" + targetName + "' ist unbekannt."));
            log.debug("AddPointsCommand abgebrochen: Spieler '{}' ist im OfflineStore unbekannt", targetName);
            return;
        }

        UUID uuid = uuidOpt.get();
        log.debug("AddPointsCommand: UUID für '{}' aufgelöst zu {}", targetName, uuid);

        // Staff-Check
        if (stafflistManager.isStaff(uuid)) {
            boolean staffPointsAllowed = configManager.isStaffPointsAllowed();
            boolean isConsoleLike = !(source instanceof Player);
            boolean hasPerm = source.hasPermission("rankproxyplugin.staffpoints");

            log.debug("Staff-Check für '{}': allowedByConfig={}, consoleLike={}, hasPermission={}",
                    targetName, staffPointsAllowed, isConsoleLike, hasPerm);

            if (!(staffPointsAllowed && (isConsoleLike || hasPerm))) {
                source.sendMessage(Component.text("§cStaff-Mitglieder dürfen keine Punkte erhalten (Config oder Berechtigung fehlt)."));
                log.warn("AddPointsCommand: {} versuchte Punkte an Staff {} zu vergeben (verhindert)", source, targetName);
                return;
            }
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("§cPlease enter a valid number."));
            log.debug("AddPointsCommand: Ungültige Punktzahl '{}' für Spieler '{}'", args[1], targetName);
            return;
        }

        try {
            log.debug("Füge {} Punkte zu '{}' ({}) hinzu", amount, targetName, uuid);

            pointsAPI.addPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);

            log.debug("Neue Punktzahl für '{}' ({}) nach addPoints: {}", targetName, uuid, total);

            // Rang direkt neu prüfen – auch für Offline-Spieler
            promotionManager.handleLogin(uuid, targetName);
            log.debug("Promotion-Prüfung für '{}' ({}) abgeschlossen", targetName, uuid);

            source.sendMessage(Component.text("§aAdded " + amount + " points to §e" + targetName + "§a. Total: §b" + total));
            log.info("AddPointsCommand: {} hat jetzt {} Punkte", targetName, total);
        } catch (Exception e) {
            source.sendMessage(Component.text("§cError while adding points."));
            log.error("AddPointsCommand: Fehler beim Hinzufügen von Punkten für {}: {}", targetName, e.getMessage());
            log.debug("AddPointsCommand Exception für '{}'", targetName, e);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.addpoints");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            return CommandUtils.suggestPlayerNames(proxy, luckPerms, args[0]);
        } else if (args.length == 2) {
            return List.of("1", "5", "10", "100");
        }

        return List.of();
    }
}