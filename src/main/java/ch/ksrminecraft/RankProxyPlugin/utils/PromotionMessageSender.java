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
 * Zuständig für die visuelle und akustische Rückmeldung bei:
 * - Promotionen
 * - Demotionen
 * - Staff-Ernennungen
 * - Staff-Entfernungen
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
 * Grund: Die ActionBar rendert Custom-Fonts nicht immer zuverlässig.
 */
public final class PromotionMessageSender {

    /**
     * Custom Font aus dem Resource Pack für die Rangsymbole.
     * Muss zu assets/ksr/font/ranks.json passen.
     */
    private static final Key RANK_FONT = Key.key("ksr", "ranks");

    private PromotionMessageSender() {
        // Utility-Klasse
    }

    /**
     * Sendet eine Promotion-Nachricht an einen Spieler.
     */
    public static void sendPromotion(Player player, String rankName, Scheduler scheduler, Object pluginInstance) {
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

        player.sendActionBar(Component.text("Neuer Rang!", data.color()));

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            playPromotionSounds(player, rankName);
        }).delay(300, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text(data.displayName(), data.color()));
        }).delay(900, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text("Glückwunsch!", data.color()));
        }).delay(1600, TimeUnit.MILLISECONDS).schedule();

        player.sendMessage(
                Component.text("Glückwunsch! Du wurdest zu ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" befördert.\nMach weiter so!"))
        );
    }

    /**
     * Sendet eine Demotion-Nachricht an einen Spieler.
     */
    public static void sendDemotion(Player player, String rankName, Scheduler scheduler, Object pluginInstance) {
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

        player.sendActionBar(Component.text("Rang angepasst", data.color()));

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

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text(data.displayName(), data.color()));
        }).delay(900, TimeUnit.MILLISECONDS).schedule();

        player.sendMessage(
                Component.text("Dein Rang wurde auf ")
                        .append(Component.text(data.displayName(), data.color()))
                        .append(Component.text(" angepasst."))
        );
    }

    /**
     * Sendet ein besonders pompöses Event für neu ernannte Staff-Mitglieder.
     *
     * Dieses Event ist bewusst stärker als eine normale Promotion.
     */
    public static void sendStaffAppointment(Player player, Scheduler scheduler, Object pluginInstance) {
        var data = RankDisplayHelper.get("staff");

        Title title = Title.title(
                Component.text(data.glyph()).font(RANK_FONT),
                Component.text("Bravo, du gehörst nun zum Staff-Team.", data.color()),
                Title.Times.times(
                        Duration.ofMillis(900),
                        Duration.ofSeconds(10),
                        Duration.ofMillis(1500)
                )
        );

        player.showTitle(title);

        player.sendActionBar(Component.text("Willkommen im Staff-Team!", data.color()));

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "ui.toast.challenge_complete"),
                            Sound.Source.MASTER,
                            1.25f,
                            1.0f
                    ),
                    Sound.Emitter.self()
            );

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "entity.ender_dragon.death"),
                            Sound.Source.MASTER,
                            0.95f,
                            1.1f
                    ),
                    Sound.Emitter.self()
            );

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "entity.player.levelup"),
                            Sound.Source.MASTER,
                            1.15f,
                            1.0f
                    ),
                    Sound.Emitter.self()
            );
        }).delay(300, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text("Staff-Team freigeschaltet", data.color()));
        }).delay(1100, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text("Vielen Dank für deinen Einsatz!", data.color()));
        }).delay(2200, TimeUnit.MILLISECONDS).schedule();

        player.sendMessage(
                Component.text("Glückwunsch! Du gehörst nun zum ")
                        .append(Component.text("Staff-Team", data.color()))
                        .append(Component.text(".\nVielen Dank für deinen Einsatz!"))
        );
    }

    /**
     * Sendet ein eigenes Event für das Entfernen aus dem Staff-Team.
     *
     * Bewusst nicht wie eine normale Demotion, sondern als separater Statuswechsel.
     */
    public static void sendStaffRemoval(Player player, Scheduler scheduler, Object pluginInstance) {
        var data = RankDisplayHelper.get("staff");

        Title title = Title.title(
                Component.text(data.glyph()).font(RANK_FONT),
                Component.text("Du gehörst nicht mehr zum Staff-Team.", data.color()),
                Title.Times.times(
                        Duration.ofMillis(800),
                        Duration.ofSeconds(9),
                        Duration.ofMillis(1300)
                )
        );

        player.showTitle(title);

        player.sendActionBar(Component.text("Staff-Status entfernt", data.color()));

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "block.note_block.bass"),
                            Sound.Source.MASTER,
                            1.0f,
                            0.75f
                    ),
                    Sound.Emitter.self()
            );

            player.playSound(
                    Sound.sound(
                            Key.key("minecraft", "entity.allay.hurt"),
                            Sound.Source.MASTER,
                            0.9f,
                            0.9f
                    ),
                    Sound.Emitter.self()
            );
        }).delay(250, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text("Zur normalen Laufbahn zurückgesetzt", data.color()));
        }).delay(1100, TimeUnit.MILLISECONDS).schedule();

        scheduler.buildTask(pluginInstance, () -> {
            if (!player.isActive()) return;
            player.sendActionBar(Component.text("Der Spielerrang folgt wieder über Punkte", data.color()));
        }).delay(2200, TimeUnit.MILLISECONDS).schedule();

        player.sendMessage(
                Component.text("Dein ")
                        .append(Component.text("Staff-Status", data.color()))
                        .append(Component.text(" wurde entfernt.\nDein normaler Spielerrang wird künftig wieder über deine Rangpunkte bestimmt."))
        );
    }

    /**
     * Spielt die Promotion-Sounds ab.
     *
     * Enthält bewusst sicher:
     * - ui.toast.challenge_complete
     * Zusätzlich:
     * - entity.player.levelup
     * - entity.experience_orb.pickup
     */
    private static void playPromotionSounds(Player player, String rankName) {
        int rankIndex = getRankIndex(rankName);

        float toastVolume = clamp(1.0f + rankIndex * 0.03f, 1.0f, 1.4f);
        float toastPitch  = clamp(1.0f + rankIndex * 0.015f, 1.0f, 1.25f);

        float levelVolume = clamp(0.9f + rankIndex * 0.04f, 0.9f, 1.5f);
        float levelPitch  = clamp(1.0f + rankIndex * 0.02f, 1.0f, 1.35f);

        float orbVolume   = clamp(0.7f + rankIndex * 0.02f, 0.7f, 1.1f);
        float orbPitch    = clamp(1.2f + rankIndex * 0.02f, 1.2f, 1.45f);

        player.playSound(
                Sound.sound(
                        Key.key("minecraft", "ui.toast.challenge_complete"),
                        Sound.Source.MASTER,
                        toastVolume,
                        toastPitch
                ),
                Sound.Emitter.self()
        );

        player.playSound(
                Sound.sound(
                        Key.key("minecraft", "entity.player.levelup"),
                        Sound.Source.MASTER,
                        levelVolume,
                        levelPitch
                ),
                Sound.Emitter.self()
        );

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
     * Dieser Index wird für leichte Sound-Skalierung benutzt.
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
     * Begrenzung von float-Werten.
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}