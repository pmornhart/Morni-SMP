package net.morni.smp.market;

import java.util.Locale;
import java.util.Set;

/** Abteilungen des Auktionshauses. Die Einordnung braucht nur die Sorte (und zwei Hinweise vom Server). */
public enum Category {
    BLOCKS("bloecke"),
    TOOLS("werkzeug"),
    ARMOR("ruestung"),
    FOOD("essen"),
    REDSTONE("redstone"),
    MAGIC("traenke"),
    OTHER("sonstiges");

    private final String id;

    Category(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Category byId(String id) {
        for (Category c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return OTHER;
    }

    private static final Set<String> TOOL_SUFFIX = Set.of("_sword", "_axe", "_pickaxe", "_shovel", "_hoe", "_spear");
    private static final Set<String> TOOLS_EXACT = Set.of("bow", "crossbow", "trident", "shears", "fishing_rod",
            "flint_and_steel", "mace", "shield", "brush", "spyglass", "carrot_on_a_stick", "warped_fungus_on_a_stick",
            "compass", "recovery_compass", "clock", "lead", "arrow", "spectral_arrow", "wind_charge", "fire_charge");
    private static final Set<String> ARMOR_SUFFIX = Set.of("_helmet", "_chestplate", "_leggings", "_boots",
            "_horse_armor", "_nautilus_armor");
    private static final Set<String> ARMOR_EXACT = Set.of("elytra", "turtle_helmet", "wolf_armor", "saddle",
            "totem_of_undying", "carved_pumpkin");
    private static final Set<String> REDSTONE_EXACT = Set.of("redstone", "redstone_block", "redstone_torch",
            "repeater", "comparator", "piston", "sticky_piston", "observer", "hopper", "dispenser", "dropper", "lever",
            "tripwire_hook", "daylight_detector", "target", "redstone_lamp", "note_block", "rail", "powered_rail",
            "detector_rail", "activator_rail", "minecart", "chest_minecart", "hopper_minecart", "tnt_minecart",
            "furnace_minecart", "tnt", "slime_block", "honey_block", "lectern", "crafter", "sculk_sensor",
            "calibrated_sculk_sensor", "trapped_chest", "lightning_rod", "jukebox", "bell");
    private static final Set<String> REDSTONE_SUFFIX = Set.of("_button", "_pressure_plate", "_door", "_trapdoor",
            "_fence_gate", "_bulb");
    private static final Set<String> MAGIC_EXACT = Set.of("potion", "splash_potion", "lingering_potion",
            "enchanted_book", "experience_bottle", "tipped_arrow", "brewing_stand", "blaze_powder", "blaze_rod",
            "nether_wart", "ghast_tear", "magma_cream", "fermented_spider_eye", "glistering_melon_slice",
            "golden_carrot", "rabbit_foot", "phantom_membrane", "dragon_breath", "glass_bottle", "enchanting_table",
            "lapis_lazuli", "book", "bookshelf", "ominous_bottle", "breeze_rod");

    /**
     * Abteilung für eine Sorte.
     *
     * @param key    z. B. „minecraft:diamond_sword“ (alles nach „|“ wird ignoriert)
     * @param block  lässt sich als Block setzen
     * @param edible lässt sich essen
     */
    public static Category of(String key, boolean block, boolean edible) {
        String k = key.toLowerCase(Locale.ROOT);
        int bar = k.indexOf('|');
        if (bar >= 0) {
            k = k.substring(0, bar);
        }
        int colon = k.indexOf(':');
        if (colon >= 0) {
            k = k.substring(colon + 1);
        }
        if (MAGIC_EXACT.contains(k)) {
            return MAGIC;
        }
        if (k.equals("golden_apple") || k.equals("enchanted_golden_apple")) {
            return FOOD;
        }
        if (TOOLS_EXACT.contains(k) || endsWithAny(k, TOOL_SUFFIX)) {
            return TOOLS;
        }
        if (ARMOR_EXACT.contains(k) || endsWithAny(k, ARMOR_SUFFIX)) {
            return ARMOR;
        }
        if (REDSTONE_EXACT.contains(k) || endsWithAny(k, REDSTONE_SUFFIX)) {
            return REDSTONE;
        }
        if (edible) {
            return FOOD;
        }
        if (block) {
            return BLOCKS;
        }
        return OTHER;
    }

    private static boolean endsWithAny(String k, Set<String> suffixes) {
        for (String s : suffixes) {
            if (k.endsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
