package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AddPointsCommand implements SimpleCommand {

    private final ProxyServer server;
    private final PointsAPI pointsAPI;
    private final Logger logger;
    private final boolean isDebug;

    public AddPointsCommand(ProxyServer server, PointsAPI pointsAPI, Logger logger, boolean isDebug) {
        this.server = server;
        this.pointsAPI = pointsAPI;
        this.logger = logger;
        this.isDebug = isDebug;
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
        UUID uuid = targetPlayer.getUniqueId();

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("§cPlease enter a valid number."));
            return;
        }

        try {
            if (isDebug) {
                logger.info("[AddPointsCommand] Adding {} points to UUID: {}", amount, uuid);
            }

            pointsAPI.addPoints(uuid, amount);
            int total = pointsAPI.getPoints(uuid);

            source.sendMessage(Component.text("§a" + amount + " points added to §e" + targetPlayer.getUsername() + "§a. Total: §b" + total));

            if (isDebug) {
                logger.info("[AddPointsCommand] Added {} points to {} (UUID: {}). New total: {}", amount, targetName, uuid, total);
            }
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn internal error occurred while adding points."));
            logger.error("[AddPointsCommand] Error while adding points", e);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.addpoints");
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
            suggestions.add("1");
            suggestions.add("5");
            suggestions.add("10");
            suggestions.add("100");
        }

        return suggestions;
    }
}
