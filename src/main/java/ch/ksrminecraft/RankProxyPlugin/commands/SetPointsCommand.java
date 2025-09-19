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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SetPointsCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final OfflinePlayerStore offlineStore;
    private final ConfigManager configManager;
    private final LogHelper log; // zentraler LogHelper

    public SetPointsCommand(PointsAPI pointsAPI,
                            StafflistManager stafflistManager,
                            OfflinePlayerStore offlineStore,
                            ConfigManager configManager,
                            LogHelper log) {
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.offlineStore = offlineStore;
        this.configManager = configManager;
        this.log = log;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 2) {
            source.sendMessage(Component.text("§cUsage: /setpoints <playername> <amount>"));
            return;
        }

        String targetName = args[0];
        Optional<UUID> uuidOpt = offlineStore.getUUID(targetName);
        if (uuidOpt.isEmpty()) {
            source.sendMessage(Component.text("§cSpieler '" + targetName + "' ist unbekannt."));
            log.warn("SetPointsCommand: Spieler '{}' nicht gefunden", targetName);
            return;
        }

        UUID uuid = uuidOpt.get();

        // Staff-Check: Für Staff müssen ZWEI Bedingungen erfüllt sein:
        // 1) config.staff.give-points == true
        // 2) Sender ist Konsole ODER besitzt Permission rankproxyplugin.staffpoints
        if (stafflistManager.isStaff(uuid)) {
            boolean staffPointsAllowed = configManager.isStaffPointsAllowed();
            boolean isConsoleLike = !(source instanceof Player);
            boolean hasPerm = source.hasPermission("rankproxyplugin.staffpoints");

            if (!(staffPointsAllowed && (isConsoleLike || hasPerm))) {
                source.sendMessage(Component.text("§cDu darfst keine Punkte für Staff-Mitglieder setzen (Config oder Berechtigung fehlt)."));
                log.warn("SetPointsCommand: {} versuchte Punkte für Staff {} zu setzen (verhindert)", source, targetName);
                return;
            }
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("§cPlease enter a valid number."));
            return;
        }

        try {
            pointsAPI.setPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§aPoints for §e" + targetName + " §aset to: §b" + total));
            log.info("SetPointsCommand: {} -> {} Punkte gesetzt ({} total)", targetName, amount, total);
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn error occurred while setting points."));
            log.error("SetPointsCommand: Fehler beim Setzen von Punkten für {}: {}", targetName, e.getMessage());
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.setpoints");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return offlineStore.getAllNamesStartingWith(args[0]);
        } else if (args.length == 2) {
            return List.of("0", "10", "50", "100");
        }
        return List.of();
    }
}
