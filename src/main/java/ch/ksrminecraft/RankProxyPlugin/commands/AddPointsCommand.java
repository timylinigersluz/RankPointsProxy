package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.*;

public class AddPointsCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final OfflinePlayerStore offlineStore;
    private final ConfigManager configManager;
    private final LogHelper log; // über Main übergeben

    public AddPointsCommand(PointsAPI pointsAPI,
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
            source.sendMessage(Component.text("§cUsage: /addpoints <playername> <amount>"));
            return;
        }

        String targetName = args[0];
        Optional<UUID> uuidOpt = offlineStore.getUUID(targetName);
        if (uuidOpt.isEmpty()) {
            source.sendMessage(Component.text("§cSpieler '" + targetName + "' ist unbekannt."));
            return;
        }

        UUID uuid = uuidOpt.get();

        // Staff-Check
        if (stafflistManager.isStaff(uuid)
                && !configManager.isStaffPointsAllowed()
                && !source.hasPermission("rankproxyplugin.staffpoints")) {
            source.sendMessage(Component.text("§cStaff members cannot receive points (disabled in config)."));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("§cPlease enter a valid number."));
            return;
        }

        try {
            pointsAPI.addPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§aAdded " + amount + " points to §e" + targetName + "§a. Total: §b" + total));
            log.info("[AddPointsCommand] {} now has {} points", targetName, total);
        } catch (Exception e) {
            source.sendMessage(Component.text("§cError while adding points."));
            log.error("[AddPointsCommand] Failed to add points for {}: {}", targetName, e.getMessage());
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
            return offlineStore.getAllNamesStartingWith(args[0]);
        } else if (args.length == 2) {
            return List.of("1", "5", "10", "100");
        }
        return List.of();
    }
}
