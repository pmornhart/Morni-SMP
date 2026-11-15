package de.moinmorni.smp.town;

import de.moinmorni.smp.town.Frame.Dir;
import de.moinmorni.smp.town.Layout.Box;
import de.moinmorni.smp.town.Layout.StallSpot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Marktstadt – ganz aus Code, ohne fremde Bauten oder Vorlagen. Ein ummauertes Alpen-Städtchen auf einem
 * Plateau um 0/0:
 *
 * <pre>
 *   |c| ≤ 17   Marktplatz: Brunnen mit Bestenliste, 16 Marktstände (4 je Seite), Pflaster in weiß-blauen Rauten
 *   18…20      Ring um den Platz, von dort Gassen bis zur Mauer
 *   21…40      Häuserblöcke: Auktionshaus + Kontor mit Auftragsbrett (Nord), Ankauf + Abholfach (Ost),
 *              Wegweiser-Platz + Gasthaus (West), Bürgerhäuser (Süd und in den vier Ecken), Gärten mit Bäumen
 *   41…44      Wallgasse innen an der Mauer
 *   45…46      Stadtmauer mit Zinnen, runde Ecktürme; drei Tore sind Reisetore (West: Oberwelt, Nord: Nether,
 *              Ost: End), das Südtor ist ein Aussichtstor
 *   47…51      Wiese vor der Mauer mit Zaun am Rand; die Stadt schwebt als Insel in der eigenen Spawn-Welt
 * </pre>
 *
 * Koordinaten: x nach Osten, z nach Süden, {@code y = 0} ist die Bodenschicht.
 */
public final class TownPlan {

    /** Bau-Fassung: wird sie erhöht, baut sich die Stadt beim nächsten Start neu (Stände werden vorher geräumt). */
    public static final int VERSION = 5;

    public static final int AREA = 58;
    public static final int FLAT = 51;
    static final int WALL = 46;
    static final int SQUARE = 17;

    private final Plan plan = new Plan();
    private final List<StallSpot> stalls = new ArrayList<>();
    private final Map<Long, String> triggers = new HashMap<>();
    private final List<Layout.Gate> gates = new ArrayList<>();

    private TownPlan() {
    }

    /** Der fertige Plan mit allen Blöcken. */
    public record Result(Plan plan, Layout layout) {
    }

    public static Result create() {
        TownPlan t = new TownPlan();
        t.ground();
        t.streets();
        t.square();
        t.fountain();
        t.stalls();
        t.buildings();
        t.gardens();
        t.lanterns();
        t.walls();
        t.signposts();
        Connect.run(t.plan);
        Layout layout = new Layout(0.5, 1, 10.5, 180f, AREA, FLAT, List.copyOf(t.stalls), Map.copyOf(t.triggers),
                List.copyOf(t.gates), 0.5, 10.3, 0.5);
        return new Result(t.plan, layout);
    }

    // ---------------------------------------------------------------- Boden, Straßen, Platz

    private void ground() {
        for (int x = -FLAT; x <= FLAT; x++) {
            for (int z = -FLAT; z <= FLAT; z++) {
                plan.set(x, 0, z, "grass_block");
                if (cheb(x, z) == FLAT) {
                    plan.set(x, 1, z, "spruce_fence"); // Rand der Insel
                }
            }
        }
    }

    private static int cheb(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }

    /** Pflasterstein mit etwas Abwechslung. */
    private static String cobble(int x, int z) {
        return switch (Plan.noise(x, z, 11, 20)) {
            case 0, 1, 2, 3, 4, 5, 6, 7 -> "cobblestone";
            case 8, 9, 10, 11, 12 -> "stone_bricks";
            case 13, 14 -> "andesite";
            case 15, 16 -> "polished_andesite";
            case 17 -> "mossy_cobblestone";
            case 18 -> "cracked_stone_bricks";
            default -> "mossy_stone_bricks";
        };
    }

