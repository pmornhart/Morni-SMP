package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.Settings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Das mitgelieferte Sortiment: gültig, fair zum freien Markt und ohne Geld-Schleifen. */
class CatalogTest {

    static Settings bundled() {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                CatalogTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        YamlConfiguration user = new YamlConfiguration();
        user.setDefaults(defaults);
        user.options().copyDefaults(true);
        return Settings.load(user);
    }

    static Catalog catalog(List<String> warnings) {
        Settings s = bundled();
        return Catalog.of(s.shopCategories, s.shopWares, s.shopMaxSellPercent, null, warnings::add);
    }

    @Test
    void bundledListIsCleanAndBig() {
        List<String> warnings = new ArrayList<>();
        Catalog c = catalog(warnings);
        assertEquals(List.of(), warnings, "die mitgelieferte Liste braucht keine Korrektur");
        assertTrue(c.size() >= 150 && c.size() <= 260, "Waren: " + c.size());
        assertEquals(10, c.categories().size());
        for (Catalog.Category cat : c.categories()) {
            assertTrue(c.wares(cat.id(), 11).size() >= 10, "Abteilung " + cat.id() + " hat zu wenig");
            assertTrue(c.wares(cat.id(), 11).size() <= 36 * 2, "Abteilung " + cat.id() + " passt auf zwei Seiten");
            assertFalse(cat.de().isBlank());
            assertFalse(cat.en().isBlank());
        }
        long sellable = c.wares().values().stream().filter(Ware::sellable).count();
        assertTrue(sellable >= 80, "Ankauf: " + sellable);
    }

    @Test
    void sellIsAlwaysWellBelowBuy() {
        Settings s = bundled();
        for (Ware w : catalog(new ArrayList<>()).wares().values()) {
            if (w.buyable() && w.sellable()) {
                assertTrue(w.sell() * 100 <= w.buy() * s.shopMaxSellPercent, w.key() + ": Ankauf zu hoch");
                assertTrue(w.sell() < w.buy(), w.key());
            }
        }
    }

