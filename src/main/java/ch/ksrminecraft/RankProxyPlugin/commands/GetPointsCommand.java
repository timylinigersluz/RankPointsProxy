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

public class GetPointsCommand implements SimpleCommand {

    private final ProxyServer server;
    private final PointsAPI pointsAPI;

    public GetPointsCommand(ProxyServer server, PointsAPI pointsAPI) {
        this.server = server;
        this.pointsAPI = pointsAPI;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /getpoints <playername>"));
            return;
        }

        String targetName = args[0];

        Optional<Player> targetOpt = server.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            source.sendMessage(Component.text("§cPlayer '" + targetName + "' not found."));
            return;
        }

        Player targetPlayer = targetOpt.get();
        UUID uuid = targetPlayer.getUniqueId();

        try {
            int points = pointsAPI.getPoints(uuid);
            source.sendMessage(Component.text("§e" + targetPlayer.getUsername() + " §ahas §b" + points + "§a points."));
        } catch (Exception e) {
            source.sendMessage(Component.text("§cAn internal error occurred while retrieving points."));
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rangproxyplugin.getpoints");
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
        }

        return suggestions;
    }
}