    private void pave(int x1, int z1, int x2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                plan.set(x, 0, z, cobble(x, z));
            }
        }
    }

    private void streets() {
        // Hauptstraßen durch die Tore bis zum Rand des Plateaus
        pave(-3, -FLAT, 3, FLAT);
        pave(-FLAT, -3, FLAT, 3);
        // Ring um den Platz
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (cheb(x, z) >= 18) {
                    plan.set(x, 0, z, cobble(x, z));
                }
            }
        }
        // Gassen vom Ring bis zur Wallgasse
        pave(18, -44, 20, 44);
        pave(-20, -44, -18, 44);
        pave(-44, 18, 44, 20);
        pave(-44, -20, 44, -18);
        // Wallgasse innen an der Mauer
        for (int x = -44; x <= 44; x++) {
            for (int z = -44; z <= 44; z++) {
                if (cheb(x, z) >= 41) {
                    plan.set(x, 0, z, cobble(x, z));
                }
            }
        }
        // Gehsteig zwischen Hauptstraße und Häusern
        pave(4, -40, 4, -21);
        pave(-4, -40, -4, -21);
        pave(4, 21, 4, 40);
        pave(-4, 21, -4, 40);
        pave(-40, 4, -21, 4);
        pave(-40, -4, -21, -4);
        pave(21, 4, 40, 4);
        pave(21, -4, 40, -4);
    }

    /** Marktplatz: weiß-blaue Rauten, dazwischen helle Linien, außen ein Band aus Steinziegeln. */
    private void square() {
        for (int x = -SQUARE; x <= SQUARE; x++) {
            for (int z = -SQUARE; z <= SQUARE; z++) {
                String b;
                if (cheb(x, z) == SQUARE) {
                    b = "stone_bricks";
                } else {
                    int u = x + z;
                    int v = x - z;
                    if (Math.floorMod(u, 6) == 0 || Math.floorMod(v, 6) == 0) {
                        b = "polished_andesite";
                    } else {
                        b = (Math.floorDiv(u, 6) + Math.floorDiv(v, 6)) % 2 == 0 ? "polished_diorite"
                                : "light_blue_terracotta";
                    }
                }
                plan.set(x, 0, z, b);
            }
        }
    }

    /** Brunnen in der Mitte: rundes Becken, Säule mit goldener Kugel, darüber die Bestenliste. */
    /**
     * Brunnen aus hellem Marmor (Quarz, Kalzit) mit Gold: unten ein ruhiges Becken, in der Mitte eine Säule mit einer
     * Schale. Wasser nur als stilles Wasser, ganz von höheren Rändern umschlossen; Sprudeln und Tropfen über den
     * Schalenrand sind Partikel ({@code GateDecor}). Laternen auf goldenen Sockeln, Blumentöpfe ringsum.
     */
    private void fountain() {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                int d2 = x * x + z * z;
                if (d2 <= 13) {
                    plan.set(x, 0, z, "smooth_quartz");
                    plan.set(x, 1, z, "water");
                } else if (d2 <= 20) {
                    plan.set(x, 0, z, "smooth_quartz");
                    plan.set(x, 1, z, "quartz_bricks");
                    plan.set(x, 2, z, "smooth_quartz_slab[type=bottom]");
                } else if (d2 <= 26) {
                    plan.set(x, 0, z, Math.abs(x) == 5 || Math.abs(z) == 5 ? "chiseled_quartz_block" : "calcite");
                }
            }
        }
        // Säule
        plan.set(0, 1, 0, "chiseled_quartz_block");
        for (int y = 2; y <= 4; y++) {
            plan.set(0, y, 0, "quartz_pillar[axis=y]");
        }
        // Schale: Boden, Rand mit Gold, Wasser
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int d2 = x * x + z * z;
                if (d2 > 5) {
                    continue;
                }
                plan.set(x, 5, z, d2 == 0 ? "gold_block" : "smooth_quartz");
                if (d2 >= 4) {
                    plan.set(x, 6, z, "smooth_quartz");
                    if (d2 == 5) {
                        plan.set(x, 7, z, "light_weighted_pressure_plate");
                    }
                } else if (d2 > 0) {
                    plan.set(x, 6, z, "water");
                }
            }
        }
        // Spitze: Säule mit Gold und Laterne; das Sprudeln und Tropfen sind Partikel (kein fließendes Wasser, das
        // über Ränder laufen könnte)
        plan.set(0, 6, 0, "quartz_pillar[axis=y]");
        plan.set(0, 7, 0, "gold_block");
        plan.set(0, 8, 0, "lantern[hanging=false]");
        // Laternen auf goldenen Sockeln am Beckenrand (diagonal)
        for (int[] d : new int[][] {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            plan.set(d[0], 2, d[1], "gold_block");
            plan.set(d[0], 3, d[1], "lantern[hanging=false]");
        }
        // Blumentöpfe auf dem Marmorrand rundum
        String[] pots = {"potted_red_tulip", "potted_blue_orchid", "potted_allium", "potted_cornflower"};
        int i = 0;
        for (int[] d : new int[][] {{4, 3}, {-4, 3}, {4, -3}, {-4, -3}, {3, 4}, {-3, 4}, {3, -4}, {-3, -4}}) {
            plan.set(d[0], 1, d[1], pots[i++ % pots.length]);
        }
        // Blumen rund um den Brunnen
        for (int[] d : new int[][] {{5, 2}, {5, -2}, {-5, 2}, {-5, -2}, {2, 5}, {-2, 5}, {2, -5}, {-2, -5}}) {
            plan.set(d[0], 0, d[1], "grass_block");
            plan.set(d[0], 1, d[1], Plan.noise(d[0], d[1], 3, 2) == 0 ? "red_tulip" : "cornflower");
        }
    }

    // ---------------------------------------------------------------- Marktstände

    /** Vier Stände je Seite: Abschnitte entlang der Seite (von–bis), außer der Straßeneinmündung. */
    private static final int[][] STALL_RANGES = {{-13, -10}, {-8, -5}, {5, 8}, {10, 13}};

    private void stalls() {
        int id = 1;
        Dir[] fronts = {Dir.SOUTH, Dir.WEST, Dir.NORTH, Dir.EAST}; // Nordseite zeigt nach Süden usw.
        for (Dir front : fronts) {
            for (int[] r : STALL_RANGES) {
                stall(id++, front, r[0], r[1]);
            }
        }
    }

    private void stall(int id, Dir front, int from, int to) {
        // Vorderkante (Tresen) 14 Blöcke von der Mitte, Rückseite 16
        Frame probe = new Frame(plan, 0, 0, front);
        int ox;
        int oz;
        if (front == Dir.SOUTH) {        // Nordseite
            ox = probe.right.dx > 0 ? from : to;
            oz = -14;
        } else if (front == Dir.NORTH) { // Südseite
            ox = probe.right.dx > 0 ? from : to;
            oz = 14;
        } else if (front == Dir.WEST) {  // Ostseite
            ox = 14;
            oz = probe.right.dz > 0 ? from : to;
        } else {                         // Westseite
            ox = -14;
            oz = probe.right.dz > 0 ? from : to;
        }
        Frame f = new Frame(plan, ox, oz, front);
        f.fill(0, 0, 0, 3, 0, 2, "spruce_planks");
        f.fill(0, 1, 0, 3, 3, 2, "air");
        for (int[] p : new int[][] {{0, 0}, {3, 0}, {0, 2}, {3, 2}}) {
            f.fill(p[0], 1, p[1], p[0], 3, p[1], "spruce_fence");
        }
        f.set(1, 1, 0, "spruce_slab[type=top]");
        f.set(2, 1, 0, "spruce_slab[type=top]");
        // Markise: weiß-blaue Rauten
        for (int u = 0; u <= 3; u++) {
            for (int v = -1; v <= 2; v++) {
                f.set(u, 4, v, Math.floorMod(u + v, 2) == 0 ? "white_wool" : "light_blue_wool");
            }
        }
        // Hängeschild mit Nummer (Text setzt das Plugin)
        int sx = f.x(1, -1);
        int sz = f.z(1, -1);
        plan.sign(sx, 3, sz, "spruce_hanging_sign[rotation=" + front.rotation() + ",attached=false]",
                List.of("<au>Stand " + id + "</au>", "<em>frei</em>", "/stand mieten", ""),
                List.of("<au>Stall " + id + "</au>", "<em>free</em>", "/stand mieten", ""));
        Box zone = Box.of(f.x(0, 0), 1, f.z(0, 0), f.x(3, 2), 3, f.z(3, 2));
        Box frontArea = Box.of(f.x(-1, -4), 0, f.z(-1, -4), f.x(4, 2), 4, f.z(4, 2));
        stalls.add(new StallSpot(id, zone, frontArea, sx, 3, sz));
        triggers.put(Plan.pack(sx, 3, sz), "stall:" + id);
    }

    // ---------------------------------------------------------------- Gebäude

    private void buildings() {
        // Norden: Auktionshaus (West) und Kontor mit Auftragsbrett (Ost), Front nach Süden
        Frame ah = new Frame(plan, -17, -21, Dir.SOUTH);
        Houses.build(ah, 13, 11, Houses.HALL, true, 1);
        auctionInterior(ah);
        Frame kontor = new Frame(plan, 5, -21, Dir.SOUTH);
        Houses.build(kontor, 13, 11, Houses.STYLES.get(1), true, 2);
        orderBoard(kontor);

        // Osten: Ankauf (Nord) und Abholfach (Süd), Front nach Westen
        buybackHall(new Frame(plan, 21, -17, Dir.WEST));
        Frame post = new Frame(plan, 21, 7, Dir.WEST);
        Houses.build(post, 9, 9, Houses.STYLES.get(3), true, 3);
        mailInterior(post);

        // Westen: Reisetor (Nord) und Gasthaus (Süd), Front nach Osten
        travelGate();
        Frame inn = new Frame(plan, -21, 16, Dir.EAST);
        Houses.build(inn, 11, 9, Houses.STYLES.get(0), false, 4);
        inn.sign(5, 3, -1, "spruce", List.of("", "<au>Gasthaus</au>", "<mu>zur Marktstadt</mu>", ""), List.of());

        // Süden: zwei Bürgerhäuser, Front nach Norden
        Houses.build(new Frame(plan, 15, 23, Dir.NORTH), 11, 9, Houses.STYLES.get(2), false, 5);
        Houses.build(new Frame(plan, -7, 23, Dir.NORTH), 11, 9, Houses.STYLES.get(3), false, 6);

        // Ecken: je vier Häuser auf 9×9-Grundstücken (Haus 7×7), Fronten zu den Gassen
        int seed = 10;
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        int px = sx > 0 ? 21 + i * 11 : -21 - i * 11 - 8; // Westkante des Grundstücks
                        int pz = sz > 0 ? 21 + j * 11 : -21 - j * 11 - 8; // Nordkante
                        cornerHouse(px, pz, sx, i, seed++);
                    }
                }
            }
        }
    }

    /** Haus auf einem 9×9-Grundstück (Westkante px, Nordkante pz); Tür zur nächsten Nord-Süd-Gasse. */
    private void cornerHouse(int px, int pz, int side, int col, int seed) {
        // innere Reihe schaut zur Gasse bei |x| = 18…20, äußere zur Wallgasse
        boolean faceInner = col == 0;
        Dir front = side > 0 ? (faceInner ? Dir.WEST : Dir.EAST) : (faceInner ? Dir.EAST : Dir.WEST);
        Houses.Style style = Houses.STYLES.get(seed % Houses.STYLES.size());
        Frame f;
        if (front == Dir.WEST) {
            f = new Frame(plan, px + 1, pz + 1, front); // Vorderwand im Westen, u läuft nach Süden
        } else {
            f = new Frame(plan, px + 7, pz + 7, front); // Vorderwand im Osten, u läuft nach Norden
        }
        Houses.build(f, 7, 7, style, false, seed);
        // Weg von der Tür zur Gasse, Blumen im Vorgarten
        f.set(3, 0, -1, "stone_bricks");
        f.set(-1, 1, 3, "rose_bush[half=lower]");
        f.set(-1, 2, 3, "rose_bush[half=upper]");
        f.set(7, 1, 5, Plan.noise(px, pz, 5, 2) == 0 ? "lilac[half=lower]" : "peony[half=lower]");
        f.set(7, 2, 5, Plan.noise(px, pz, 5, 2) == 0 ? "lilac[half=upper]" : "peony[half=upper]");
    }

    private void auctionInterior(Frame f) {
        // Teppich vom Eingang zum Pult, Tresen, Pult des Auktionators, Lüster
        f.fill(6, 1, 1, 6, 1, 6, "red_carpet");
        f.fill(3, 1, 7, 9, 1, 7, "polished_andesite");
        f.fill(3, 2, 7, 9, 2, 7, "spruce_slab[type=bottom]");
        f.set(6, 1, 7, "lectern[facing=" + f.f() + ",has_book=false]");
        f.set(6, 2, 7, "air");
        for (int u = 2; u <= 10; u += 2) {
            f.set(u, 1, 9, "barrel[facing=" + f.f() + "]");
            f.set(u, 2, 9, "chest[facing=" + f.f() + "]");
        }
        f.set(1, 1, 9, "emerald_block");
        f.set(11, 1, 9, "emerald_block");
        f.set(4, 3, 4, "lantern[hanging=true]");
        f.set(8, 3, 4, "lantern[hanging=true]");
        f.set(6, 3, 4, "lantern[hanging=true]");
        for (int u = 3; u <= 9; u++) {
            trigger(f, u, 1, 7, "ah");
            trigger(f, u, 2, 7, "ah");
        }
        f.sign(6, 5, -1, "dark_oak", List.of("<au>Auktionshaus</au>", "<mu>Auction House</mu>", "Rechtsklick drinnen",
                "<mu>/ah</mu>"), List.of());
        trigger(f, 6, 5, -1, "ah");
        f.sign(4, 1, 6, "spruce", List.of("<em>Kaufen &</em>", "<em>Verkaufen</em>", "Rechtsklick", "<mu>/ah</mu>"),
                List.of());
        f.sign(8, 1, 6, "spruce", List.of("<em>Buy &</em>", "<em>sell</em>", "right-click", "<mu>/ah</mu>"),
                List.of());
        trigger(f, 4, 1, 6, "ah");
        trigger(f, 8, 1, 6, "ah");
    }

    /** Auftragsbrett an der Vorderwand des Kontors (links vom Eingang). */
    private void orderBoard(Frame f) {
        f.fill(0, 1, -1, 4, 3, -1, "air");
        f.fill(1, 1, 0, 4, 3, 0, "spruce_planks");
        f.fill(0, 1, 0, 0, 3, 0, "stripped_dark_oak_log[axis=y]");
        f.fill(1, 4, 0, 4, 4, 0, "stripped_dark_oak_log[axis=" + f.axisU() + "]");
        f.sign(2, 3, -1, "dark_oak", List.of("<au>Auftragsbrett</au>", "<mu>Orders</mu>", "Rechtsklick", ""),
                List.of());
        f.sign(3, 3, -1, "dark_oak", List.of("<em>Gesucht!</em>", "<mu>Wanted!</mu>", "<mu>/auftraege</mu>", ""),
                List.of());
        f.sign(1, 2, -1, "birch", List.of("Biete Taler,", "suche Ware", "", ""), List.of());
        f.sign(2, 2, -1, "birch", List.of("Liefern =", "sofort Geld", "", ""), List.of());
        f.sign(3, 2, -1, "birch", List.of("Paying Taler", "for goods", "", ""), List.of());
        f.sign(4, 2, -1, "birch", List.of("Deliver =", "instant pay", "", ""), List.of());
        for (int u = 0; u <= 4; u++) {
            for (int y = 1; y <= 4; y++) {
                trigger(f, u, y, 0, "orders");
                trigger(f, u, y, -1, "orders");
            }
        }
        f.sign(6, 5, -1, "dark_oak", List.of("<au>Kontor</au>", "<mu>Office</mu>", "", ""), List.of());
        f.set(6, 1, 6, "lectern[facing=" + f.f() + ",has_book=false]");
        trigger(f, 6, 1, 6, "orders");
    }

    /** Offene Markthalle für den Ankauf: Dach auf Pfosten, Heuballen, Kürbisse, Tresen. */
    private void buybackHall(Frame f) {
        int w = 13;
        int d = 11;
        f.fill(0, 0, 0, w - 1, 0, d - 1, "stone_bricks");
        for (int u = 0; u < w; u += 4) {
            for (int v : new int[] {0, d - 1}) {
                f.fill(Math.min(u, w - 1), 1, v, Math.min(u, w - 1), 4, v, "stripped_spruce_log[axis=y]");
            }
        }
        f.fill(0, 5, 0, w - 1, 5, 0, "spruce_log[axis=" + f.axisU() + "]");
        f.fill(0, 5, d - 1, w - 1, 5, d - 1, "spruce_log[axis=" + f.axisU() + "]");
        f.fill(0, 5, 1, 0, 5, d - 2, "spruce_log[axis=" + f.axisV() + "]");
        f.fill(w - 1, 5, 1, w - 1, 5, d - 2, "spruce_log[axis=" + f.axisV() + "]");
        // Satteldach quer (First entlang u)
        for (int k = 0; k <= 6; k++) {
            int vf = -1 + k;
            int vb = d - k;
            int y = 6 + k;
            if (vf > vb) {
                break;
            }
            if (vf == vb) {
                f.fill(-1, y, vf, w, y, vf, "spruce_slab[type=bottom]");
                break;
            }
            f.fill(-1, y, vf, w, y, vf, "spruce_stairs[facing=" + f.b() + ",half=bottom]");
            f.fill(-1, y, vb, w, y, vb, "spruce_stairs[facing=" + f.f() + ",half=bottom]");
            if (vb - vf > 1) {
                f.fill(0, y, vf + 1, 0, y, vb - 1, "spruce_planks");
                f.fill(w - 1, y, vf + 1, w - 1, y, vb - 1, "spruce_planks");
            }
        }
        // Tresen vorne mit Schild, dahinter Ware
        f.fill(3, 1, 3, 9, 1, 3, "spruce_planks");
        f.fill(3, 2, 3, 9, 2, 3, "spruce_slab[type=bottom]");
        f.set(6, 1, 3, "composter");
        for (int u = 2; u <= 10; u++) {
            String b = switch (u % 5) {
                case 0 -> "hay_block[axis=y]";
                case 1 -> "pumpkin";
                case 2 -> "melon";
                case 3 -> "barrel[facing=up]";
                default -> "hay_block[axis=" + f.axisU() + "]";
            };
            f.set(u, 1, 8, b);
            if (u % 2 == 0) {
                f.set(u, 2, 8, "hay_block[axis=y]");
            }
        }
        f.set(1, 1, 5, "cauldron");
        f.set(11, 1, 5, "barrel[facing=up]");
        f.set(4, 4, 5, "lantern[hanging=true]");
        f.set(8, 4, 5, "lantern[hanging=true]");
        for (int u = 3; u <= 9; u++) {
            trigger(f, u, 1, 3, "buyback");
            trigger(f, u, 2, 3, "buyback");
        }
        f.sign(5, 1, 2, "spruce", List.of("<au>Ankauf</au>", "<mu>Buy-back</mu>", "Feldfrüchte und", "Monster-Beute"),
                List.of());
        f.sign(7, 1, 2, "spruce", List.of("<au>Ankauf</au>", "<mu>Buy-back</mu>", "crops and", "mob drops"),
                List.of());
        trigger(f, 5, 1, 2, "buyback");
        trigger(f, 7, 1, 2, "buyback");
        f.sign(6, 5, -1, "spruce", List.of("", "<au>Ankauf</au>", "<mu>/verkaufen</mu>", ""), List.of());
        trigger(f, 6, 5, -1, "buyback");
    }

    private void mailInterior(Frame f) {
        f.fill(2, 1, 4, 6, 1, 4, "barrel[facing=" + f.f() + "]");
        f.fill(2, 2, 4, 6, 2, 4, "spruce_slab[type=bottom]");
        f.fill(2, 1, 6, 6, 2, 6, "barrel[facing=" + f.f() + "]");
        f.set(4, 3, 2, "lantern[hanging=true]");
        for (int u = 2; u <= 6; u++) {
            trigger(f, u, 1, 4, "mail");
            trigger(f, u, 2, 4, "mail");
            trigger(f, u, 1, 6, "mail");
            trigger(f, u, 2, 6, "mail");
        }
        f.sign(4, 1, 3, "spruce", List.of("<au>Abholfach</au>", "<mu>Mailbox</mu>", "Rechtsklick", "<mu>/abholen</mu>"),
                List.of());
        trigger(f, 4, 1, 3, "mail");
        f.sign(4, 5, -1, "spruce", List.of("", "<au>Abholfach</au>", "<mu>Mailbox</mu>", ""), List.of());
        trigger(f, 4, 5, -1, "mail");
    }

    /** Wegweiser-Platz vor dem Westtor (Reisetor zur Oberwelt): Pflaster, Bänke, Bäume, großer Wegweiser. */
    private void travelGate() {
        for (int x = -33; x <= -21; x++) {
            for (int z = -17; z <= -5; z++) {
                plan.set(x, 0, z, (x + z) % 2 == 0 ? "stone_bricks" : "polished_andesite");
            }
        }
        plan.set(-27, 1, -11, "spruce_log");
        plan.set(-27, 2, -11, "spruce_log");
        plan.set(-27, 3, -11, "spruce_log");
        plan.set(-27, 4, -11, "lantern[hanging=false]");
        plan.sign(-26, 2, -11, "spruce_wall_sign[facing=east]",
                List.of("<au>Reisetore</au>", "<em>West</em>: Oberwelt", "<red>Nord</red>: Nether",
                        "<light_purple>Ost</light_purple>: End"),
                List.of());
        plan.sign(-28, 2, -11, "spruce_wall_sign[facing=west]",
                List.of("<au>Travel gates</au>", "<em>West</em>: Overworld", "<red>North</red>: Nether",
                        "<light_purple>East</light_purple>: End"),
                List.of());
        // Bänke und Bäume auf dem Platz
        bench(-31, -16, Dir.SOUTH);
        bench(-31, -6, Dir.NORTH);
        tree(-23, -16);
        tree(-23, -6);
    }

    // ---------------------------------------------------------------- Gärten, Bäume, Bänke

    private void gardens() {
        int[][] trees = {
                {-15, -37}, {-9, -37}, {9, -37}, {15, -37},
                {-15, 37}, {-9, 37}, {9, 37}, {15, 37},
                {37, -15}, {37, -9}, {37, 9}, {37, 15},
                {-37, -15}, {-37, -9}, {-37, 9}, {-37, 15},
                {30, 30}, {-30, 30}, {30, -30}, {-30, -30}};
        for (int[] t : trees) {
            tree(t[0], t[1]);
        }
        // Blumenbeete in den Gärten hinter den Gebäuden
        flowers(5, -40, 17, -35);
        flowers(-17, -40, -5, -35);
        flowers(5, 35, 17, 40);
        flowers(-17, 35, -5, 40);
        flowers(35, -17, 40, -5);
        flowers(35, 5, 40, 17);
        flowers(-40, -17, -35, -5);
        flowers(-40, 5, -35, 17);
        // Bänke an den Hauptstraßen
        bench(-5, -36, Dir.EAST);
        bench(5, -36, Dir.WEST);
        bench(-5, 36, Dir.EAST);
        bench(5, 36, Dir.WEST);
        bench(36, -5, Dir.SOUTH);
        bench(36, 5, Dir.NORTH);
        bench(-36, -5, Dir.SOUTH);
        bench(-36, 5, Dir.NORTH);
    }

    /** Linde: Stamm, runde Krone (Laub bleibt, verwelkt nicht). */
    private void tree(int x, int z) {
        plan.set(x, 0, z, "grass_block");
        for (int y = 1; y <= 4; y++) {
            plan.set(x, y, z, "oak_log[axis=y]");
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int d2 = dx * dx + dz * dz + dy * dy;
                    if (d2 <= 5 && !(dx == 0 && dz == 0 && dy <= 0)) {
                        String leaf = Plan.noise(x + dx, z + dz, dy, 7) == 0 ? "flowering_azalea_leaves"
                                : "oak_leaves";
                        if (plan.get(x + dx, 5 + dy, z + dz) == null) {
                            plan.set(x + dx, 5 + dy, z + dz, leaf + "[persistent=true]");
                        }
                    }
                }
            }
        }
    }

    private void flowers(int x1, int z1, int x2, int z2) {
        String[] kinds = {"poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "red_tulip",
                "white_tulip", "short_grass", "short_grass", "short_grass", "short_grass"};
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (plan.get(x, 1, z) == null && "grass_block".equals(plan.get(x, 0, z))
                        && Plan.noise(x, z, 21, 3) == 0) {
                    plan.set(x, 1, z, kinds[Plan.noise(x, z, 22, kinds.length)]);
                }
            }
        }
    }

    /** Bank aus Treppen mit Falltüren als Armlehnen; man sitzt mit Blick nach {@code facing}. */
    private void bench(int x, int z, Dir facing) {
        Dir along = facing.right();
        // Treppe „facing“ = Rückenlehne; Blick also entgegengesetzt
        String seat = "spruce_stairs[facing=" + facing.opposite().id() + ",half=bottom]";
        plan.set(x, 1, z, seat);
        plan.set(x + along.dx, 1, z + along.dz, seat);
        plan.set(x - along.dx, 1, z - along.dz, "spruce_trapdoor[facing=" + along.opposite().id()
                + ",half=bottom,open=true]");
        plan.set(x + 2 * along.dx, 1, z + 2 * along.dz, "spruce_trapdoor[facing=" + along.id()
                + ",half=bottom,open=true]");
    }

    // ---------------------------------------------------------------- Laternen

    private void lanterns() {
        for (int d = 22; d <= 40; d += 6) {
            for (int s : new int[] {-1, 1}) {
                post(4, s * d);
                post(-4, s * d);
                post(s * d, 4);
                post(s * d, -4);
            }
        }
        // rund um den Platz
        for (int[] p : new int[][] {{-17, -17}, {17, -17}, {-17, 17}, {17, 17}, {-17, -6}, {17, -6}, {-17, 6},
                {17, 6}, {-6, -17}, {6, -17}, {-6, 17}, {6, 17}}) {
            if (plan.get(p[0], 1, p[1]) == null) {
                post(p[0], p[1]);
            }
        }
        // vor den Toren
        for (int s : new int[] {-1, 1}) {
            post(4, s * 49);
            post(-4, s * 49);
            post(s * 49, 4);
            post(s * 49, -4);
        }
    }

    private void post(int x, int z) {
        plan.set(x, 1, z, "spruce_fence");
        plan.set(x, 2, z, "spruce_fence");
        plan.set(x, 3, z, "lantern[hanging=false]");
    }

    // ---------------------------------------------------------------- Mauer, Tore, Türme

    private static String wallStone(int x, int y, int z) {
        return switch (Plan.noise(x * 7 + y, z, 31, 12)) {
            case 0 -> "mossy_stone_bricks";
            case 1 -> "cracked_stone_bricks";
            default -> "stone_bricks";
        };
    }

    private void walls() {
        for (int x = -WALL; x <= WALL; x++) {
            for (int z = -WALL; z <= WALL; z++) {
                int c = cheb(x, z);
                if (c != WALL && c != WALL - 1) {
                    continue;
                }
                plan.set(x, 0, z, "stone_bricks");
                for (int y = 1; y <= 6; y++) {
                    plan.set(x, y, z, wallStone(x, y, z));
                }
                if (c == WALL && Math.floorMod(x + z, 2) == 0) {
                    plan.set(x, 7, z, wallStone(x, 7, z));
                }
            }
        }
        gateHouse(Dir.NORTH, NETHER);
        gateHouse(Dir.SOUTH, null);
        gateHouse(Dir.EAST, END);
        gateHouse(Dir.WEST, OVERWORLD);
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                tower(sx * WALL, sz * WALL);
            }
        }
    }

    /** Aussehen eines Reisetors. */
    private record Theme(String target, String[] stones, String rim, String stairs, String floor, String crown,
                         String banner, List<String[]> pattern, String de, String en, String colour) {
        String stone(int x, int y, int z) {
            int n = Plan.noise(x * 5 + y, z, 47, 10);
            return n < 6 ? stones[0] : n < 9 ? stones[1] : stones[2];
        }
    }

    private static final Theme OVERWORLD = new Theme("overworld",
            new String[] {"stone_bricks", "mossy_stone_bricks", "mossy_cobblestone"}, "oak_log[axis=y]",
            "mossy_stone_brick_stairs", "moss_block", "lantern[hanging=false]", "green_wall_banner",
            List.<String[]>of(new String[] {"lime", "rhombus"}, new String[] {"yellow", "flower"}),
            "Oberwelt", "Overworld", "em");
    private static final Theme NETHER = new Theme("nether",
            new String[] {"polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "gilded_blackstone"},
            "crying_obsidian", "polished_blackstone_brick_stairs", "crimson_nylium", "soul_lantern[hanging=false]",
            "red_wall_banner", List.<String[]>of(new String[] {"orange", "rhombus"}, new String[] {"black", "border"}),
            "Nether", "Nether", "red");
    private static final Theme END = new Theme("end",
            new String[] {"end_stone_bricks", "purpur_block", "end_stone_bricks"}, "purpur_pillar[axis=y]",
            "purpur_stairs", "end_stone", "end_rod[facing=up]", "purple_wall_banner",
            List.<String[]>of(new String[] {"magenta", "rhombus"}, new String[] {"black", "border"}),
            "End", "End", "light_purple");

    /**
     * Torhaus über einer Hauptstraße: Bogen 5 breit, oben ein kleines Haus mit Dach und Banner. Mit {@code theme} ist
     * es ein Reisetor: eigene Steine, Rahmen, Boden und Krone, die Öffnung schimmert (das Bild setzt das Plugin) und
     * wer hindurchgeht, landet per Zufalls-Teleport in dieser Dimension.
     */
    private void gateHouse(Dir out, Theme theme) {
        // Ursprung: Außenkante, 4 Blöcke „links“ der Straßenmitte; u läuft 9 Blöcke die Mauer entlang, v 4 nach innen
        Frame f = new Frame(plan, out.dx * (WALL + 1) - 4 * out.left().dx, out.dz * (WALL + 1) - 4 * out.left().dz,
                out);
        int w = 9;
        int d = 4;
        f.fill(0, 0, 0, w - 1, 0, d - 1, "stone_bricks");
        for (int u = 0; u < w; u++) {
            for (int v = 0; v < d; v++) {
                boolean opening = u >= 2 && u <= 6;
                for (int y = 1; y <= 10; y++) {
                    if (opening && y <= 5) {
                        f.set(u, y, v, "air");
                        continue;
                    }
                    int x = f.x(u, v);
                    int z = f.z(u, v);
                    f.set(u, y, v, theme == null ? wallStone(x, y, z) : theme.stone(x, y, z));
                }
            }
        }
        String stairs = theme == null ? "stone_brick_stairs" : theme.stairs();
        // Rundbogen
        for (int v = 0; v < d; v++) {
            f.set(2, 5, v, stairs + "[facing=" + f.l() + ",half=top]");
            f.set(6, 5, v, stairs + "[facing=" + f.r() + ",half=top]");
            f.set(2, 0, v, "stone_bricks");
        }
        // Fenster und Zinnen
        for (int u : new int[] {2, 6}) {
            f.set(u, 8, 0, "iron_bars");
            f.set(u, 8, d - 1, "iron_bars");
        }
        for (int u = 0; u < w; u += 2) {
            f.set(u, 11, 0, theme == null ? "stone_bricks" : theme.stones()[0]);
            f.set(u, 11, d - 1, theme == null ? "stone_bricks" : theme.stones()[0]);
        }
        // Dach: flache Pyramide aus Schiefer
        for (int k = 0; k <= 2; k++) {
            f.fill(1 + k, 11 + k, 1, w - 2 - k, 11 + k, d - 2, "deepslate_tile_slab[type=bottom]");
        }
        f.set(4, 14, 1, theme == null ? "lightning_rod" : theme.crown());
        if (theme == null) {
            plan.banner(f.x(4, -1), 9, f.z(4, -1), "light_blue_wall_banner[facing=" + f.f() + "]", LOZENGE);
            plan.banner(f.x(4, d), 9, f.z(4, d), "light_blue_wall_banner[facing=" + f.b() + "]", LOZENGE);
            f.sign(4, 6, -1, "spruce", List.of("", "<au>Marktstadt</au>", "<mu>MorniSMP</mu>", ""), List.of());
            plan.sign(f.x(4, d), 6, f.z(4, d), "spruce_wall_sign[facing=" + f.b() + "]",
                    List.of("", "<au>Aussicht</au>", "<mu>Viewpoint</mu>", ""), List.of());
            return;
        }
        // Rahmen um die Öffnung (beide Seiten), Boden, Kronen neben dem Bogen
        for (int v : new int[] {0, d - 1}) {
            for (int y = 1; y <= 6; y++) {
                f.set(1, y, v, theme.rim());
                f.set(7, y, v, theme.rim());
            }
            for (int u = 1; u <= 7; u++) {
                f.set(u, 6, v, theme.rim().startsWith("oak_log") ? "stripped_oak_wood" : theme.rim());
            }
            f.set(1, 7, v, theme.crown());
            f.set(7, 7, v, theme.crown());
        }
        f.fill(2, 0, 0, 6, 0, d - 1, theme.floor());
        plan.banner(f.x(4, -1), 9, f.z(4, -1), theme.banner() + "[facing=" + f.f() + "]", theme.pattern());
        // Stadtseite: Banner hoch oben, darunter steht die große Schrift (setzt das Plugin)
        plan.banner(f.x(4, d), 10, f.z(4, d), theme.banner() + "[facing=" + f.b() + "]", theme.pattern());
        f.sign(4, 7, -1, "spruce", List.of("", "<au>Marktstadt</au>", "<mu>MorniSMP</mu>", ""), List.of());
        // Reisetor für das Plugin
        Box box = Box.of(f.x(2, 1), 1, f.z(2, 1), f.x(6, 2), 3, f.z(6, 2));
        Box membrane = Box.of(f.x(2, 1), 1, f.z(2, 1), f.x(6, 1), 4, f.z(6, 1));
        Dir in = out.opposite();
        float yaw = (float) Math.toDegrees(Math.atan2(-in.dx, in.dz));
        String axis = out.dx == 0 ? "x" : "z";
        gates.add(new Layout.Gate(theme.target(), box, f.x(4, d + 2) + 0.5, f.z(4, d + 2) + 0.5, yaw, membrane,
                axis, f.x(4, d + 1) + 0.5, 6.2, f.z(4, d + 1) + 0.5));
    }

    /** Weiß-blaue Raute auf hellblauem Grund. */
    private static final List<String[]> LOZENGE = List.<String[]>of(new String[] {"white", "rhombus"},
            new String[] {"white", "border"});

    private void tower(int cx, int cz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int d2 = dx * dx + dz * dz;
                int x = cx + dx;
                int z = cz + dz;
                if (d2 > 18) {
                    continue;
                }
                plan.set(x, 0, z, "stone_bricks");
                for (int y = 1; y <= 10; y++) {
                    plan.set(x, y, z, d2 >= 10 ? wallStone(x, y, z) : "air");
                }
                plan.set(x, 10, z, d2 >= 10 ? wallStone(x, 10, z) : "spruce_planks");
                if (d2 >= 10 && Math.floorMod(dx + dz, 2) == 0) {
                    plan.set(x, 11, z, wallStone(x, 11, z));
                }
            }
        }
        // spitzes Dach
        for (int k = 0; k <= 4; k++) {
            int r = 3 - k;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == Math.max(r, 0)) {
                        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east")
                                : (dz > 0 ? "north" : "south");
                        plan.set(cx + dx, 12 + k, cz + dz, r <= 0 ? "dark_oak_planks"
                                : "dark_oak_stairs[facing=" + facing + ",half=bottom]");
                    } else if (Math.max(Math.abs(dx), Math.abs(dz)) < r) {
                        plan.set(cx + dx, 12 + k, cz + dz, "dark_oak_planks");
                    }
                }
            }
        }
        plan.set(cx, 16, cz, "dark_oak_fence");
        plan.banner(cx, 17, cz, "light_blue_banner[rotation=0]",
                List.<String[]>of(new String[] {"white", "rhombus"}, new String[] {"yellow", "border"}));
        // Licht innen
        plan.set(cx, 3, cz, "lantern[hanging=false]");
        plan.set(cx, 1, cz, "spruce_planks");
        plan.set(cx, 2, cz, "spruce_fence");
    }

    // ---------------------------------------------------------------- Wegweiser

    private void signposts() {
        int[][] corners = {{-18, -18, 14}, {18, -18, 2}, {-18, 18, 10}, {18, 18, 6}};
        for (int[] c : corners) {
            plan.set(c[0], 1, c[1], "spruce_fence");
            plan.set(c[0], 2, c[1], "spruce_fence");
            plan.sign(c[0], 3, c[1], "spruce_sign[rotation=" + c[2] + "]",
                    List.of("<au>Reisetore</au>:", "<em>W</em> Oberwelt", "<red>N</red> Nether", "<light_purple>O</light_purple> End"),
                    List.of("<au>Travel gates</au>:", "<em>W</em> Overworld", "<red>N</red> Nether",
                            "<light_purple>E</light_purple> End"));
        }
    }

    /** Rechtsklick auf diesen Block öffnet {@code kind} (nur, wo wirklich ein Block steht). */
    private void trigger(Frame f, int u, int y, int v, String kind) {
        String b = f.get(u, y, v);
        if (b != null && !b.equals("air")) {
            triggers.put(Plan.pack(f.x(u, v), y, f.z(u, v)), kind);
        }
    }
}
