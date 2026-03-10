package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
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
        CommandSource source = invocation.source();

        try {
            configManager.reload();

            String level = configManager.getLogLevel();
            LogHelper log = configManager.getLogger();

            source.sendMessage(Component.text("§aConfiguration successfully reloaded from resources.yaml."));
            source.sendMessage(Component.text("§eAktuelles Log-Level: " + level));

            log.info("Configuration reloaded via /rankproxyreload. Aktuelles Log-Level: {}", level);
        } catch (Exception e) {
            source.sendMessage(Component.text("§cFehler beim Neuladen der Konfiguration."));
            configManager.getLogger().error("Fehler beim Reload der Konfiguration: {}", e.getMessage());
            configManager.getLogger().debug("ReloadConfigCommand Exception", e);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.reloadconfig");
    }
}