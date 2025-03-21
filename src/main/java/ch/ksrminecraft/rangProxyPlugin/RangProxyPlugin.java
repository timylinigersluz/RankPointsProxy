package ch.ksrminecraft.rangProxyPlugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.slf4j.Logger;
import ch.ksrminecraft.RangAPI.RangAPI;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Plugin(id = "rangproxyplugin", name = "RangProxyPlugin", version = "1.0")
public class RangProxyPlugin {

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent eventD, @DataDirectory Path dataDirectory) {
        // https://github.com/SpongePowered/Configurate/wiki/Getting-Started
        //TODO make dir if not there
        //TODO make conf if not there
        Path configpath = Paths.get("resources");
        final YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(dataDirectory.resolve(dataDirectory.resolve(configpath))).build();
        CommentedConfigurationNode root;
        try {
        root = loader.load();
        } catch (IOException e) {
            System.err.println("An error occurred while loading this configuration: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.exit(1);
        }


        RangAPI api = new RangAPI("asf", "sa", "asdf");
    }
}
