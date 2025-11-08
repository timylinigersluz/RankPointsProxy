package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;
import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.LogLevel;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * /staffadd <playername>
 * Fügt Spieler (auch offline) der Stafflist hinzu.
 */
public class StafflistAddCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;
    private final ConfigManager configManager;
    private final LuckPerms luckPerms;
    private final LogHelper log;

    public StafflistAddCommand(ProxyServer server,
                               StafflistManager stafflistManager,
                               ConfigManager configManager,
                               Logger baseLogger,
                               LuckPerms luckPerms) {
        this.server = server;
        this.stafflistManager = stafflistManager;
        this.configManager = configManager;
        this.luckPerms = luckPerms;

        // LogLevel aus String wandeln:
        LogLevel level = LogLevel.fromString(configManager.getLogLevel());
        this.log = new LogHelper(baseLogger, level);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /staffadd <playername>"));
            return;
        }

        String targetName = args[0];

        // 1️⃣ UUID bestimmen (online oder über Mojang-API)
        UUID uuid = server.getPlayer(targetName)
                .map(p -> p.getUniqueId())
                .orElseGet(() -> fetchUUIDFromMojang(targetName));

        if (uuid == null) {
            source.sendMessage(Component.text("§cFehler: '" + targetName + "' ist kein gültiger Minecraft-Name."));
            log.warn("[StaffAdd] Ungültiger Name '{}' (nicht bei Mojang gefunden)", targetName);
            return;
        }

        // 2️⃣ Staff hinzufügen + Gruppe zuweisen
        boolean success = stafflistManager.addStaffAndAssignGroup(
                uuid,
                targetName,
                luckPerms,
                configManager.getStaffGroupName()
        );

        if (success) {
            source.sendMessage(Component.text("§a" + targetName + " wurde zur Stafflist hinzugefügt."));
            log.info("[StaffAdd] {} ({}) hinzugefügt zur Gruppe '{}'.", targetName, uuid, configManager.getStaffGroupName());
        } else {
            source.sendMessage(Component.text("§c" + targetName + " konnte nicht hinzugefügt werden (bereits vorhanden?)."));
            log.warn("[StaffAdd] Hinzufügen von {} ({}) fehlgeschlagen.", targetName, uuid);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.add");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        // Keine speziellen Vorschläge nötig
        return List.of();
    }

    /**
     * Holt UUID über Mojang API für offline Spieler.
     */
    private UUID fetchUUIDFromMojang(String name) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() != 200) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String jsonText = reader.lines().reduce("", (a, b) -> a + b);
                JSONObject json = (JSONObject) new JSONParser().parse(jsonText);
                String id = (String) json.get("id");
                if (id == null) return null;
                return UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                        "$1-$2-$3-$4-$5"));
            }
        } catch (Exception e) {
            log.warn("[StaffAdd] Mojang-Abfrage für {} fehlgeschlagen: {}", name, e.getMessage());
            return null;
        }
    }
}
