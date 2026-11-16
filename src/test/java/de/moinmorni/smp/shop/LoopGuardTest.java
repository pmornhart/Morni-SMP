package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.Settings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Der Schleifen-Wächter: billigster Weg zu jeder Sorte, gesenkte Ankaufspreise – auch bei wilden Faktoren. */
class LoopGuardTest {

    @Test
    void cheapestFollowsRecipesAcrossSteps() {
        // Stamm 1,20 → 4 Bretter (0,30) → 4 Stöcke aus 2 Brettern (0,15)
        Map<String, Double> c = LoopGuard.cheapest(Map.of("minecraft:oak_log", 120L), Recipes.ALL);
        assertEquals(30.0, c.get("minecraft:oak_planks"), 1e-9);
        assertEquals(15.0, c.get("minecraft:stick"), 1e-9);
        assertEquals(120.0, c.get("minecraft:charcoal"), 1e-9, "Holzkohle aus dem Ofen");
        assertFalse(c.containsKey("minecraft:diamond"), "an Diamanten kommt man so nicht");
        // gekauft ist billiger als gemacht → der Kaufpreis zählt
        Map<String, Double> c2 = LoopGuard.cheapest(Map.of("minecraft:oak_log", 120L, "minecraft:stick", 5L),
                Recipes.ALL);
        assertEquals(5.0, c2.get("minecraft:stick"), 1e-9);
        assertEquals(14, LoopGuard.maxSell("minecraft:stick", Map.of("minecraft:stick", 15.0)), "echt darunter");
        assertEquals(Long.MAX_VALUE, LoopGuard.maxSell("minecraft:diamond", c));
    }

    @Test
    void routeExplainsTheLoop() {
        Map<String, Double> c = LoopGuard.cheapest(Map.of("minecraft:iron_block", 900L), Recipes.ALL);
        assertEquals(100.0, c.get("minecraft:iron_ingot"), 1e-9);
        String route = LoopGuard.route("minecraft:iron_ingot", c, Recipes.ALL);
        assertTrue(route.contains("iron_block") && route.contains("9×"), route);
    }

    @Test
    void guardLowersSellPricesThatWouldOpenALoop() {
        List<String> warnings = new ArrayList<>();
        // Eisenblock für 9 ₮ kaufen, in 9 Barren zerlegen, jeden für 5 ₮ verkaufen = 45 ₮ – das darf nicht gehen
        Catalog c = Catalog.of(List.of(new Catalog.Category("erze", "diamond", "Erze", "Ores")), List.of(
                        new Ware("iron_block", "erze", 900, 0, 64, 0, Set.of()),
                        new Ware("iron_ingot", "erze", 2_000, 500, 64, 0, Set.of())),
                40, null, warnings::add);
        assertEquals(99, c.ware("minecraft:iron_ingot").sell(), "unter 9 ₮ / 9 = 1 ₮");
        assertEquals(1, warnings.size(), String.join("\n", warnings));
        assertTrue(warnings.get(0).contains("Geld-Schleife") && warnings.get(0).contains("iron_block"), warnings.get(0));
    }

    /**
     * Mit beliebigen Faktoren je Abteilung (0,05 … 100) bleibt das Sortiment ohne Schleife – der Wächter senkt, was nötig
     * ist. 40 zufällige Mischungen.
     */
    @Test
    void wildFactorsNeverOpenALoop() {
        Settings s = CatalogTest.bundled();
        Random rnd = new Random(4711);
        for (int round = 0; round < 40; round++) {
            Map<String, Double> f = new HashMap<>();
            for (Catalog.Category cat : s.shopCategories) {
                f.put(cat.id(), Math.exp(Math.log(0.05) + rnd.nextDouble() * (Math.log(100) - Math.log(0.05))));
            }
            Catalog c = Catalog.of(s.shopCategories, s.shopWares, s.shopMaxSellPercent, f, null, w -> { });
            Map<String, Long> buy = new HashMap<>();
            c.wares().values().forEach(w -> buy.put(w.key(), w.buy()));
            Map<String, Double> cheapest = LoopGuard.cheapest(buy, Recipes.ALL);
            for (Ware w : c.wares().values()) {
                Double cost = cheapest.get(w.key());
                if (w.sellable() && cost != null) {
                    assertTrue(w.sell() < cost, "Runde " + round + ": " + w.key() + " " + w.sell() + " ≥ " + cost);
                }
                if (w.sellable() && w.buyable()) {
                    assertTrue(w.sell() * 100 <= w.buy() * s.shopMaxSellPercent, w.key());
                }
            }
        }
    }

    @Test
    void recipesAreWellFormed() {
        Set<String> seen = new HashSet<>();
        for (Recipes.Recipe r : Recipes.ALL) {
            assertTrue(r.out().startsWith("minecraft:"), r.out());
            assertTrue(r.count() >= 1);
            assertFalse(r.in().isEmpty());
            assertFalse(r.in().containsKey(r.out()), "Rezept aus sich selbst: " + r.out());
            r.in().forEach((k, v) -> assertTrue(k.startsWith("minecraft:") && v >= 1, k));
            assertTrue(Set.of("werkbank", "ofen", "abbauen").contains(r.how()), r.how());
            assertTrue(seen.add(r.out() + r.count() + r.in()), "doppelt: " + r);
            assertFalse(r.out().endsWith(":chain") || r.in().containsKey("minecraft:chain"), "26.x heißt es iron_chain");
        }
        assertTrue(Recipes.ALL.size() >= 180, "Rezepte: " + Recipes.ALL.size());
    }
}
