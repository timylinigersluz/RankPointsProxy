package ch.ksrminecraft.rangProxyPlugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;
import ch.ksrminecraft.RangAPI.RangAPI;

import java.util.UUID;

@Plugin(id = "rangproxyplugin", name = "RangProxyPlugin", version = "1.0")
public class RangProxyPlugin {

    private LuckPerms luckPerms;

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        RangAPI api = new RangAPI("asf", "sa", "asdf");

        try {
            this.luckPerms = LuckPermsProvider.get();
            logger.info("Successfully loaded LuckPerms.");
        } catch (IllegalStateException e) {
            logger.error("LuckPerms not available");
            }
        }
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public boolean hasPermission(UUID playerUUID, String permission) {
        User user = luckPerms.getUserManager().getUser(playerUUID);
        return user != null && user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }
}
