package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.market.Buyback;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Dorfbewohner-Handel: Dorfbewohner kaufen Alltagsware gegen Smaragde und verkaufen Sachen für Smaragde. Ein Smaragd
 * ist so für ein paar Taler zu haben (11 Glasscheiben beim Kartografen). Darum:
 *
 * <ul>
 *   <li>Vroni kauft keine Smaragde an (sonst druckt jeder Bauer Geld).</li>
 *   <li>Was ein Dorfbewohner für Smaragde hergibt (Redstone und Lapis beim Kleriker, Kettenrüstung zum Einschmelzen …),
 *       bringt über Vroni höchstens ein paar Taler pro Tag: Dorfbewohner haben nur wenig Nachschub, und Vroni zahlt den
 *       vollen Preis nur bis zur Tagesmenge, danach weniger (Ankaufstufen) – und der Kurs fällt dabei noch.</li>
 * </ul>
 *
 * Grundpreise der Dorfbewohner (Java 1.21, ohne Rabatte). Nach dem Heilen von Zombie-Dorfbewohnern werden sie billiger;
 * die Tagesgrenze bleibt.
 */
class VillagerTradeTest {

    /** Höchstens so viel Gewinn pro Spieler und Tag über einen Dorfbewohner-Umweg (Hundertstel). */
    private static final long MAX_PER_WARE = 300_000;
    private static final long MAX_TOTAL = 600_000;

    private static void sells(List<Recipes.Recipe> r, String out, int count, int emeralds) {
        r.add(new Recipes.Recipe(out, count, Map.of("emerald", emeralds), "dorf"));
    }

    private static void buys(List<Recipes.Recipe> r, String in, int count) {
        r.add(new Recipes.Recipe("emerald", 1, Map.of(in, count), "dorf"));
    }

    /** Dorfbewohner-Handel als Umwandlungen (Ware ↔ Smaragd). */
    static List<Recipes.Recipe> trades() {
        List<Recipes.Recipe> r = new ArrayList<>();
        // Dorfbewohner kaufen (Spieler gibt Ware, bekommt 1 Smaragd)
        buys(r, "wheat", 20);
        buys(r, "potato", 26);
        buys(r, "carrot", 22);
        buys(r, "beetroot", 15);
        buys(r, "pumpkin", 6);
        buys(r, "melon", 4);
        buys(r, "string", 20);
        buys(r, "coal", 15);
        buys(r, "cod", 15);
        buys(r, "salmon", 13);
        buys(r, "white_wool", 18);
        buys(r, "white_dye", 12);
        buys(r, "black_dye", 12);
        buys(r, "stick", 32);
        buys(r, "feather", 24);
        buys(r, "paper", 24);
        buys(r, "book", 4);
        buys(r, "glass_pane", 11);
        buys(r, "compass", 1);
        buys(r, "rotten_flesh", 32);
        buys(r, "gold_ingot", 3);
        buys(r, "rabbit_foot", 2);
        buys(r, "glass_bottle", 9);
        buys(r, "nether_wart", 22);
        buys(r, "iron_ingot", 4);
        buys(r, "diamond", 1);
        buys(r, "chicken", 14);
        buys(r, "porkchop", 7);
        buys(r, "mutton", 7);
        buys(r, "beef", 10);
        buys(r, "sweet_berries", 10);
        buys(r, "leather", 6);
        buys(r, "rabbit_hide", 9);
        buys(r, "clay_ball", 10);
        buys(r, "stone", 20);
        buys(r, "andesite", 16);
        buys(r, "diorite", 16);
        buys(r, "granite", 16);
        buys(r, "quartz", 12);
        // Dorfbewohner verkaufen (Spieler gibt Smaragde)
        sells(r, "redstone", 2, 1);
        sells(r, "lapis_lazuli", 1, 1);
        sells(r, "glowstone", 1, 4);
        sells(r, "ender_pearl", 1, 5);
        sells(r, "experience_bottle", 1, 3);
        sells(r, "bread", 6, 1);
        sells(r, "pumpkin_pie", 4, 1);
        sells(r, "apple", 4, 1);
        sells(r, "cookie", 18, 1);
        sells(r, "cake", 1, 1);
        sells(r, "golden_carrot", 3, 3);
        sells(r, "glistering_melon_slice", 3, 4);
        sells(r, "arrow", 16, 1);
        sells(r, "flint", 10, 1);
        sells(r, "bow", 1, 2);
        sells(r, "crossbow", 1, 3);
        sells(r, "bookshelf", 1, 9);
        sells(r, "lantern", 1, 1);
        sells(r, "glass", 4, 1);
        sells(r, "clock", 1, 5);
        sells(r, "compass", 1, 4);
        sells(r, "name_tag", 1, 20);
        sells(r, "item_frame", 1, 7);
        sells(r, "shears", 1, 2);
        sells(r, "white_wool", 1, 1);
        sells(r, "white_bed", 1, 3);
        sells(r, "painting", 3, 2);
        sells(r, "chainmail_boots", 1, 1);
        sells(r, "chainmail_helmet", 1, 1);
        sells(r, "chainmail_leggings", 1, 3);
        sells(r, "chainmail_chestplate", 1, 4);
        sells(r, "iron_helmet", 1, 5);
        sells(r, "iron_chestplate", 1, 9);
        sells(r, "iron_leggings", 1, 7);
        sells(r, "iron_boots", 1, 4);
        sells(r, "shield", 1, 5);
        sells(r, "bell", 1, 36);
        sells(r, "stone_axe", 1, 1);
        sells(r, "stone_pickaxe", 1, 1);
        sells(r, "iron_axe", 1, 3);
        sells(r, "iron_sword", 1, 2);
        sells(r, "brick", 10, 1);
        sells(r, "quartz_block", 1, 1);
        sells(r, "cooked_porkchop", 5, 1);
        sells(r, "cooked_chicken", 8, 1);
        sells(r, "saddle", 1, 6);
        return r;
    }

