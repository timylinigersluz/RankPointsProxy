package ch.ksrminecraft.RankProxyPlugin.utils;

import ch.ksrminecraft.RankPointsAPI.PointsAPI;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PromotionManager {

    private final PointsAPI pointsAPI;
    private final RankManager rankManager;
    private final LuckPerms luckPerms;
    private final Logger logger;
    private final ProxyServer proxy;

    private final PendingPromotionStore promotionStore = new PendingPromotionStore();

    public PromotionManager(PointsAPI pointsAPI, RankManager rankManager, LuckPerms luckPerms, Logger logger, ProxyServer proxy) {
        this.pointsAPI = pointsAPI;
        this.rankManager = rankManager;
        this.luckPerms = luckPerms;
        this.logger = logger;
        this.proxy = proxy;
    }

    public void checkAndPromote(UUID uuid, String username) {
        int points = pointsAPI.getPoints(uuid);
        logger.debug("[Debug] checkAndPromote() gestartet für {} (UUID: {}, Punkte: {})", username, uuid, points);

        Optional<RankManager.Rank> currentOpt = getCurrentRank(uuid);
        Optional<RankManager.Rank> nextOpt = rankManager.getRankForPoints(points);

        if (nextOpt.isEmpty()) {
            logger.debug("→ Kein Zielrang für {} gefunden bei {} Punkten", username, points);
            return;
        }

        String targetRank = nextOpt.get().name;
        User user = luckPerms.getUserManager().loadUser(uuid).join();
        String currentGroup = user.getPrimaryGroup();

        if (currentGroup.equalsIgnoreCase(targetRank)) {
            logger.debug("→ {} hat bereits den richtigen Rang: {}", username, targetRank);
            return;
        }

        List<RankManager.Rank> ranks = rankManager.getRankList();
        int currentIndex = getIndex(ranks, currentGroup);
        int targetIndex = getIndex(ranks, targetRank);

        if (targetIndex != currentIndex) {
            boolean isPromotion = targetIndex > currentIndex;

            logger.info("→ {} erkannt für {}: {} → {}", (isPromotion ? "Promotion" : "Demotion"), username, currentGroup, targetRank);

            user.data().clear(node -> node instanceof InheritanceNode);
            user.data().add(InheritanceNode.builder(targetRank).build());
            user.setPrimaryGroup(targetRank);
            luckPerms.getUserManager().saveUser(user);

            Component message = isPromotion
                    ? buildPromotionMessageComponent(targetRank, points)
                    : buildDemotionMessageComponent(targetRank);

            logger.debug("→ Generierte Titel-Nachricht für {}: {}", uuid, message);

            boolean shown = sendTitleIfOnline(uuid, message, isPromotion);
            if (!shown) {
                logger.debug("→ Anzeige fehlgeschlagen – speichere Titel für später");
                promotionStore.setPendingMessage(uuid, message,
                        isPromotion ? PendingPromotionStore.PromotionType.PROMOTION : PendingPromotionStore.PromotionType.DEMOTION);
            } else {
                logger.debug("→ Titel direkt erfolgreich angezeigt");
            }
        } else {
            logger.debug("→ Keine Rangänderung nötig für {} ({} == {})", username, currentIndex, targetIndex);
        }
    }

    private boolean sendTitleIfOnline(UUID uuid, Component message, boolean isPromotion) {
        Optional<Player> playerOpt = proxy.getPlayer(uuid);
        logger.debug("→ Suche Spieler mit UUID {} → gefunden: {}", uuid, playerOpt.isPresent());
        return playerOpt.map(player -> tryShowTitle(player, message, isPromotion)).orElse(false);
    }

    private boolean tryShowTitle(Player player, Component subtitleText, boolean isPromotion) {
        logger.info("→ tryShowTitle() für {} gestartet", player.getUsername());

        Component titleText = Component.text("Rangänderung!").color(NamedTextColor.GREEN);
        Title.Times times = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(6), Duration.ofSeconds(1));
        Title title = Title.title(titleText, subtitleText, times);

        try {
            logger.info("→ Zeige Titel an Spieler {}", player.getUsername());
            player.showTitle(title);
        } catch (Exception e) {
            logger.warn("→ Fehler beim Anzeigen des Titels an {}: {}", player.getUsername(), e.getMessage());
        }

        return true;
    }

    public void handleLogin(Player player) {
        logger.info("→ handleLogin() aufgerufen für {}", player.getUsername());
        UUID uuid = player.getUniqueId();

        if (!promotionStore.hasPendingMessage(uuid)) {
            logger.info("→ Kein gespeicherter Titel für {}", player.getUsername());
            return;
        }

        Component subtitleText = promotionStore.getPendingMessage(uuid);
        PendingPromotionStore.PromotionType type = promotionStore.getPromotionType(uuid);

        if (subtitleText == null) {
            logger.warn("→ PendingMessage ist null für {}", player.getUsername());
            return;
        }

        logger.info("→ Sende gespeicherten Titel an {} nach Login ({}).", player.getUsername(), type);

        Component titleText = Component.text("Rangänderung!").color(NamedTextColor.GREEN);
        Title.Times times = Title.Times.times(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(2));
        player.showTitle(Title.title(titleText, subtitleText, times));

        promotionStore.clearPendingMessage(uuid);
    }

    private Component buildPromotionMessageComponent(String rank, int points) {
        Optional<RankManager.Rank> nextNext = getNextRank(rankManager.getRankList(), rank);
        int pointsToNext = nextNext.map(r -> r.points - points).orElse(0);

        return Component.text()
                .append(Component.text("Neuer Rang: ").color(NamedTextColor.GOLD))
                .append(Component.text(rank).color(NamedTextColor.YELLOW))
                .append(Component.text(" • ").color(NamedTextColor.GRAY))
                .append(Component.text("Noch ").color(NamedTextColor.BLUE))
                .append(Component.text(pointsToNext + " ").color(NamedTextColor.AQUA))
                .append(Component.text("Punkte bis zum nächsten Rang").color(NamedTextColor.BLUE))
                .build();
    }

    private Component buildDemotionMessageComponent(String rank) {
        return Component.text()
                .append(Component.text("Rang geändert: ").color(NamedTextColor.GOLD))
                .append(Component.text(rank).color(NamedTextColor.YELLOW))
                .append(Component.text(" • ").color(NamedTextColor.GRAY))
                .append(Component.text("Punktestand wurde vom Team angepasst").color(NamedTextColor.BLUE))
                .append(Component.text(" – bleib dran!").color(NamedTextColor.BLUE))
                .build();
    }

    private Optional<RankManager.Rank> getCurrentRank(UUID uuid) {
        String group = luckPerms.getUserManager().loadUser(uuid).join().getPrimaryGroup();
        return rankManager.getRankList().stream()
                .filter(r -> r.name.equalsIgnoreCase(group))
                .findFirst();
    }

    private Optional<RankManager.Rank> getNextRank(List<RankManager.Rank> list, String currentRankName) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).name.equalsIgnoreCase(currentRankName)) {
                return Optional.of(list.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private int getIndex(List<RankManager.Rank> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
