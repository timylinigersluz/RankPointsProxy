package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.Map;

public class StafflistListCommand implements SimpleCommand {

    private final StafflistManager manager;
    private final LogHelper log;

    public StafflistListCommand(StafflistManager manager, LogHelper log) {
        this.manager = manager;
        this.log = log;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        Map<String, String> all = manager.getAllStaff();
        log.debug("StafflistListCommand: {} Staff-Einträge geladen", all.size());

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