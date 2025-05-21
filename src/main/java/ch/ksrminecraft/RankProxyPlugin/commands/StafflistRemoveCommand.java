package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

public class StafflistRemoveCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;

    public StafflistRemoveCommand(ProxyServer server, StafflistManager stafflistManager) {
        this.server = server;
        this.stafflistManager = stafflistManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /staffremove <playername>"));
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

        boolean success = stafflistManager.removeStaffMember(uuid);

        if (success) {
            source.sendMessage(Component.text("§aPlayer §e" + targetName + " §ahas been removed from the stafflist."));
        } else {
            source.sendMessage(Component.text("§cPlayer §e" + targetName + " §cwas not found in the stafflist or an error occurred."));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.remove");
    }

    @Override
    public java.util.List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        java.util.List<String> suggestions = new java.util.ArrayList<>();

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
