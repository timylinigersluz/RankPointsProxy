package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.LogLevel;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /staffremove <playername>
 * Entfernt Spieler (auch offline) aus der Stafflist.
 */
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

        // 1️⃣ UUID aus DB holen
        UUID uuid = stafflistManager.getUUIDByName(targetName);

        if (uuid == null) {
            source.sendMessage(Component.text("§cSpieler '" + targetName + "' wurde nicht in der Stafflist gefunden."));
            log.warn("[StaffRemove] {} nicht in Stafflist gefunden", targetName);
            return;
        }

        // 2️⃣ Entfernen aus der DB
        boolean success = stafflistManager.removeStaffMember(uuid);

        if (success) {
            source.sendMessage(Component.text("§a" + targetName + " wurde aus der Stafflist entfernt."));
            log.info("[StaffRemove] {} ({}) entfernt.", targetName, uuid);
        } else {
            source.sendMessage(Component.text("§cFehler beim Entfernen von " + targetName));
            log.warn("[StaffRemove] Entfernen von {} ({}) fehlgeschlagen.", targetName, uuid);
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
            for (String name : stafflistManager.getAllStaffNames()) {
                if (name.toLowerCase().startsWith(prefix)) {
                    suggestions.add(name);
                }
            }
        }
        return suggestions;
    }
}
