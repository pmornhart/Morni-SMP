package de.moinmorni.smp.town;

import java.util.List;

/**
 * Häuser im Alpendorf-Stil: Steinsockel, weißer Putz, Holzbalken, oben Holz mit Balkon und Blumentöpfen,
 * Fensterläden, Blumenkästen, Satteldach mit Giebel nach vorne und Kamin mit Rauch. Öffentliche Gebäude (Hallen)
 * haben einen offenen Torbogen statt der Haustür und goldene/smaragdgrüne Zierde.
 */
final class Houses {

    private Houses() {
    }

    /** Farben und Stoffe eines Hauses. */
    record Style(String base, String plaster, String post, String beam, String upper, String roof, String ridge,
                 String gable, String shutter, String door, List<String> flowers) {
    }

    static final List<Style> STYLES = List.of(
            new Style("cobblestone", "calcite", "stripped_dark_oak_log", "dark_oak_log", "spruce_planks",
                    "dark_oak_stairs", "dark_oak_slab", "spruce_planks", "waxed_oxidized_copper_trapdoor",
                    "spruce_door", List.of("potted_red_tulip", "potted_poppy", "potted_oxeye_daisy")),
            new Style("stone_bricks", "white_terracotta", "stripped_spruce_log", "spruce_log", "calcite",
                    "spruce_stairs", "spruce_slab", "spruce_planks", "crimson_trapdoor", "dark_oak_door",
                    List.of("potted_cornflower", "potted_azure_bluet", "potted_allium")),
            new Style("mossy_cobblestone", "calcite", "stripped_oak_log", "oak_log", "dark_oak_planks",
                    "deepslate_tile_stairs", "deepslate_tile_slab", "dark_oak_planks", "spruce_trapdoor",
                    "spruce_door", List.of("potted_pink_tulip", "potted_red_tulip", "potted_blue_orchid")),
            new Style("stone_bricks", "calcite", "stripped_spruce_log", "spruce_log", "spruce_planks",
                    "mangrove_stairs", "mangrove_slab", "spruce_planks", "warped_trapdoor", "spruce_door",
                    List.of("potted_orange_tulip", "potted_dandelion", "potted_white_tulip")));

    /** Für öffentliche Gebäude: Stein unten, Fachwerk oben, Schieferdach. */
    static final Style HALL = new Style("polished_andesite", "calcite", "stripped_dark_oak_log", "dark_oak_log",
            "calcite", "deepslate_tile_stairs", "deepslate_tile_slab", "dark_oak_planks",
            "waxed_oxidized_copper_trapdoor", "dark_oak_door", List.of("potted_red_tulip", "potted_cornflower"));

