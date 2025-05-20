package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class AddPointsCommand implements SimpleCommand {

    private final ProxyServer server;
    private final PointsAPI pointsAPI;

    public AddPointsCommand(ProxyServer server, PointsAPI pointsAPI) {
        this.server = server;
        this.pointsAPI = pointsAPI;
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
        String amountStr = args[1];

        Optional<Player> targetOpt = server.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            source.sendMessage(Component.text("§cPlayer '" + targetName + "' not found."));
            return;
        }

        Player targetPlayer = targetOpt.get();

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("§cPlease enter a valid number."));
            return;
        }

        UUID uuid = targetPlayer.getUniqueId();

        try {
            pointsAPI.addPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§a" + amount + " points added to §e" + targetPlayer.getUsername() + "§a. Total: §b" + total));
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn internal error occurred while adding points."));
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rangproxyplugin.addpoints");
    }
}
