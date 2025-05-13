package ch.ksrminecraft.rangProxyPlugin;

import ch.ksrminecraft.rangProxyPlugin.commands.AddPointsCommand;
import ch.ksrminecraft.rangProxyPlugin.commands.GetPointsCommand;
import ch.ksrminecraft.rangProxyPlugin.commands.SetPointsCommand;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.slf4j.Logger;
import ch.ksrminecraft.RangAPI.RangAPI;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


@Plugin(id = "rangproxyplugin", name = "RangProxyPlugin", version = "1.0")
public class RangProxyPlugin {
    private final Path dataDirectory = null;

    private final ProxyServer server;
    private RangAPI rangapi = new RangAPI("", "", "");

    @Inject
    private Logger logger;

    @Inject
    public RangProxyPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {

        this.server = server;

        // https://github.com/SpongePowered/Configurate/wiki/Getting-Started
        //TODO make dir if not there
        //TODO make conf if not there
        Path configpath = Paths.get("resources.yaml");
        final YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(dataDirectory.resolve(configpath)).build();
        CommentedConfigurationNode root = null;
        try {
            root = loader.load();
        } catch (IOException e) {
            System.err.println("An error occurred while loading this configuration: " + e.getMessage());
            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            System.exit(1);
        }
        try {
            root.node("test").set(10);
        } catch (SerializationException e) {
            System.out.println("An error occurred while setting up the configuration: " + e.getMessage());
        }
        // And save the node back to the file
        try {
            loader.save(root);
        } catch (final ConfigurateException e) {
            System.err.println("Unable to save your messages configuration! Sorry! " + e.getMessage());
            System.exit(1);
        }
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent e) {
        server.getCommandManager().register("addpoints", new AddPointsCommand(server, rangapi));
        server.getCommandManager().register("setpoints", new SetPointsCommand(server, rangapi));
        server.getCommandManager().register("getpoints", new GetPointsCommand(server, rangapi));
    }
}