    /**
     * Ein Haus mit Grundfläche {@code w} × {@code d} (u = 0…w-1, v = 0…d-1, v = 0 vorne). Für Wohnhäuser w = 7,
     * d = 7; Hallen sind größer. Liefert die Höhe des Firsts.
     */
    static int build(Frame f, int w, int d, Style s, boolean hall, int seed) {
        int mid = w / 2;
        // Boden und Grundmauern
        f.fill(1, 0, 1, w - 2, 0, d - 2, hall ? "polished_andesite" : "spruce_planks");
        f.fill(0, 0, 0, w - 1, 0, d - 1, s.base());
        f.fill(1, 0, 1, w - 2, 0, d - 2, hall ? "polished_andesite" : "spruce_planks");
        // Erdgeschoss
        for (int y = 1; y <= 3; y++) {
            String wall = y == 1 ? s.base() : s.plaster();
            ring(f, w, d, y, wall);
        }
        // Balkenlage und Obergeschoss
        f.fill(0, 4, 0, w - 1, 4, 0, s.beam() + "[axis=" + f.axisU() + "]");
        f.fill(0, 4, d - 1, w - 1, 4, d - 1, s.beam() + "[axis=" + f.axisU() + "]");
        f.fill(0, 4, 1, 0, 4, d - 2, s.beam() + "[axis=" + f.axisV() + "]");
        f.fill(w - 1, 4, 1, w - 1, 4, d - 2, s.beam() + "[axis=" + f.axisV() + "]");
        f.fill(1, 4, 1, w - 2, 4, d - 2, "spruce_planks");
        for (int y = 5; y <= 6; y++) {
            ring(f, w, d, y, s.upper());
        }
        // Eckpfosten
        for (int[] c : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            f.fill(c[0], 1, c[1], c[0], 6, c[1], s.post() + "[axis=y]");
        }
        // Fachwerk in Hallen: senkrechte Balken im Obergeschoss
        if (hall) {
            for (int u = 2; u < w - 2; u += 3) {
                f.fill(u, 5, 0, u, 6, 0, s.post() + "[axis=y]");
                f.fill(u, 5, d - 1, u, 6, d - 1, s.post() + "[axis=y]");
            }
        }
        // Innenraum leer (falls vorher etwas stand)
        f.fill(1, 1, 1, w - 2, 3, d - 2, "air");
        f.fill(1, 5, 1, w - 2, 6, d - 2, "air");

        // Eingang
        if (hall) {
            f.fill(mid - 1, 1, 0, mid + 1, 3, 0, "air");
            f.set(mid - 1, 3, 0, s.roof() + "[facing=" + f.l() + ",half=top]");
            f.set(mid + 1, 3, 0, s.roof() + "[facing=" + f.r() + ",half=top]");
            f.fill(mid - 2, 1, 0, mid - 2, 3, 0, "chiseled_stone_bricks");
            f.fill(mid + 2, 1, 0, mid + 2, 3, 0, "chiseled_stone_bricks");
            f.set(mid, 4, 0, "gold_block");
            f.set(mid - 1, 4, 0, "emerald_block");
            f.set(mid + 1, 4, 0, "emerald_block");
        } else {
            f.set(mid, 1, 0, s.door() + "[facing=" + f.f() + ",half=lower,hinge=left,open=false]");
            f.set(mid, 2, 0, s.door() + "[facing=" + f.f() + ",half=upper,hinge=left,open=false]");
            f.set(mid, 0, -1, "stone_bricks");
        }

        // Fenster vorne (Erdgeschoss mit Blumenkasten und Läden, oben mit Balkon)
        for (int u = 1; u <= w - 2; u += 2) {
            if (Math.abs(u - mid) <= (hall ? 2 : 1)) {
                continue;
            }
            window(f, u, 2, 0, s, true, seed + u);
            f.set(u, 5, 0, "glass_pane");
            f.set(u, 6, 0, "glass_pane");
        }
        if (!hall) {
            f.set(mid, 5, 0, "glass_pane");
            f.set(mid, 6, 0, "glass_pane");
        }
        // Seitenfenster
        for (int v = 2; v <= d - 3; v += Math.max(2, d - 5)) {
            sideWindow(f, 0, v, true, s);
            sideWindow(f, w - 1, v, false, s);
        }
        // Rückseite
        for (int u = 2; u <= w - 3; u += Math.max(2, w - 5)) {
            f.set(u, 2, d - 1, "glass_pane");
            f.set(u, 5, d - 1, "glass_pane");
            f.set(u, 6, d - 1, "glass_pane");
        }

        // Balkon vorne (nur Wohnhäuser)
        if (!hall) {
            f.fill(1, 4, -1, w - 2, 4, -1, "spruce_slab[type=top]");
            f.fill(1, 5, -1, w - 2, 5, -1, "spruce_fence");
            for (int u = 1; u <= w - 2; u += 2) {
                f.set(u, 6, -1, s.flowers().get(Plan.noise(u, seed, 7, s.flowers().size())));
            }
        }

        // Dach: Giebel nach vorne, First in der Mitte, Überstand 1
        int top = 7;
        for (int k = 0; ; k++) {
            int ul = -1 + k;
            int ur = w - k;
            int y = 7 + k;
            if (ul > ur) {
                break;
            }
            if (ul == ur) {
                f.fill(ul, y, -1, ul, y, d, s.ridge() + "[type=bottom]");
                top = y;
                break;
            }
            f.fill(ul, y, -1, ul, y, d, s.roof() + "[facing=" + f.r() + ",half=bottom]");
            f.fill(ur, y, -1, ur, y, d, s.roof() + "[facing=" + f.l() + ",half=bottom]");
            if (ur - ul > 1) {
                f.fill(ul + 1, y, 0, ur - 1, y, d - 1, s.gable());
            }
            top = y;
        }
        // Giebelfenster
        if (w >= 7) {
            f.set(mid, 8, 0, "glass_pane");
        }
        // Laternen unter dem Dachüberstand
        f.set(-1, 6, -1, "lantern[hanging=true]");
        f.set(w, 6, -1, "lantern[hanging=true]");
        // Kamin mit Rauch
        int cu = w - 2;
        int cv = d - 2;
        int roofAt = 7 + (w - cu); // Höhe der Dachfläche über dieser Spalte (rechte Dachseite)
        for (int y = 7; y <= roofAt + 1; y++) {
            f.set(cu, y, cv, "bricks");
        }
        f.set(cu, roofAt + 2, cv, "campfire[lit=true,signal_fire=false,facing=" + f.f() + "]");

        // Einrichtung
        if (!hall) {
            furnish(f, w, d, s);
        }
        return top;
    }

