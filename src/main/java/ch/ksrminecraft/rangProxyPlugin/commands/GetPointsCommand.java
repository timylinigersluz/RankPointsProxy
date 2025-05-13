package ch.ksrminecraft.rangProxyPlugin.commands;

import ch.ksrminecraft.RangAPI.RangAPI;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class GetPointsCommand implements SimpleCommand {

    private final ProxyServer server;
    private final RangAPI rangapi;

    public GetPointsCommand(ProxyServer server) {
        this.server = server;
        this.rangapi = rangapi;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("Number of arguments not 1. Please check your input."));
            return;
        }

        String targetName = args[0];

        Optional<Player> targetOpt = server.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            source.sendMessage(Component.text("Target could not be found."));
            return;
        }

        Player targetPlayer = targetOpt.get();

        UUID targetPlayerUUID;

        try{
            targetPlayerUUID = targetPlayer.getUniqueId();
        } catch (Exception e) {
            source.sendMessage(Component.text("Internal error: UUID could not be retrieved."));
            return;
        }

        int amount = rangapi.getPoints(targetPlayerUUID);

        source.sendMessage(Component.text("Player " + targetPlayer.getUsername() + " has " + amount + " points."));
        return;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rangproxyplugin.getpoints");
    }
}
