package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SetPointsCommand implements SimpleCommand {

    private final ProxyServer server;
    private final PointsAPI pointsAPI;

    public SetPointsCommand(ProxyServer server, PointsAPI pointsAPI) {
        this.server = server;
        this.pointsAPI = pointsAPI;
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
            pointsAPI.setPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§aPoints for §e" + targetPlayer.getUsername() + " §aset to: §b" + total));
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn internal error occurred while setting points."));
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.setpoints");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player player : server.getAllPlayers()) {
                if (player.getUsername().toLowerCase().startsWith(prefix)) {
                    suggestions.add(player.getUsername());
                }
            }
        } else if (args.length == 2) {
            suggestions.add("0");
            suggestions.add("10");
            suggestions.add("50");
            suggestions.add("100");
        }

        return suggestions;
    }
}
