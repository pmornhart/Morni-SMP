package net.morni.smp.market;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suche im Markt: Der Server kennt nur die englischen Sorten („diamond_sword“). Damit „Diamant“ oder „Schwert“
 * trotzdem etwas findet, übersetzt diese kleine, selbst geschriebene Wortliste häufige deutsche Wörter in Teile der
 * Sorten-Namen. Jedes Suchwort muss passen (UND), für jedes Wort genügt eine seiner Übersetzungen (ODER).
 */
public final class SearchWords {

    private SearchWords() {
    }

    private static final Map<String, List<String>> DE = Map.<String, List<String>>ofEntries(
            Map.entry("diamant", List.of("diamond")),
            Map.entry("smaragd", List.of("emerald")),
            Map.entry("eisen", List.of("iron")),
            Map.entry("gold", List.of("gold")),
            Map.entry("endertruhe", List.of("ender_chest")),
            Map.entry("enderauge", List.of("ender_eye")),
            Map.entry("golden", List.of("gold")),
            Map.entry("goldene", List.of("gold")),
            Map.entry("goldener", List.of("gold")),
            Map.entry("goldenen", List.of("gold")),
            Map.entry("kupfer", List.of("copper")),
            Map.entry("netherit", List.of("netherite")),
            Map.entry("kohle", List.of("coal")),
            Map.entry("quarz", List.of("quartz")),
            Map.entry("lapis", List.of("lapis")),
            Map.entry("amethyst", List.of("amethyst")),
            Map.entry("erz", List.of("_ore", "raw_")),
            Map.entry("barren", List.of("ingot")),
            Map.entry("holz", List.of("_log", "_planks", "_wood")),
            Map.entry("stamm", List.of("_log")),
            Map.entry("bretter", List.of("_planks")),
            Map.entry("eiche", List.of("oak")),
            Map.entry("fichte", List.of("spruce")),
            Map.entry("birke", List.of("birch")),
            Map.entry("tropenbaum", List.of("jungle")),
            Map.entry("akazie", List.of("acacia")),
            Map.entry("schwarzeiche", List.of("dark_oak")),
            Map.entry("kirsche", List.of("cherry")),
            Map.entry("mangrove", List.of("mangrove")),
            Map.entry("stein", List.of("stone")),
            Map.entry("bruchstein", List.of("cobblestone")),
            Map.entry("ziegel", List.of("brick")),
            Map.entry("erde", List.of("dirt")),
            Map.entry("gras", List.of("grass")),
            Map.entry("sand", List.of("sand")),
            Map.entry("kies", List.of("gravel")),
            Map.entry("lehm", List.of("clay")),
            Map.entry("ton", List.of("clay")),
            Map.entry("glas", List.of("glass")),
            Map.entry("scheibe", List.of("pane")),
            Map.entry("beton", List.of("concrete")),
            Map.entry("terrakotta", List.of("terracotta")),
            Map.entry("wolle", List.of("wool")),
            Map.entry("teppich", List.of("carpet")),
            Map.entry("eis", List.of("ice")),
            Map.entry("schnee", List.of("snow")),
            Map.entry("obsidian", List.of("obsidian")),
            Map.entry("endstein", List.of("end_stone")),
            Map.entry("seelensand", List.of("soul_sand")),
            Map.entry("tiefenschiefer", List.of("deepslate")),
            Map.entry("andesit", List.of("andesite")),
            Map.entry("diorit", List.of("diorite")),
            Map.entry("granit", List.of("granite")),
            Map.entry("tuff", List.of("tuff")),
            Map.entry("kalzit", List.of("calcite")),
            Map.entry("moos", List.of("moss")),
            Map.entry("blume", List.of("flower", "tulip", "poppy", "dandelion", "orchid", "allium", "daisy",
                    "cornflower", "lily")),
            Map.entry("setzling", List.of("sapling")),
            Map.entry("samen", List.of("seeds")),
            Map.entry("laub", List.of("leaves")),
            Map.entry("schwert", List.of("sword")),
            Map.entry("spitzhacke", List.of("pickaxe")),
            Map.entry("axt", List.of("_axe")),
            Map.entry("schaufel", List.of("shovel")),
            Map.entry("hacke", List.of("_hoe")),
            Map.entry("helm", List.of("helmet")),
            Map.entry("brustpanzer", List.of("chestplate")),
            Map.entry("hose", List.of("leggings")),
            Map.entry("stiefel", List.of("boots")),
            Map.entry("ruestung", List.of("helmet", "chestplate", "leggings", "boots")),
            Map.entry("rüstung", List.of("helmet", "chestplate", "leggings", "boots")),
            Map.entry("bogen", List.of("bow")),
            Map.entry("armbrust", List.of("crossbow")),
            Map.entry("pfeil", List.of("arrow")),
            Map.entry("dreizack", List.of("trident")),
            Map.entry("schild", List.of("shield", "_sign")),
            Map.entry("angel", List.of("fishing_rod")),
            Map.entry("schere", List.of("shears")),
            Map.entry("totem", List.of("totem")),
            Map.entry("buch", List.of("book")),
            Map.entry("zauber", List.of("enchanted")),
            Map.entry("verzaubert", List.of("enchanted")),
            Map.entry("trank", List.of("potion")),
            Map.entry("apfel", List.of("apple")),
            Map.entry("goldapfel", List.of("golden_apple")),
            Map.entry("brot", List.of("bread")),
            Map.entry("fleisch", List.of("beef", "porkchop", "mutton", "chicken", "rabbit")),
            Map.entry("steak", List.of("cooked_beef")),
            Map.entry("fisch", List.of("cod", "salmon", "fish")),
            Map.entry("weizen", List.of("wheat")),
            Map.entry("karotte", List.of("carrot")),
            Map.entry("kartoffel", List.of("potato")),
            Map.entry("rübe", List.of("beetroot")),
            Map.entry("ruebe", List.of("beetroot")),
            Map.entry("kürbis", List.of("pumpkin")),
            Map.entry("kuerbis", List.of("pumpkin")),
            Map.entry("melone", List.of("melon")),
            Map.entry("zuckerrohr", List.of("sugar_cane")),
            Map.entry("zucker", List.of("sugar")),
            Map.entry("kaktus", List.of("cactus")),
            Map.entry("bambus", List.of("bamboo")),
            Map.entry("seetang", List.of("kelp")),
            Map.entry("knochen", List.of("bone")),
            Map.entry("faden", List.of("string")),
            Map.entry("schießpulver", List.of("gunpowder")),
            Map.entry("schiesspulver", List.of("gunpowder")),
            Map.entry("enderperle", List.of("ender_pearl")),
            Map.entry("perle", List.of("pearl")),
            Map.entry("lohenrute", List.of("blaze_rod")),
            Map.entry("schleim", List.of("slime")),
            Map.entry("feder", List.of("feather")),
            Map.entry("leder", List.of("leather")),
            Map.entry("ei", List.of("egg")),
            Map.entry("honig", List.of("honey")),
            Map.entry("wabe", List.of("honeycomb")),
            Map.entry("fackel", List.of("torch")),
            Map.entry("laterne", List.of("lantern")),
            Map.entry("kerze", List.of("candle")),
            Map.entry("truhe", List.of("chest")),
            Map.entry("fass", List.of("barrel")),
            Map.entry("ofen", List.of("furnace", "smoker")),
            Map.entry("amboss", List.of("anvil")),
            Map.entry("werkbank", List.of("crafting_table")),
            Map.entry("bett", List.of("_bed")),
            Map.entry("eimer", List.of("bucket")),
            Map.entry("wasser", List.of("water")),
            Map.entry("milch", List.of("milk")),
            Map.entry("trichter", List.of("hopper")),
            Map.entry("lore", List.of("minecart")),
            Map.entry("schiene", List.of("rail")),
            Map.entry("kolben", List.of("piston")),
            Map.entry("beobachter", List.of("observer")),
            Map.entry("spender", List.of("dispenser")),
            Map.entry("werfer", List.of("dropper")),
            Map.entry("hebel", List.of("lever")),
            Map.entry("knopf", List.of("button")),
            Map.entry("druckplatte", List.of("pressure_plate")),
            Map.entry("tür", List.of("_door")),
            Map.entry("tuer", List.of("_door")),
            Map.entry("falltür", List.of("trapdoor")),
            Map.entry("falltuer", List.of("trapdoor")),
            Map.entry("zaun", List.of("fence")),
            Map.entry("treppe", List.of("stairs")),
            Map.entry("stufe", List.of("slab")),
            Map.entry("mauer", List.of("_wall")),
            Map.entry("rakete", List.of("firework_rocket")),
            Map.entry("sattel", List.of("saddle")),
            Map.entry("namensschild", List.of("name_tag")),
            Map.entry("leine", List.of("lead")),
            Map.entry("schulkerkiste", List.of("shulker_box")),
            Map.entry("shulker", List.of("shulker")),
            Map.entry("kompass", List.of("compass")),
            Map.entry("uhr", List.of("clock")),
            Map.entry("karte", List.of("map")),
            Map.entry("farbstoff", List.of("dye")),
            Map.entry("farbe", List.of("dye")),
            Map.entry("weiß", List.of("white")),
            Map.entry("weiss", List.of("white")),
            Map.entry("schwarz", List.of("black")),
            Map.entry("rot", List.of("red")),
            Map.entry("blau", List.of("blue")),
            Map.entry("hellblau", List.of("light_blue")),
            Map.entry("grün", List.of("green", "lime")),
            Map.entry("gruen", List.of("green", "lime")),
            Map.entry("gelb", List.of("yellow")),
            Map.entry("orange", List.of("orange")),
            Map.entry("lila", List.of("purple")),
            Map.entry("rosa", List.of("pink")),
            Map.entry("braun", List.of("brown")),
            Map.entry("grau", List.of("gray")),
            Map.entry("leuchtstein", List.of("glowstone")),
            Map.entry("seelaterne", List.of("sea_lantern")),
            Map.entry("prismarin", List.of("prismarine")),
            Map.entry("spawner", List.of("spawner")),
            Map.entry("kopf", List.of("head", "skull")),
            Map.entry("schädel", List.of("skull")),
            Map.entry("schaedel", List.of("skull")),
            Map.entry("erfahrung", List.of("experience")),
            Map.entry("netherwarze", List.of("nether_wart")),
            Map.entry("drachenei", List.of("dragon_egg")),
            Map.entry("elytra", List.of("elytra")),
            Map.entry("flügel", List.of("elytra")),
            Map.entry("fluegel", List.of("elytra")));

