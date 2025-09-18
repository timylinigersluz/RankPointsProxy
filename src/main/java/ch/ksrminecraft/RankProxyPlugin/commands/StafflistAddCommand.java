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
import net.luckperms.api.LuckPerms;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class StafflistAddCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;
    private final ConfigManager configManager;
    private final LuckPerms luckPerms;
    private final LogHelper log;

    public StafflistAddCommand(ProxyServer server,
                               StafflistManager stafflistManager,
                               ConfigManager configManager,
                               Logger baseLogger,
                               LuckPerms luckPerms) {
        this.server = server;
        this.stafflistManager = stafflistManager;
        this.configManager = configManager;
        this.luckPerms = luckPerms;

        // LogHelper aus Config initialisieren
        LogLevel level = LogLevel.fromString(configManager.getLogLevel());
        this.log = new LogHelper(baseLogger, level);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /staffadd <playername>"));
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = server.getPlayer(targetName);

        if (targetOpt.isEmpty()) {
            source.sendMessage(Component.text("§cPlayer '" + targetName + "' not found."));
            log.warn("StafflistAddCommand: Spieler '{}' nicht online gefunden", targetName);
            return;
        }

        Player targetPlayer = targetOpt.get();
        UUID uuid = targetPlayer.getUniqueId();

        boolean success = stafflistManager.addStaffAndAssignGroup(
                uuid,
                targetPlayer.getUsername(),
                luckPerms,
                configManager.getStaffGroupName()
        );

        if (success) {
            source.sendMessage(Component.text("§aPlayer §e" + targetName + " §ahas been added to the stafflist and assigned to group '"
                    + configManager.getStaffGroupName() + "'."));
            log.info("StafflistAddCommand: {} ({}) wurde zur Stafflist hinzugefügt und Gruppe '{}' zugewiesen",
                    targetName, uuid, configManager.getStaffGroupName());
        } else {
            source.sendMessage(Component.text("§cPlayer §e" + targetName + " §cis already in the stafflist or an error occurred."));
            log.warn("StafflistAddCommand: Hinzufügen von {} ({}) fehlgeschlagen (bereits vorhanden oder Fehler)", targetName, uuid);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.add");
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
