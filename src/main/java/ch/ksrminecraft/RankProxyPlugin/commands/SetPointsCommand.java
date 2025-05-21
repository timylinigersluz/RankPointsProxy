package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import ch.ksrminecraft.RankProxyPlugin.utils.OfflinePlayerStore;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.*;

public class SetPointsCommand implements SimpleCommand {

    private final PointsAPI pointsAPI;
    private final StafflistManager stafflistManager;
    private final OfflinePlayerStore offlineStore;

    public SetPointsCommand(PointsAPI pointsAPI, StafflistManager stafflistManager, OfflinePlayerStore offlineStore) {
        this.pointsAPI = pointsAPI;
        this.stafflistManager = stafflistManager;
        this.offlineStore = offlineStore;
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
            return;
        }

        UUID uuid = uuidOpt.get();
        if (stafflistManager.isStaff(uuid)) {
            source.sendMessage(Component.text("§cStaff members cannot receive points."));
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
            pointsAPI.setPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§aPoints for §e" + targetName + " §aset to: §b" + total));
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn error occurred while setting points."));
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
