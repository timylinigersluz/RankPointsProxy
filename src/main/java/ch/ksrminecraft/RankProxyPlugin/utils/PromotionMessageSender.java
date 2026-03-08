package ch.ksrminecraft.RankProxyPlugin.utils;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.sound.Sound;

import java.time.Duration;

/**
 * PromotionMessageSender
 *
 * Sendet Promotion-/Demotion-Nachrichten an Spieler:
 * - Title: Rang-Icon mit Custom Font
 * - Subtitle: frei definierter Text
 * - Chat-Nachricht mit zusätzlicher Zeile
 */
public final class PromotionMessageSender {

    private static final Key RANK_FONT = Key.key("ksr", "ranks");

    private PromotionMessageSender() {
    }

    public static void sendPromotion(Player player, String rankName) {
        var data = RankDisplayHelper.get(rankName);

        Title title = Title.title(
                Component.text(data.glyph()).font(RANK_FONT),
                Component.text("Bravo, du hast nun den Rang " + data.displayName() + ".", data.color()),
                Title.Times.times(
                        Duration.ofMillis(700),
                        Duration.ofSeconds(8),
                        Duration.ofMillis(1200)
                )
        );

        player.showTitle(title);

        player.playSound(
                Sound.sound(
                        Key.key("ui.toast.challenge_complete"),
                        Sound.Source.MASTER,
                        1.0f,
                        1.0f
                )
        );

        player.sendMessage(
                Component.text("Glückwunsch! Du wurdest zu ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" befördert.\nMach weiter so!"))
        );
    }

    public static void sendDemotion(Player player, String rankName) {
        var data = RankDisplayHelper.get(rankName);

        Title title = Title.title(
                Component.text(data.glyph()).font(RANK_FONT),
                Component.text("Dein Rang wurde auf " + data.displayName() + " angepasst.", data.color()),
                Title.Times.times(
                        Duration.ofMillis(700),
                        Duration.ofSeconds(8),
                        Duration.ofMillis(1200)
                )
        );

        player.showTitle(title);

        player.playSound(
                Sound.sound(
                        Key.key("block.note_block.bass"),
                        Sound.Source.MASTER,
                        1.0f,
                        0.8f
                )
        );

        player.sendMessage(
                Component.text("Dein Rang wurde auf ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" angepasst."))
        );
    }
}