    /**
     * Keine Schleife über die Werkbank oder den Ofen: Zutaten beim Händler kaufen und das Ergebnis an ihn verkaufen
     * bringt nie mehr, als es gekostet hat (Brennstoff nicht mitgerechnet – das macht es nur noch teurer).
     */
    @Test
    void noMoneyLoopThroughCraftingOrSmelting() {
        Catalog c = catalog(new ArrayList<>());
        // Ergebnis × Anzahl ← Zutaten
        record Recipe(String out, int count, Map<String, Integer> in) {
        }
        List<Recipe> recipes = new ArrayList<>();
        for (String wood : List.of("oak", "spruce", "birch", "dark_oak")) {
            recipes.add(new Recipe(wood + "_planks", 4, Map.of(wood + "_log", 1)));
        }
        recipes.add(new Recipe("stick", 4, Map.of("oak_planks", 2)));
        recipes.add(new Recipe("charcoal", 1, Map.of("oak_log", 1)));
        recipes.add(new Recipe("copper_ingot", 1, Map.of("raw_copper", 1)));
        recipes.add(new Recipe("iron_ingot", 1, Map.of("raw_iron", 1)));
        recipes.add(new Recipe("gold_ingot", 1, Map.of("raw_gold", 1)));
        recipes.add(new Recipe("iron_ingot", 1, Map.of("iron_nugget", 9)));
        recipes.add(new Recipe("gold_ingot", 1, Map.of("gold_nugget", 9)));
        recipes.add(new Recipe("iron_ingot", 9, Map.of("iron_block", 1)));
        recipes.add(new Recipe("gold_ingot", 9, Map.of("gold_block", 1)));
        recipes.add(new Recipe("emerald", 9, Map.of("emerald_block", 1)));
        recipes.add(new Recipe("coal", 9, Map.of("coal_block", 1)));
        recipes.add(new Recipe("redstone", 9, Map.of("redstone_block", 1)));
        recipes.add(new Recipe("wheat", 9, Map.of("hay_block", 1)));
        recipes.add(new Recipe("melon_slice", 9, Map.of("melon", 1)));
        recipes.add(new Recipe("white_wool", 1, Map.of("string", 4)));
        recipes.add(new Recipe("glass", 1, Map.of("sand", 1)));
        recipes.add(new Recipe("stone", 1, Map.of("cobblestone", 1)));
        recipes.add(new Recipe("bread", 1, Map.of("wheat", 3)));
        recipes.add(new Recipe("sugar", 1, Map.of("sugar_cane", 1)));
        recipes.add(new Recipe("bone_meal", 3, Map.of("bone", 1)));
        recipes.add(new Recipe("blaze_powder", 2, Map.of("blaze_rod", 1)));
        recipes.add(new Recipe("arrow", 4, Map.of("feather", 1, "stick", 1)));
        recipes.add(new Recipe("paper", 3, Map.of("sugar_cane", 3)));
        recipes.add(new Recipe("book", 1, Map.of("paper", 3, "leather", 1)));
        recipes.add(new Recipe("dried_kelp", 1, Map.of("kelp", 1)));
        recipes.add(new Recipe("cooked_beef", 1, Map.of("beef", 1)));
        recipes.add(new Recipe("cooked_porkchop", 1, Map.of("porkchop", 1)));
        recipes.add(new Recipe("cooked_chicken", 1, Map.of("chicken", 1)));
        recipes.add(new Recipe("cooked_salmon", 1, Map.of("salmon", 1)));
        recipes.add(new Recipe("baked_potato", 1, Map.of("potato", 1)));
        recipes.add(new Recipe("quartz", 4, Map.of("quartz_block", 1)));
        recipes.add(new Recipe("ender_eye", 1, Map.of("ender_pearl", 1, "blaze_powder", 1)));
        recipes.add(new Recipe("pumpkin_seeds", 4, Map.of("pumpkin", 1)));
        recipes.add(new Recipe("melon_seeds", 1, Map.of("melon_slice", 1)));
        recipes.add(new Recipe("glowstone_dust", 4, Map.of("glowstone", 1)));
        recipes.add(new Recipe("black_dye", 1, Map.of("ink_sac", 1)));
        recipes.add(new Recipe("lapis_lazuli", 9, Map.of("lapis_block", 1)));
        recipes.add(new Recipe("string", 4, Map.of("white_wool", 1)));
        int checked = 0;
        for (Recipe r : recipes) {
            Ware out = c.ware("minecraft:" + r.out());
            if (out == null || !out.sellable()) {
                continue;
            }
            long cost = 0;
            boolean allBuyable = true;
            for (Map.Entry<String, Integer> in : r.in().entrySet()) {
                Ware w = c.ware("minecraft:" + in.getKey());
                if (w == null || !w.buyable()) {
                    allBuyable = false;
                    break;
                }
                cost += w.buy() * in.getValue();
            }
            if (!allBuyable) {
                continue;
            }
            long gain = out.sell() * r.count();
            assertTrue(gain < cost, "Geld-Schleife: " + r.in() + " → " + r.count() + "× " + r.out() + " kostet "
                    + cost + ", bringt " + gain);
            checked++;
        }
        assertTrue(checked >= 15, "zu wenige Rezepte geprüft: " + checked);
        // gepresste Blöcke kauft der Händler nicht an (sonst ließe sich die Tagesmenge umgehen)
        for (String block : Set.of("iron_block", "gold_block", "emerald_block", "coal_block", "redstone_block",
                "hay_block", "melon")) {
            Ware w = c.ware("minecraft:" + block);
            assertTrue(w == null || !w.sellable(), block + " wird angekauft");
        }
    }

    @Test
    void checksDropBadEntries() {
        List<String> warnings = new ArrayList<>();
        Catalog c = Catalog.of(List.of(new Catalog.Category("a", "stone", "A", "A")), List.of(
                new Ware("stone", "a", 100, 90, 64, 0, Set.of()),
                new Ware("stone", "a", 50, 10, 64, 0, Set.of()),
                new Ware("dirt", "b", 50, 10, 64, 0, Set.of()),
                new Ware("gravel", "a", 0, 0, 64, 0, Set.of()),
                new Ware("unbekannt", "a", 10, 0, 64, 0, Set.of())), 40, k -> !k.endsWith("unbekannt"),
                warnings::add);
        assertEquals(1, c.size());
        assertEquals(40, c.ware("minecraft:stone").sell(), "gedeckelt");
        assertEquals(5, warnings.size(), String.join("\n", warnings));
    }

    @Test
    void seasonAndSearch() {
        Catalog c = catalog(new ArrayList<>());
        Ware snow = c.ware("minecraft:snowball");
        assertNotNull(snow);
        assertFalse(snow.inSeason(7));
        assertTrue(snow.inSeason(12));
        assertTrue(c.wares("besonderes", 12).contains(snow));
        assertFalse(c.wares("besonderes", 7).contains(snow));
        assertTrue(c.search("diamant", 11).stream().anyMatch(w -> w.id().equals("diamond")), "deutsch");
        assertTrue(c.search("oak log", 11).stream().anyMatch(w -> w.id().equals("oak_log")), "englisch");
        assertTrue(c.search("eisen", 11).size() >= 5);
        assertTrue(c.search("", 11).isEmpty());
        assertEquals("wheat", c.search("weizen", 11).get(0).id(), "genauer Treffer zuerst");
        assertEquals("ender_chest", c.search("endertruhe", 11).get(0).id());
        assertEquals(c.wares().values().stream().filter(Ware::sellable).count(), c.sellOffers().size());
    }
}
