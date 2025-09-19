package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import net.kyori.adventure.text.Component;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

public class ReloadConfigCommand implements SimpleCommand {

    private final ConfigManager configManager;
    private final LogHelper log;

    public ReloadConfigCommand(ConfigManager configManager, LogHelper log) {
        this.configManager = configManager;
        this.log = log;
    }

    @Override
    public void execute(Invocation invocation) {
        configManager.reload();
        String level = configManager.getLogLevel();
        invocation.source().sendMessage(Component.text("§aConfiguration successfully reloaded from resources.yaml."));
        invocation.source().sendMessage(Component.text("§eAktuelles Log-Level: " + level));
        log.info("Configuration reloaded via /rankproxyreload. Aktuelles Log-Level: {}", level);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.reloadconfig");
    }
}
