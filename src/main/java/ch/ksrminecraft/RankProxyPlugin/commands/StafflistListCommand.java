package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.LogLevel;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.Map;

public class StafflistListCommand implements SimpleCommand {

    private final StafflistManager manager;
    private final LogHelper log;

    public StafflistListCommand(StafflistManager manager,
                                ConfigManager configManager,
                                Logger baseLogger) {
        this.manager = manager;

        // LogHelper aus Config initialisieren
        LogLevel level = LogLevel.fromString(configManager.getLogLevel());
        this.log = new LogHelper(baseLogger, level);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        Map<String, String> all = manager.getAllStaff();

        if (all.isEmpty()) {
            source.sendMessage(Component.text("§7No staff members found."));
            log.info("StafflistListCommand: Staffliste abgefragt – leer");
            return;
        }

        source.sendMessage(Component.text("§eStafflist:"));
        for (Map.Entry<String, String> entry : all.entrySet()) {
            source.sendMessage(Component.text(" §8- §b" + entry.getValue() + " §7(" + entry.getKey() + ")"));
        }

        log.info("StafflistListCommand: {} Staff-Mitglieder aufgelistet", all.size());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.list");
    }
}
