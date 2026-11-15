package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SearchWordsTest {

    @Test
    void germanWordsFindEnglishKeys() {
        assertTrue(SearchWords.matches("minecraft:diamond_sword", SearchWords.terms("Diamant Schwert")));
        assertTrue(SearchWords.matches("minecraft:cooked_beef", SearchWords.terms("steak")));
        assertFalse(SearchWords.matches("minecraft:iron_sword", SearchWords.terms("Diamant Schwert")));
        assertTrue(SearchWords.matches("minecraft:oak_log", SearchWords.terms("eiche holz")));
        assertTrue(SearchWords.matches("anything", SearchWords.terms("")));
        assertEquals(2, SearchWords.terms("  rot   wolle ").size());
    }

    @Test
    void categories() {
        assertEquals(Category.TOOLS, Category.of("minecraft:diamond_pickaxe", false, false));
        assertEquals(Category.ARMOR, Category.of("minecraft:netherite_chestplate", false, false));
        assertEquals(Category.ARMOR, Category.of("minecraft:elytra", false, false));
        assertEquals(Category.FOOD, Category.of("minecraft:bread", false, true));
        assertEquals(Category.FOOD, Category.of("minecraft:golden_apple", false, true));
        assertEquals(Category.REDSTONE, Category.of("minecraft:piston", true, false));
        assertEquals(Category.REDSTONE, Category.of("minecraft:oak_door", true, false));
        assertEquals(Category.MAGIC, Category.of("minecraft:enchanted_book|sharpness=5", false, false));
        assertEquals(Category.BLOCKS, Category.of("minecraft:stone_bricks", true, false));
        assertEquals(Category.OTHER, Category.of("minecraft:stick", false, false));
    }
}
