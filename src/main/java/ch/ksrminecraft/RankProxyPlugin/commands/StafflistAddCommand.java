package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.PendingStaffEventStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionMessageSender;
import ch.ksrminecraft.RankProxyPlugin.utils.StaffPermissionService;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import net.kyori.adventure.text.Component;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public class StafflistAddCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;
    private final StaffPermissionService staffPermissionService;
    private final PendingStaffEventStore pendingStaffEventStore;
    private final Scheduler scheduler;
    private final Object pluginInstance;
    private final LogHelper log;

    public StafflistAddCommand(ProxyServer server,
                               StafflistManager stafflistManager,
                               StaffPermissionService staffPermissionService,
                               PendingStaffEventStore pendingStaffEventStore,
                               Scheduler scheduler,
                               Object pluginInstance,
                               LogHelper log) {
        this.server = server;
        this.stafflistManager = stafflistManager;
        this.staffPermissionService = staffPermissionService;
        this.pendingStaffEventStore = pendingStaffEventStore;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;
        this.log = log;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /staffadd <playername>"));
            log.debug("StafflistAddCommand mit ungültiger Argumentanzahl ausgeführt: {}", args.length);
            return;
        }

        String targetName = args[0];
        log.debug("StafflistAddCommand ausgeführt für '{}'", targetName);

        UUID uuid = server.getPlayer(targetName)
                .map(p -> p.getUniqueId())
                .orElseGet(() -> fetchUUIDFromMojang(targetName));

        if (uuid == null) {
            source.sendMessage(Component.text("§cFehler: '" + targetName + "' ist kein gültiger Minecraft-Name."));
            log.debug("StafflistAddCommand: '{}' konnte nicht in UUID aufgelöst werden", targetName);
            return;
        }

        log.debug("StafflistAddCommand: UUID für '{}' = {}", targetName, uuid);

        boolean addedToStafflist = stafflistManager.addStaffMember(uuid, targetName);

        if (!addedToStafflist) {
            source.sendMessage(Component.text("§c" + targetName + " konnte nicht hinzugefügt werden (bereits vorhanden?)."));
            log.warn("StafflistAddCommand: Hinzufügen von {} ({}) zur Stafflist fehlgeschlagen", targetName, uuid);
            return;
        }

        StaffPermissionService.PermissionSyncResult result =
                staffPermissionService.promoteToStaff(uuid, targetName);

        if (!result.success()) {
            log.error("StafflistAddCommand: LuckPerms-Umstellung für {} ({}) fehlgeschlagen. Rolle Stafflist-Eintrag zurück.",
                    targetName, uuid);

            boolean rollbackSuccess = stafflistManager.removeStaffMember(uuid);
            if (!rollbackSuccess) {
                log.error("StafflistAddCommand: Rollback fehlgeschlagen: {} ({}) bleibt in Stafflist, obwohl LuckPerms nicht umgestellt werden konnte.",
                        targetName, uuid);
            }

            source.sendMessage(Component.text("§c" + targetName + " konnte nicht korrekt zum Staff hinzugefügt werden. Änderungen wurden zurückgerollt."));
            return;
        }

        source.sendMessage(Component.text("§a" + targetName + " wurde zur Stafflist hinzugefügt."));

        server.getPlayer(uuid).ifPresentOrElse(player -> {
            PromotionMessageSender.sendStaffAppointment(player, scheduler, pluginInstance);
            log.info("StafflistAddCommand: Staff-Event direkt an online Spieler {} gesendet", targetName);
        }, () -> {
            pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.APPOINTMENT);
            log.info("StafflistAddCommand: {} ({}) ist offline – Staff-Event wird beim nächsten Login nachgeholt", targetName, uuid);
        });

        if (result.changed()) {
            log.info("StafflistAddCommand: {} ({}) hinzugefügt und via LuckPerms in Staff-Laufbahn verschoben", targetName, uuid);
        } else {
            log.info("StafflistAddCommand: {} ({}) wurde zur Stafflist hinzugefügt, war in LuckPerms aber bereits korrekt Staff", targetName, uuid);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.add");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length > 1) {
            return List.of();
        }

        String prefix = args.length == 0 ? "" : args[0].toLowerCase();

        return server.getAllPlayers().stream()
                .filter(player -> !stafflistManager.isStaff(player.getUniqueId()))
                .map(player -> player.getUsername())
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private UUID fetchUUIDFromMojang(String name) {
        try {
            log.debug("StafflistAddCommand: Mojang-Abfrage für '{}'", name);

            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int responseCode = conn.getResponseCode();
            log.debug("StafflistAddCommand: Mojang-Response für '{}' = {}", name, responseCode);

            if (responseCode != 200) {
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String jsonText = reader.lines().reduce("", (a, b) -> a + b);
                JSONObject json = (JSONObject) new JSONParser().parse(jsonText);
                String id = (String) json.get("id");

                if (id == null) {
                    return null;
                }

                UUID uuid = UUID.fromString(id.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                        "$1-$2-$3-$4-$5"
                ));

                log.debug("StafflistAddCommand: Mojang lieferte UUID {} für '{}'", uuid, name);
                return uuid;
            }
        } catch (Exception e) {
            log.warn("StafflistAddCommand: Mojang-Abfrage für {} fehlgeschlagen: {}", name, e.getMessage());
            log.debug("StafflistAddCommand Exception bei Mojang-Abfrage für '{}'", name, e);
            return null;
        }
    }
}