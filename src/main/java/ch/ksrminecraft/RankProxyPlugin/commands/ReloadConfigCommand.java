package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import net.kyori.adventure.text.Component;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

public class ReloadConfigCommand implements SimpleCommand {

    private final ConfigManager configManager;

    public ReloadConfigCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void execute(Invocation invocation) {
        configManager.reload();
        invocation.source().sendMessage(Component.text("§aConfiguration successfully reloaded from resources.yaml."));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.reloadconfig");
    }
}