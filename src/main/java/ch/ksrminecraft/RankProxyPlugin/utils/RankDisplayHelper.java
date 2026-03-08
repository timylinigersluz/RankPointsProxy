package ch.ksrminecraft.RankProxyPlugin.utils;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.Map;

/**
 * RankDisplayHelper
 *
 * Liefert Anzeigeinformationen für einen Rang:
 * - Glyph aus dem Resource Pack
 * - Anzeigename
 * - Farbe
 */
public final class RankDisplayHelper {

    public record RankDisplayData(String glyph, String displayName, NamedTextColor color) {}

    private static final Map<String, RankDisplayData> RANKS = Map.ofEntries(
            Map.entry("beginner", new RankDisplayData("\uE000", "Beginner", NamedTextColor.GRAY)),
            Map.entry("iron", new RankDisplayData("\uE001", "Iron", NamedTextColor.WHITE)),
            Map.entry("iron_plus", new RankDisplayData("\uE002", "Iron Plus", NamedTextColor.WHITE)),
            Map.entry("bronze", new RankDisplayData("\uE003", "Bronze", NamedTextColor.GOLD)),
            Map.entry("bronze_plus", new RankDisplayData("\uE004", "Bronze Plus", NamedTextColor.GOLD)),
            Map.entry("silver", new RankDisplayData("\uE005", "Silver", NamedTextColor.AQUA)),
            Map.entry("silver_plus", new RankDisplayData("\uE006", "Silver Plus", NamedTextColor.AQUA)),
            Map.entry("gold", new RankDisplayData("\uE007", "Gold", NamedTextColor.YELLOW)),
            Map.entry("gold_plus", new RankDisplayData("\uE008", "Gold Plus", NamedTextColor.YELLOW)),
            Map.entry("diamond", new RankDisplayData("\uE009", "Diamond", NamedTextColor.BLUE)),
            Map.entry("diamond_plus", new RankDisplayData("\uE00A", "Diamond Plus", NamedTextColor.BLUE)),
            Map.entry("netherite", new RankDisplayData("\uE00B", "Netherite", NamedTextColor.DARK_GRAY)),
            Map.entry("staff", new RankDisplayData("\uE00C", "Staff", NamedTextColor.GREEN))
    );

    private RankDisplayHelper() {
    }

    public static RankDisplayData get(String rankName) {
        if (rankName == null) {
            return new RankDisplayData("?", "Unbekannt", NamedTextColor.WHITE);
        }

        return RANKS.getOrDefault(
                rankName.toLowerCase(Locale.ROOT),
                new RankDisplayData("?", rankName, NamedTextColor.WHITE)
        );
    }
}