package ch.ksrminecraft.RankProxyPlugin.utils;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * PromotionMessageSender
 *
 * Zuständig für die visuelle und akustische Rückmeldung bei Promotionen und Demotionen.
 *
 * Enthält:
 * - Title mit Rangsymbol
 * - Subtitle mit Rangtext
 * - Chat-Nachricht
 * - Sound-Effekte
 * - kurze ActionBar-Animation
 *
 * Wichtig:
 * Das Rangsymbol wird bewusst nur im Title verwendet, nicht in der ActionBar.
 * Grund: Die ActionBar rendert Custom-Fonts nicht immer zuverlässig und zeigte deshalb Vierecke an.
 */
public final class PromotionMessageSender {

    /**
     * Custom Font aus dem Resource Pack für die Rangsymbole.
     * Muss zu assets/ksr/font/ranks.json passen.
     */
    private static final Key RANK_FONT = Key.key("ksr", "ranks");

    private PromotionMessageSender() {
        // Utility-Klasse: keine Instanz nötig
    }

    /**
     * Sendet eine Promotion-Nachricht an einen Spieler.
     *
     * Ablauf:
     * 1. Title mit Rangsymbol
     * 2. kurze ActionBar-Animation
     * 3. kombinierte Sounds leicht verzögert
     * 4. Chat-Nachricht
     */
    public static void sendPromotion(Player player, String rankName, Scheduler scheduler, Object pluginInstance) {
        var data = RankDisplayHelper.get(rankName);

        // Title mit grossem Rangsymbol und erklärendem Subtitle
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

        // Erste ActionBar direkt anzeigen
        // Bewusst OHNE Glyph/Icon, damit keine Vierecke erscheinen.
        player.sendActionBar(
                Component.text("Neuer Rang!", data.color())
        );

        // UX-Trick:
        // Sounds leicht verzögert abspielen, damit zuerst der Title "ankommt".
        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            playPromotionSounds(player, rankName);
        }).delay(300, TimeUnit.MILLISECONDS).schedule();

        // Zweite ActionBar-Stufe: konkreter Rangname
        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.sendActionBar(
                    Component.text(data.displayName(), data.color())
            );
        }).delay(900, TimeUnit.MILLISECONDS).schedule();

        // Dritte ActionBar-Stufe: Abschluss
        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.sendActionBar(
                    Component.text("Glückwunsch!", data.color())
            );
        }).delay(1600, TimeUnit.MILLISECONDS).schedule();

        // Chat-Nachricht
        player.sendMessage(
                Component.text("Glückwunsch! Du wurdest zu ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" befördert.\nMach weiter so!"))
        );
    }

    /**
     * Sendet eine Demotion-Nachricht an einen Spieler.
     *
     * Bewusst etwas ruhiger gehalten als die Promotion.
     */
    public static void sendDemotion(Player player, String rankName, Scheduler scheduler, Object pluginInstance) {
        var data = RankDisplayHelper.get(rankName);

        // Title mit Rangsymbol und Erklärung
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

        // Erste ActionBar
        player.sendActionBar(
                Component.text("Rang angepasst", data.color())
        );

        // Demotion-Sound leicht verzögert
        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "block.note_block.bass"),
                            Sound.Source.MASTER,
                            1.0f,
                            0.8f
                    ),
                    Sound.Emitter.self()
            );
        }).delay(250, TimeUnit.MILLISECONDS).schedule();

        // Zweite ActionBar-Stufe
        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.sendActionBar(
                    Component.text(data.displayName(), data.color())
            );
        }).delay(900, TimeUnit.MILLISECONDS).schedule();

        // Chat-Nachricht
        player.sendMessage(
                Component.text("Dein Rang wurde auf ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" angepasst."))
        );
    }

    /**
     * Spielt die Promotion-Sounds ab.
     *
     * Enthält bewusst sicher:
     * - ui.toast.challenge_complete
     *
     * Zusätzlich:
     * - entity.player.levelup
     * - entity.experience_orb.pickup
     *
     * Lautstärke und Pitch skalieren leicht mit der Ranghöhe.
     */
    private static void playPromotionSounds(Player player, String rankName) {
        int rankIndex = getRankIndex(rankName);

        float toastVolume = clamp(1.0f + rankIndex * 0.03f, 1.0f, 1.4f);
        float toastPitch  = clamp(1.0f + rankIndex * 0.015f, 1.0f, 1.25f);

        float levelVolume = clamp(0.9f + rankIndex * 0.04f, 0.9f, 1.5f);
        float levelPitch  = clamp(1.0f + rankIndex * 0.02f, 1.0f, 1.35f);

        float orbVolume   = clamp(0.7f + rankIndex * 0.02f, 0.7f, 1.1f);
        float orbPitch    = clamp(1.2f + rankIndex * 0.02f, 1.2f, 1.45f);

        // Hauptsound – soll sicher vorkommen
        player.playSound(
                Sound.sound(
                        Key.key("minecraft", "ui.toast.challenge_complete"),
                        Sound.Source.MASTER,
                        toastVolume,
                        toastPitch
                ),
                Sound.Emitter.self()
        );

        // Zweiter Sound – klassischer Rangaufstieg
        player.playSound(
                Sound.sound(
                        Key.key("minecraft", "entity.player.levelup"),
                        Sound.Source.MASTER,
                        levelVolume,
                        levelPitch
                ),
                Sound.Emitter.self()
        );

        // Dritter Sound – kurzer Reward-Effekt
        player.playSound(
                Sound.sound(
                        Key.key("minecraft", "entity.experience_orb.pickup"),
                        Sound.Source.MASTER,
                        orbVolume,
                        orbPitch
                ),
                Sound.Emitter.self()
        );
    }

    /**
     * Ordnet jedem Rang einen Index zu.
     * Dieser Index wird benutzt, um Sound-Lautstärke und Pitch leicht zu skalieren.
     */
    private static int getRankIndex(String rankName) {
        if (rankName == null) return 0;

        return switch (rankName.toLowerCase(Locale.ROOT)) {
            case "beginner" -> 0;
            case "iron" -> 1;
            case "iron_plus" -> 2;
            case "bronze" -> 3;
            case "bronze_plus" -> 4;
            case "silver" -> 5;
            case "silver_plus" -> 6;
            case "gold" -> 7;
            case "gold_plus" -> 8;
            case "diamond" -> 9;
            case "diamond_plus" -> 10;
            case "netherite" -> 11;
            case "staff" -> 12;
            default -> 0;
        };
    }

    /**
     * Hilfsmethode zum Begrenzen von float-Werten.
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}