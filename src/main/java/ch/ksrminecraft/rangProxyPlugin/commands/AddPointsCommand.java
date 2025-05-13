package ch.ksrminecraft.rangProxyPlugin.commands;

import ch.ksrminecraft.RangAPI.RangAPI;
import ch.ksrminecraft.rangProxyPlugin.RangProxyPlugin;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class AddPointsCommand implements SimpleCommand{
    // Zugriff auf Server nötig, um Spieler zu finden
    private final ProxyServer server;
    private final RangAPI rangapi;

    public AddPointsCommand(ProxyServer server, RangAPI rangapi) {
        this.server = server;
        this.rangapi = rangapi;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();


        // Argumentanzahl überprüfen
        if (args.length != 2 ) {
            source.sendMessage(Component.text("Number of arguments not 2! usage: /addpoints <playername> <amount>"));
            return;
        }

        String targetName = args[0];
        String amountStr = args[1];

        // Spieler suchen
        Optional<Player> targetOpt = server.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            source.sendMessage(Component.text("Player " + targetName + " could not be found."));
            return;
        }

        Player targetPlayer = targetOpt.get();

        // Punktzahl auslesen
        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            source.sendMessage(Component.text("Number could not be read. Make sure you enter a number."));
            return;
        }

        // UUID auslesen zur Ausführung der Funktion
        UUID targetPlayerUUID;

        try{
            targetPlayerUUID = targetPlayer.getUniqueId();
        } catch (Exception e) {
            source.sendMessage(Component.text("Internal error: UUID could not be found"));
            return;
        }

        try{
            rangapi.addPoints(targetPlayerUUID, amount);
            source.sendMessage(Component.text(amount + " points were written to player " + targetPlayer + "."));
            return;
        } catch (Exception e) {
            source.sendMessage(Component.text("Internal error: Points could not be added. Error in calling RangAPI"));
            return;
        }


    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rangproxyplugin.addpoints");
    }
}