    /**
     * Suchwörter einer Eingabe: je Wort die Liste seiner Varianten (das Wort selbst plus Übersetzungen), alles klein.
     * Leere Eingabe → leere Liste (alles passt).
     */
    public static List<Set<String>> terms(String query) {
        List<Set<String>> out = new ArrayList<>();
        if (query == null) {
            return out;
        }
        for (String word : query.toLowerCase(Locale.ROOT).strip().split("[\\s,]+")) {
            if (word.isEmpty()) {
                continue;
            }
            Set<String> variants = new LinkedHashSet<>();
            variants.add(word.replace(' ', '_'));
            List<String> de = DE.get(word);
            if (de != null) {
                variants.addAll(de);
            }
            out.add(variants);
        }
        return out;
    }

    /** Passt der Suchtext (Sorte + Name) zu allen Wörtern? (gleiche Regel wie die SQL-Abfrage) */
    public static boolean matches(String haystack, List<Set<String>> terms) {
        String h = haystack.toLowerCase(Locale.ROOT);
        for (Set<String> variants : terms) {
            boolean any = false;
            for (String v : variants) {
                if (h.contains(v)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /** SQL-Bedingung für eine Spalte (bzw. einen Ausdruck) und die Werte für die Platzhalter. */
    public static String sql(String column, List<Set<String>> terms, List<Object> args) {
        if (terms.isEmpty()) {
            return "1=1";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sb.append(" AND ");
            }
            sb.append('(');
            int j = 0;
            for (String v : terms.get(i)) {
                if (j++ > 0) {
                    sb.append(" OR ");
                }
                sb.append("instr(lower(").append(column).append("), ?) > 0");
                args.add(v);
            }
            sb.append(')');
        }
        return sb.append(')').toString();
    }
}
