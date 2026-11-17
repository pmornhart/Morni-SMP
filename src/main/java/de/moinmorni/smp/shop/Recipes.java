package de.moinmorni.smp.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Umwege, über die man aus gekauften Sachen andere Sachen macht – Werkbank, Ofen und Abbauen (z. B. Ton → 4 Tonklumpen).
 * Der {@link LoopGuard} rechnet damit aus, wie billig man an eine Ware kommt, und senkt Ankaufspreise, die sonst eine
 * Geld-Schleife öffnen würden (beim Händler kaufen → umwandeln → an den Händler verkaufen).
 *
 * <p>Nur Werkbank, Ofen, Steinsäge und Abbauen: Sie gehen sofort und unbegrenzt. Dorfbewohner sind pro Tag begrenzt
 * (Nachschub) und stehen nur im Test. Brennstoff ist nicht mitgerechnet – er macht jeden Umweg nur teurer.
 */
public final class Recipes {

    /** {@code count} × {@code out} aus {@code in} (Sorte → Stück). {@code how}: werkbank, ofen, abbauen. */
    public record Recipe(String out, int count, Map<String, Integer> in, String how) {
        public Recipe {
            out = key(out);
            Map<String, Integer> m = new LinkedHashMap<>();
            in.forEach((k, v) -> m.put(key(k), v));
            in = Map.copyOf(m);
        }
    }

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "pale_oak"};
    private static final String[] TOOLS_3 = {"pickaxe", "axe"};

    /** Alle bekannten Umwege. */
    public static final List<Recipe> ALL = build();

    private Recipes() {
    }

    private static String key(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static List<Recipe> build() {
        List<Recipe> r = new ArrayList<>();
        // ---- Holz
        for (String w : WOODS) {
            craft(r, w + "_planks", 4, w + "_log", 1);
            smelt(r, "charcoal", w + "_log");
        }
        craft(r, "crimson_planks", 4, "crimson_stem", 1);
        craft(r, "warped_planks", 4, "warped_stem", 1);
        craft(r, "bamboo_block", 1, "bamboo", 9);
        craft(r, "bamboo_planks", 2, "bamboo_block", 1);
        for (String w : new String[] {"oak", "spruce", "birch", "dark_oak"}) {
            craft(r, "stick", 4, w + "_planks", 2);
            craft(r, "chest", 1, w + "_planks", 8);
            craft(r, w + "_slab", 6, w + "_planks", 3);
        }
        craft(r, "stick", 1, "bamboo", 2);
        craft(r, "barrel", 1, Map.of("oak_planks", 6, "oak_slab", 2));
        craft(r, "bookshelf", 1, Map.of("oak_planks", 6, "book", 3));
        craft(r, "paper", 3, "sugar_cane", 3);
        craft(r, "book", 1, Map.of("paper", 3, "leather", 1));
        craft(r, "map", 1, Map.of("paper", 8, "compass", 1));
        craft(r, "torch", 4, Map.of("coal", 1, "stick", 1));
        craft(r, "torch", 4, Map.of("charcoal", 1, "stick", 1));
        craft(r, "item_frame", 1, Map.of("stick", 8, "leather", 1));
        craft(r, "painting", 1, Map.of("stick", 8, "white_wool", 1));
        craft(r, "lever", 1, Map.of("stick", 1, "cobblestone", 1));
        craft(r, "white_bed", 1, Map.of("white_wool", 3, "oak_planks", 3));
        craft(r, "white_banner", 1, Map.of("white_wool", 6, "stick", 1));
        craft(r, "armor_stand", 1, Map.of("stick", 6, "smooth_stone_slab", 1));
        craft(r, "smooth_stone_slab", 6, "smooth_stone", 3);
        craft(r, "bowl", 4, "oak_planks", 3);
        // ---- Stein, Sand, Glas, Ton
        smelt(r, "stone", "cobblestone");
        smelt(r, "smooth_stone", "stone");
        smelt(r, "deepslate", "cobbled_deepslate");
        mine(r, "cobblestone", 1, "stone", 1);
        craft(r, "stone_bricks", 4, "stone", 4);
        craft(r, "stone_button", 1, "stone", 1);
        craft(r, "polished_andesite", 4, "andesite", 4);
        craft(r, "diorite", 2, Map.of("cobblestone", 2, "quartz", 2));
        craft(r, "andesite", 2, Map.of("diorite", 1, "cobblestone", 1));
        craft(r, "granite", 1, Map.of("diorite", 1, "quartz", 1));
        craft(r, "sandstone", 1, "sand", 4);
        smelt(r, "glass", "sand");
        smelt(r, "glass", "red_sand");
        craft(r, "glass_pane", 16, "glass", 6);
        craft(r, "glass_bottle", 3, "glass", 3);
        mine(r, "clay_ball", 4, "clay", 1);
        smelt(r, "brick", "clay_ball");
        smelt(r, "terracotta", "clay");
        craft(r, "bricks", 1, "brick", 4);
        craft(r, "flower_pot", 1, "brick", 3);
        mine(r, "flint", 1, "gravel", 1);
        mine(r, "dirt", 1, "grass_block", 1);
        craft(r, "snow_block", 1, "snowball", 4);
        mine(r, "snowball", 4, "snow_block", 1);
        craft(r, "packed_ice", 1, "ice", 9);
        craft(r, "blue_ice", 1, "packed_ice", 9);
        smelt(r, "nether_brick", "netherrack");
        craft(r, "nether_bricks", 1, "nether_brick", 4);
        craft(r, "quartz_block", 1, "quartz", 4);
        craft(r, "prismarine", 1, "prismarine_shard", 4);
        craft(r, "sea_lantern", 1, Map.of("prismarine_shard", 4, "prismarine_crystals", 5));
        craft(r, "glowstone", 1, "glowstone_dust", 4);
        mine(r, "glowstone_dust", 4, "glowstone", 1);
        smelt(r, "green_dye", "cactus");
        // ---- Erze, Barren, Blöcke
        storage(r, "coal", "coal_block");
        storage(r, "copper_ingot", "copper_block");
        storage(r, "iron_ingot", "iron_block");
        storage(r, "gold_ingot", "gold_block");
        storage(r, "emerald", "emerald_block");
        storage(r, "diamond", "diamond_block");
        storage(r, "lapis_lazuli", "lapis_block");
        storage(r, "redstone", "redstone_block");
        storage(r, "raw_iron", "raw_iron_block");
        storage(r, "raw_gold", "raw_gold_block");
        storage(r, "raw_copper", "raw_copper_block");
        storage(r, "iron_nugget", "iron_ingot");
        storage(r, "gold_nugget", "gold_ingot");
        storage(r, "netherite_ingot", "netherite_block");
        smelt(r, "copper_ingot", "raw_copper");
        smelt(r, "iron_ingot", "raw_iron");
        smelt(r, "gold_ingot", "raw_gold");
        smelt(r, "netherite_scrap", "ancient_debris");
        craft(r, "netherite_ingot", 1, Map.of("netherite_scrap", 4, "gold_ingot", 4));
        craft(r, "amethyst_block", 1, "amethyst_shard", 4);
        // ---- Werkzeug und Rüstung (Eisen und Gold lassen sich im Ofen zu Nuggets schmelzen)
        for (String t : TOOLS_3) {
            craft(r, "iron_" + t, 1, Map.of("iron_ingot", 3, "stick", 2));
            craft(r, "diamond_" + t, 1, Map.of("diamond", 3, "stick", 2));
            craft(r, "stone_" + t, 1, Map.of("cobblestone", 3, "stick", 2));
        }
        craft(r, "iron_shovel", 1, Map.of("iron_ingot", 1, "stick", 2));
        craft(r, "iron_sword", 1, Map.of("iron_ingot", 2, "stick", 1));
        craft(r, "diamond_sword", 1, Map.of("diamond", 2, "stick", 1));
        craft(r, "stone_sword", 1, Map.of("cobblestone", 2, "stick", 1));
        craft(r, "iron_helmet", 1, "iron_ingot", 5);
        craft(r, "iron_chestplate", 1, "iron_ingot", 8);
        craft(r, "iron_leggings", 1, "iron_ingot", 7);
        craft(r, "iron_boots", 1, "iron_ingot", 4);
        for (String it : new String[] {"iron_pickaxe", "iron_axe", "iron_shovel", "iron_sword", "iron_hoe",
                "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_horse_armor",
                "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"}) {
            smelt(r, "iron_nugget", it);
        }
        for (String it : new String[] {"golden_pickaxe", "golden_axe", "golden_shovel", "golden_sword", "golden_hoe",
                "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "golden_horse_armor"}) {
            smelt(r, "gold_nugget", it);
        }
        craft(r, "bucket", 1, "iron_ingot", 3);
        craft(r, "shears", 1, "iron_ingot", 2);
        craft(r, "flint_and_steel", 1, Map.of("iron_ingot", 1, "flint", 1));
        craft(r, "compass", 1, Map.of("iron_ingot", 4, "redstone", 1));
        craft(r, "clock", 1, Map.of("gold_ingot", 4, "redstone", 1));
        craft(r, "cauldron", 1, "iron_ingot", 7);
        craft(r, "shield", 1, Map.of("iron_ingot", 1, "oak_planks", 6));
        craft(r, "tripwire_hook", 2, Map.of("iron_ingot", 1, "stick", 1, "oak_planks", 1));
        craft(r, "crossbow", 1, Map.of("stick", 3, "string", 2, "iron_ingot", 1, "tripwire_hook", 1));
        craft(r, "bow", 1, Map.of("stick", 3, "string", 3));
        craft(r, "arrow", 4, Map.of("flint", 1, "stick", 1, "feather", 1));
        craft(r, "fishing_rod", 1, Map.of("stick", 3, "string", 2));
        craft(r, "spyglass", 1, Map.of("copper_ingot", 2, "amethyst_shard", 1));
        craft(r, "iron_chain", 1, Map.of("iron_ingot", 1, "iron_nugget", 2));
        craft(r, "lantern", 1, Map.of("iron_nugget", 8, "torch", 1));
        craft(r, "anvil", 1, Map.of("iron_block", 3, "iron_ingot", 4));
        craft(r, "saddle", 1, Map.of("leather", 3, "iron_ingot", 1));
        craft(r, "enchanting_table", 1, Map.of("diamond", 2, "obsidian", 4, "book", 1));
        craft(r, "ender_chest", 1, Map.of("obsidian", 8, "ender_eye", 1));
        craft(r, "lead", 2, Map.of("string", 4, "slime_ball", 1));
        craft(r, "bundle", 1, Map.of("string", 1, "leather", 1));
        // ---- Redstone
        craft(r, "redstone_torch", 1, Map.of("redstone", 1, "stick", 1));
        craft(r, "repeater", 1, Map.of("stone", 3, "redstone_torch", 2, "redstone", 1));
        craft(r, "comparator", 1, Map.of("stone", 3, "redstone_torch", 3, "quartz", 1));
        craft(r, "observer", 1, Map.of("cobblestone", 6, "redstone", 2, "quartz", 1));
        craft(r, "piston", 1, Map.of("oak_planks", 3, "cobblestone", 4, "iron_ingot", 1, "redstone", 1));
        craft(r, "sticky_piston", 1, Map.of("piston", 1, "slime_ball", 1));
        craft(r, "dropper", 1, Map.of("cobblestone", 7, "redstone", 1));
        craft(r, "dispenser", 1, Map.of("cobblestone", 7, "bow", 1, "redstone", 1));
        craft(r, "dispenser", 1, Map.of("dropper", 1, "stick", 3, "string", 3));
        craft(r, "hopper", 1, Map.of("iron_ingot", 5, "chest", 1));
        craft(r, "minecart", 1, "iron_ingot", 5);
        craft(r, "rail", 16, Map.of("iron_ingot", 6, "stick", 1));
        craft(r, "powered_rail", 6, Map.of("gold_ingot", 6, "stick", 1, "redstone", 1));
        craft(r, "trapped_chest", 1, Map.of("chest", 1, "tripwire_hook", 1));
        craft(r, "redstone_lamp", 1, Map.of("redstone", 4, "glowstone", 1));
        craft(r, "target", 1, Map.of("redstone", 4, "hay_block", 1));
        craft(r, "tnt", 1, Map.of("gunpowder", 5, "sand", 4));
        // ---- Essen und Ernte
        craft(r, "bread", 1, "wheat", 3);
        storage(r, "wheat", "hay_block");
        craft(r, "melon", 1, "melon_slice", 9);
        mine(r, "melon_slice", 9, "melon", 1); // mit Glück bis zu 9 Scheiben
        craft(r, "melon_seeds", 1, "melon_slice", 1);
        craft(r, "pumpkin_seeds", 4, "pumpkin", 1);
        craft(r, "pumpkin_pie", 1, Map.of("pumpkin", 1, "sugar", 1, "egg", 1));
        craft(r, "cookie", 8, Map.of("wheat", 2, "cocoa_beans", 1));
        craft(r, "sugar", 1, "sugar_cane", 1);
        craft(r, "sugar", 1, "honey_bottle", 1);
        smelt(r, "baked_potato", "potato");
        smelt(r, "dried_kelp", "kelp");
        for (String meat : new String[] {"beef", "porkchop", "chicken", "mutton", "salmon", "cod"}) {
            smelt(r, "cooked_" + meat, meat);
        }
        craft(r, "golden_apple", 1, Map.of("gold_ingot", 8, "apple", 1));
        craft(r, "golden_carrot", 1, Map.of("gold_nugget", 8, "carrot", 1));
        craft(r, "glistering_melon_slice", 1, Map.of("gold_nugget", 8, "melon_slice", 1));
        mine(r, "apple", 1, "oak_leaves", 1);
        mine(r, "carved_pumpkin", 1, "pumpkin", 1);
        craft(r, "jack_o_lantern", 1, Map.of("carved_pumpkin", 1, "torch", 1));
        // ---- Beute, Tränke, Farben
        craft(r, "bone_meal", 3, "bone", 1);
        craft(r, "white_dye", 1, "bone_meal", 1);
        craft(r, "white_dye", 1, "lily_of_the_valley", 1);
        craft(r, "black_dye", 1, "ink_sac", 1);
        craft(r, "red_dye", 1, "poppy", 1);
        craft(r, "yellow_dye", 1, "dandelion", 1);
        craft(r, "yellow_dye", 2, "sunflower", 1);
        craft(r, "blue_dye", 1, "cornflower", 1);
        craft(r, "lime_dye", 2, Map.of("green_dye", 1, "white_dye", 1));
        craft(r, "white_wool", 1, "string", 4);
        mine(r, "string", 4, "white_wool", 1); // kein Vanilla-Rezept – sicher ist sicher
        craft(r, "blaze_powder", 2, "blaze_rod", 1);
        craft(r, "magma_cream", 1, Map.of("blaze_powder", 1, "slime_ball", 1));
        craft(r, "fermented_spider_eye", 1, Map.of("spider_eye", 1, "brown_mushroom", 1, "sugar", 1));
        craft(r, "ender_eye", 1, Map.of("ender_pearl", 1, "blaze_powder", 1));
        craft(r, "brewing_stand", 1, Map.of("blaze_rod", 1, "cobblestone", 3));
        craft(r, "firework_rocket", 3, Map.of("paper", 1, "gunpowder", 1));
        craft(r, "end_rod", 4, Map.of("blaze_rod", 1, "popped_chorus_fruit", 1));
        craft(r, "slime_ball", 9, "slime_block", 1);
        return List.copyOf(r);
    }

    private static void craft(List<Recipe> r, String out, int count, String in, int n) {
        r.add(new Recipe(out, count, Map.of(in, n), "werkbank"));
    }

    private static void craft(List<Recipe> r, String out, int count, Map<String, Integer> in) {
        r.add(new Recipe(out, count, in, "werkbank"));
    }

    private static void smelt(List<Recipe> r, String out, String in) {
        r.add(new Recipe(out, 1, Map.of(in, 1), "ofen"));
    }

    private static void mine(List<Recipe> r, String out, int count, String in, int n) {
        r.add(new Recipe(out, count, Map.of(in, n), "abbauen"));
    }

    /** Neun Stück ↔ ein Block (in beide Richtungen). */
    private static void storage(List<Recipe> r, String item, String block) {
        craft(r, block, 1, item, 9);
        craft(r, item, 9, block, 1);
    }
}