    @Test
    void vroniBuysNoEmeralds() {
        Catalog c = CatalogTest.catalog(new ArrayList<>());
        assertFalse(c.ware("minecraft:emerald").sellable());
        assertTrue(c.ware("minecraft:emerald_block") == null || !c.ware("minecraft:emerald_block").sellable());
    }

    /** Gewinn pro Tag über alle Ankaufstufen, solange ein Stück mehr bringt, als es kostet (endlich!). */
    static long dailyProfit(long sell, double cost, int daily, List<Buyback.Tier> tiers) {
        long profit = 0;
        for (int i = 0; i < tiers.size(); i++) {
            double price = sell * tiers.get(i).percent() / 100.0;
            if (price <= cost) {
                continue;
            }
            assertTrue(i + 1 < tiers.size(), "auch die letzte Ankaufstufe bringt noch Gewinn – unbegrenzt");
            long band = Math.round((tiers.get(i + 1).fromDaily() - tiers.get(i).fromDaily()) * daily);
            profit += Math.round((price - cost) * band);
        }
        return profit;
    }

    @Test
    void villagerDetoursAreSmallAndBounded() {
        Catalog c = CatalogTest.catalog(new ArrayList<>());
        List<Buyback.Tier> tiers = CatalogTest.bundled().buybackTiers;
        Map<String, Long> buy = new HashMap<>();
        c.wares().values().forEach(w -> buy.put(w.key(), w.buy()));
        List<Recipes.Recipe> all = new ArrayList<>(Recipes.ALL);
        all.addAll(trades());
        Map<String, Double> cheapest = LoopGuard.cheapest(buy, all);
        double emerald = cheapest.get("minecraft:emerald");
        assertTrue(emerald < 500, "ein Smaragd beim Dorfbewohner kostet nur " + emerald + " Hundertstel");
        long total = 0;
        StringBuilder report = new StringBuilder();
        for (Ware w : c.wares().values()) {
            Double cost = cheapest.get(w.key());
            if (!w.sellable() || cost == null || w.sell() <= cost) {
                continue;
            }
            long day = dailyProfit(w.sell(), cost, w.daily(), tiers);
            report.append(w.id()).append(": ").append(day / 100).append(" ₮/Tag; ");
            assertTrue(day <= MAX_PER_WARE, w.key() + " über Dorfbewohner: " + day / 100 + " ₮ pro Tag ("
                    + LoopGuard.route(w.key(), cheapest, all) + ")");
            total += day;
        }
        System.out.println("Dorfbewohner-Umwege (Smaragd ab " + emerald / 100 + " ₮): " + report);
        assertTrue(total <= MAX_TOTAL, "alle Dorfbewohner-Umwege zusammen " + total / 100 + " ₮ pro Tag: " + report);
    }
}
