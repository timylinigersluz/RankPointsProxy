package ch.ksrminecraft.RankProxyPlugin.commands;

import ch.ksrminecraft.RankProxyPlugin.utils.ConfigManager;
import ch.ksrminecraft.RankProxyPlugin.utils.LogHelper;
import ch.ksrminecraft.RankProxyPlugin.utils.LogLevel;
import ch.ksrminecraft.RankProxyPlugin.utils.PendingStaffEventStore;
import ch.ksrminecraft.RankProxyPlugin.utils.PromotionMessageSender;
import ch.ksrminecraft.RankProxyPlugin.utils.StaffPermissionService;
import ch.ksrminecraft.RankProxyPlugin.utils.StafflistManager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;

import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StafflistRemoveCommand implements SimpleCommand {

    private final ProxyServer server;
    private final StafflistManager stafflistManager;
    private final StaffPermissionService staffPermissionService;
    private final PendingStaffEventStore pendingStaffEventStore;
    private final Scheduler scheduler;
    private final Object pluginInstance;
    private final LogHelper log;

    public StafflistRemoveCommand(ProxyServer server,
                                  StafflistManager stafflistManager,
                                  StaffPermissionService staffPermissionService,
                                  PendingStaffEventStore pendingStaffEventStore,
                                  ConfigManager configManager,
                                  Logger baseLogger,
                                  Scheduler scheduler,
                                  Object pluginInstance) {
        this.server = server;
        this.stafflistManager = stafflistManager;
        this.staffPermissionService = staffPermissionService;
        this.pendingStaffEventStore = pendingStaffEventStore;
        this.scheduler = scheduler;
        this.pluginInstance = pluginInstance;

        LogLevel level = LogLevel.fromString(configManager.getLogLevel());
        this.log = new LogHelper(baseLogger, level);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(Component.text("§cUsage: /staffremove <playername>"));
            return;
        }

        String targetName = args[0];
        UUID uuid = stafflistManager.getUUIDByName(targetName);

        if (uuid == null) {
            source.sendMessage(Component.text("§cSpieler '" + targetName + "' wurde nicht in der Stafflist gefunden."));
            log.warn("[StaffRemove] {} nicht in Stafflist gefunden", targetName);
            return;
        }

        boolean removedFromStafflist = stafflistManager.removeStaffMember(uuid);

        if (!removedFromStafflist) {
            source.sendMessage(Component.text("§cFehler beim Entfernen von " + targetName));
            log.warn("[StaffRemove] Entfernen von {} ({}) aus Stafflist fehlgeschlagen.", targetName, uuid);
            return;
        }

        StaffPermissionService.PermissionSyncResult result =
                staffPermissionService.demoteFromStaff(uuid, targetName);

        if (!result.success()) {
            log.error("[StaffRemove] LuckPerms-Umstellung für {} ({}) fehlgeschlagen. Rolle Stafflist-Eintrag zurück.", targetName, uuid);

            boolean rollbackSuccess = stafflistManager.addStaffMember(uuid, targetName);
            if (!rollbackSuccess) {
                log.error("[StaffRemove] Rollback fehlgeschlagen: {} ({}) bleibt aus der Stafflist entfernt, obwohl LuckPerms nicht korrekt umgestellt werden konnte.", targetName, uuid);
            }

            source.sendMessage(Component.text("§c" + targetName + " konnte nicht korrekt aus dem Staff entfernt werden. Änderungen wurden zurückgerollt."));
            return;
        }

        source.sendMessage(Component.text("§a" + targetName + " wurde aus der Stafflist entfernt."));

        server.getPlayer(uuid).ifPresentOrElse(player -> {
            PromotionMessageSender.sendStaffRemoval(player, scheduler, pluginInstance);
            log.info("[StaffRemove] Staff-Removal-Event direkt an online Spieler {} gesendet.", targetName);
        }, () -> {
            pendingStaffEventStore.setPending(uuid, PendingStaffEventStore.PendingStaffEventType.REMOVAL);
            log.info("[StaffRemove] {} ({}) ist offline – Staff-Removal-Event wird beim nächsten Login nachgeholt.", targetName, uuid);
        });

        if (result.changed()) {
            log.info("[StaffRemove] {} ({}) aus Stafflist entfernt und via LuckPerms aus Staff-Laufbahn genommen.", targetName, uuid);
        } else {
            log.info("[StaffRemove] {} ({}) wurde aus der Stafflist entfernt, war in LuckPerms aber bereits korrekt nicht mehr Staff.", targetName, uuid);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("rankproxyplugin.staff.remove");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String name : stafflistManager.getAllStaffNames()) {
                if (name.toLowerCase().startsWith(prefix)) {
                    suggestions.add(name);
                }
            }
        }

        return suggestions;
    }
}