package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.Map;

public class StafflistListCommand implements SimpleCommand {

    private final StafflistManager manager;

    public StafflistListCommand(StafflistManager manager) {
        this.manager = manager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        Map<String, String> all = manager.getAllStaff();

        if (all.isEmpty()) {
            source.sendMessage(Component.text("§7No staff members found."));
            return;
        }

        source.sendMessage(Component.text("§eStafflist:"));
        for (Map.Entry<String, String> entry : all.entrySet()) {
            source.sendMessage(Component.text(" §8- §b" + entry.getValue() + " §7(" + entry.getKey() + ")"));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.list");
    }
}