    private static void ring(Frame f, int w, int d, int y, String block) {
        f.fill(0, y, 0, w - 1, y, 0, block);
        f.fill(0, y, d - 1, w - 1, y, d - 1, block);
        f.fill(0, y, 1, 0, y, d - 2, block);
        f.fill(w - 1, y, 1, w - 1, y, d - 2, block);
    }

    /** Fenster in der Vorderwand bei u, mit Läden links/rechts und Blumenkasten darunter. */
    private static void window(Frame f, int u, int y, int v, Style s, boolean box, int seed) {
        f.set(u, y, v, "glass_pane");
        f.set(u, y + 1, v, "glass_pane");
        f.set(u - 1, y, v - 1, s.shutter() + "[facing=" + f.f() + ",half=bottom,open=true]");
        f.set(u - 1, y + 1, v - 1, s.shutter() + "[facing=" + f.f() + ",half=top,open=true]");
        f.set(u + 1, y, v - 1, s.shutter() + "[facing=" + f.f() + ",half=bottom,open=true]");
        f.set(u + 1, y + 1, v - 1, s.shutter() + "[facing=" + f.f() + ",half=top,open=true]");
        if (box) {
            f.set(u, y - 1, v - 1, "spruce_trapdoor[facing=" + f.f() + ",half=top,open=false]");
            f.set(u, y, v - 1, s.flowers().get(Plan.noise(u, seed, 3, s.flowers().size())));
        }
    }

    /** Fenster in einer Seitenwand (links: u = 0, rechts: u = w-1) mit Läden davor. */
    private static void sideWindow(Frame f, int u, int v, boolean left, Style s) {
        String out = left ? f.l() : f.r();
        int o = left ? u - 1 : u + 1;
        f.set(u, 2, v, "glass_pane");
        f.set(u, 3, v, "glass_pane");
        f.set(u, 5, v, "glass_pane");
        f.set(u, 6, v, "glass_pane");
        for (int dv : new int[] {-1, 1}) {
            f.set(o, 2, v + dv, s.shutter() + "[facing=" + out + ",half=bottom,open=true]");
            f.set(o, 3, v + dv, s.shutter() + "[facing=" + out + ",half=top,open=true]");
        }
    }

    /** Stube: Tisch mit Stühlen, Lampe, Werkbank, Ofen, Regal, Teppich. */
    private static void furnish(Frame f, int w, int d, Style s) {
        int mu = w / 2;
        int mv = d / 2;
        f.set(mu, 1, mv, "spruce_fence");
        f.set(mu, 2, mv, "spruce_pressure_plate");
        f.set(mu - 1, 1, mv, "spruce_stairs[facing=" + f.l() + ",half=bottom]");
        f.set(mu + 1, 1, mv, "spruce_stairs[facing=" + f.r() + ",half=bottom]");
        f.set(mu, 3, mv, "lantern[hanging=true]");
        f.set(1, 1, d - 2, "crafting_table");
        f.set(2, 1, d - 2, "furnace[facing=" + f.f() + "]");
        f.set(w - 2, 1, d - 2, "bookshelf");
        f.set(w - 2, 2, d - 2, "bookshelf");
        f.set(1, 1, 1, "barrel[facing=up]");
        f.set(w - 2, 1, 1, "potted_fern");
        f.set(mu, 1, 1, "red_carpet");
    }
}
