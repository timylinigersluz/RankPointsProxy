package ch.ksrminecraft.rangProxyPlugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;
import ch.ksrminecraft.RangAPI.RangAPI;

@Plugin(id = "rangproxyplugin", name = "RangProxyPlugin", version = "1.0")
public class RangProxyPlugin {

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        RangAPI api = new RangAPI("asf", "sa", "asdf");
    }
}
