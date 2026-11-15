package de.moinmorni.smp.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.town.Layout.Box;
import de.moinmorni.smp.town.Layout.StallSpot;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TownPlanTest {

    private final TownPlan.Result town = TownPlan.create();
    private final Plan plan = town.plan();
    private final Layout layout = town.layout();

    @Test
    void everyBlockExistsInThisMinecraftVersion() {
        Set<String> unknown = new TreeSet<>();
        for (String data : plan.blocks().values()) {
            String name = data.indexOf('[') < 0 ? data : data.substring(0, data.indexOf('['));
            Material m = Material.matchMaterial(name);
            if (m == null) {
                unknown.add(name);
            }
            if (data.contains("[")) {
                assertTrue(data.endsWith("]") && data.indexOf('[') == data.lastIndexOf('['), "Zustand: " + data);
            }
        }
        assertEquals(Set.of(), unknown, "unbekannte Blöcke");
    }

    @Test
    void nothingOutsideTheTownArea() {
        for (long p : plan.blocks().keySet()) {
            assertTrue(Math.abs(Plan.x(p)) <= TownPlan.AREA && Math.abs(Plan.z(p)) <= TownPlan.AREA,
                    "außerhalb: " + Plan.x(p) + "/" + Plan.z(p));
            assertTrue(Plan.y(p) >= 0 && Plan.y(p) < 40, "Höhe: " + Plan.y(p));
        }
        assertTrue(plan.blocks().size() > 20_000, "die Stadt ist groß: " + plan.blocks().size());
    }

    @Test
    void sixteenStallsOnTheSquareWithoutOverlap() {
        assertEquals(16, layout.stalls().size());
        Set<Integer> ids = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        for (StallSpot s : layout.stalls()) {
            assertTrue(ids.add(s.id()));
            Box z = s.zone();
            assertEquals(4, Math.max(z.x2() - z.x1(), z.z2() - z.z1()) + 1, "4 breit");
            assertEquals(3, Math.min(z.x2() - z.x1(), z.z2() - z.z1()) + 1, "3 tief");
            for (int x = z.x1(); x <= z.x2(); x++) {
                for (int zz = z.z1(); zz <= z.z2(); zz++) {
                    assertTrue(Math.abs(x) <= 16 && Math.abs(zz) <= 16, "Stand auf dem Platz");
                    assertTrue(cells.add(Plan.pack(x, 0, zz)), "Stände überlappen");
                }
            }
            assertTrue(layout.triggers().containsKey(Plan.pack(s.signX(), s.signY(), s.signZ())), "Schild klickbar");
            assertTrue(plan.get(s.signX(), s.signY(), s.signZ()).startsWith("spruce_hanging_sign"));
        }
    }

    @Test
    void arrivalGateAndFountainWork() {
        int sx = (int) Math.floor(layout.spawnX());
        int sz = (int) Math.floor(layout.spawnZ());
        assertNull(plan.get(sx, 1, sz), "Ankunft: Füße frei");
        assertNull(plan.get(sx, 2, sz), "Ankunft: Kopf frei");
        assertNotNull(plan.get(sx, 0, sz), "Ankunft: fester Boden");
        assertEquals(Set.of("overworld", "nether", "end"),
                layout.gates().stream().map(Layout.Gate::target).collect(java.util.stream.Collectors.toSet()));
        for (Layout.Gate gate : layout.gates()) {
            // Tor offen (nur der Bogen oben darf Treppe sein), Boden darunter, Rückweg in der Stadt frei
            for (Box g : List.of(gate.box(), gate.membrane())) {
                for (int x = g.x1(); x <= g.x2(); x++) {
                    for (int y = g.y1(); y <= g.y2(); y++) {
                        for (int z = g.z1(); z <= g.z2(); z++) {
                            String b = plan.get(x, y, z);
                            assertTrue(b == null || b.equals("air"), gate.target() + ": Tor offen bei " + x + "/" + y
                                    + "/" + z + ": " + b);
                            assertNotNull(plan.get(x, 0, z), "Boden im Tor");
                        }
                    }
                }
            }
            int bx = (int) Math.floor(gate.backX());
            int bz = (int) Math.floor(gate.backZ());
            assertTrue(Math.max(Math.abs(bx), Math.abs(bz)) < 45, "Rückweg innerhalb der Mauer");
            assertNull(plan.get(bx, 1, bz), "Rückweg: Füße frei");
            assertNull(plan.get(bx, 2, bz), "Rückweg: Kopf frei");
            assertNotNull(plan.get(bx, 0, bz), "Rückweg: Boden");
            assertTrue(!gate.box().containsXZ(bx, bz), "Rückweg liegt nicht im Tor");
            String axis = gate.axis();
            assertTrue(axis.equals("x") ? gate.membrane().z1() == gate.membrane().z2()
                    : gate.membrane().x1() == gate.membrane().x2(), "Tor-Bild ist eine Fläche");
        }
        // Wasser im Brunnen läuft nirgends hin
        for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
            if (!e.getValue().equals("water")) {
                continue;
            }
            int x = Plan.x(e.getKey());
            int y = Plan.y(e.getKey());
            int z = Plan.z(e.getKey());
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                String n = plan.get(x + d[0], y, z + d[1]);
                assertNotNull(n, "Wasser fließt aus bei " + x + "/" + z);
                assertTrue(!n.equals("air"), "Wasser fließt aus bei " + x + "/" + z);
            }
            assertNotNull(plan.get(x, y - 1, z), "Wasser hat Boden");
        }
        // Wasserfälle des Brunnens landen im Becken
        int falls = 0;
        for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
            if (!e.getValue().equals("water[level=8]")) {
                continue;
            }
            falls++;
            String below = plan.get(Plan.x(e.getKey()), Plan.y(e.getKey()) - 1, Plan.z(e.getKey()));
            assertTrue(below != null && below.startsWith("water"), "Wasserfall landet im Wasser");
        }
        assertTrue(falls >= 4, "vier Wasserfälle");
        assertTrue(layout.boardY() > 9, "Bestenliste über der Brunnenspitze");
    }

    @Test
    void triggersPointAtBlocks() {
        assertTrue(layout.triggers().values().containsAll(Set.of("ah", "orders", "buyback", "mail")));
        for (long p : layout.triggers().keySet()) {
            String b = plan.get(Plan.x(p), Plan.y(p), Plan.z(p));
            assertNotNull(b, "Auslöser ohne Block");
            assertTrue(!b.equals("air"), "Auslöser auf Luft");
        }
    }

    @Test
    void fencesAndPanesAreConnected() {
        int connected = 0;
        for (String data : plan.blocks().values()) {
            if (data.startsWith("glass_pane") || data.startsWith("spruce_fence[")) {
                assertTrue(data.contains("north="), "Verbindungen gesetzt: " + data);
                if (data.contains("=true")) {
                    connected++;
                }
            }
        }
        assertTrue(connected > 100);
    }
}
