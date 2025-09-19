package ch.ksrminecraft.RankProxyPlugin.utils;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Hilfsmethoden für Command-Autocompletion.
 */
public class CommandUtils {

    public static List<String> suggestPlayerNames(ProxyServer proxy, LuckPerms luckPerms, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        // Online-Spieler
        List<String> online = proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());

        // Offline-Spieler aus LuckPerms (geladene User)
        List<String> offline = luckPerms.getUserManager().getLoadedUsers().stream()
                .map(User::getUsername)
                .filter(name -> name != null && name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                .collect(Collectors.toList());

        // Mergen ohne Duplikate
        offline.stream()
                .filter(name -> !online.contains(name))
                .forEach(online::add);

        return online;
    }
}
