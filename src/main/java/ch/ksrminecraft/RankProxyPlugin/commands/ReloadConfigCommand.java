package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

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
            source.sendMessage(Component.text("§6Hinweis: Änderungen an Tracks, Default-Gruppen, Staff-Rängen oder Intervallen"));
            source.sendMessage(Component.text("§6werden erst nach einem Plugin-/Proxy-Neustart vollständig wirksam."));

            log.info("Configuration reloaded via /rankproxyreload. Aktuelles Log-Level: {}", level);
            log.warn("Hinweis nach /rankproxyreload: Änderungen an Tracks, Default-Gruppen, Staff-Rängen oder Intervallen erfordern aktuell einen Neustart.");

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