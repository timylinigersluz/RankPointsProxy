package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.LogLevel;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class StafflistRemoveCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;
    private final LogHelper log;

    public StafflistRemoveCommand(ProxyServer server,
                                  StafflistManager stafflistManager,
                                  ConfigManager configManager,
                                  Logger baseLogger) {
        this.server = server;
        this.stafflistManager = stafflistManager;

        // LogHelper aus Config initialisieren
        LogLevel level = LogLevel.fromString(configManager.getLogLevel());
        this.log = new LogHelper(baseLogger, level);
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
            log.warn("StafflistRemoveCommand: Spieler '{}' nicht online gefunden", targetName);
            return;
        }

        Player targetPlayer = targetOpt.get();
        UUID uuid = targetPlayer.getUniqueId();

        boolean success = stafflistManager.removeStaffMember(uuid);

        if (success) {
            source.sendMessage(Component.text("§aPlayer §e" + targetName + " §ahas been removed from the stafflist."));
            log.info("StafflistRemoveCommand: {} ({}) wurde aus der Stafflist entfernt", targetName, uuid);
        } else {
            source.sendMessage(Component.text("§cPlayer §e" + targetName + " §cwas not found in the stafflist or an error occurred."));
            log.warn("StafflistRemoveCommand: Entfernen von {} ({}) fehlgeschlagen (nicht in Liste oder Fehler)", targetName, uuid);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.remove");
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
