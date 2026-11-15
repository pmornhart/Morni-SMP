package de.moinmorni.smp.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.teleport.SafeSpot.Column;
import de.moinmorni.smp.teleport.SafeSpot.Ground;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SafeSpotTest {

    private static boolean ok(Column c) {
        return SafeSpot.ok(c, -60, 300, true, false, false, true);
    }

    @Test
    void onlySolidGroundWithRoomAndSky() {
        assertTrue(ok(new Column(Ground.SOLID, 70, true, true, true)));
        assertFalse(ok(new Column(Ground.LIQUID, 62, true, true, true)), "Wasser");
        assertFalse(ok(new Column(Ground.LEAVES, 80, true, true, true)), "Baumkrone");
        assertFalse(ok(new Column(Ground.DANGEROUS, 70, true, true, true)), "Magma, Kaktus …");
        assertFalse(ok(new Column(Ground.NONE, 70, true, true, true)), "nichts darunter");
        assertFalse(ok(new Column(Ground.SOLID, 70, false, true, true)), "Füße im Block");
        assertFalse(ok(new Column(Ground.SOLID, 70, true, false, true)), "Kopf im Block");
        assertFalse(ok(new Column(Ground.SOLID, 40, true, true, false)), "Höhle");
        assertFalse(SafeSpot.ok(new Column(Ground.SOLID, 70, true, true, true), -60, 300, true, true, false, true),
                "Grundstück");
        assertFalse(SafeSpot.ok(new Column(Ground.SOLID, 70, true, true, true), -60, 300, true, false, true, true),
                "Stadt");
        assertFalse(SafeSpot.ok(new Column(Ground.SOLID, 70, true, true, true), -60, 300, true, false, false, false),
                "außerhalb der Weltgrenze");
        // Nether: kein Himmel nötig, aber unter der Decke bleiben
        assertTrue(SafeSpot.ok(new Column(Ground.SOLID, 60, true, true, false), 1, 120, false, false, false, true));
        assertFalse(SafeSpot.ok(new Column(Ground.SOLID, 126, true, true, false), 1, 120, false, false, false, true));
        assertFalse(ok(null));
    }

    @Test
    void pickedPointsStayInTheRingAndInsideTheBorder() {
        Random rng = new Random(42);
        SafeSpot.Band band = new SafeSpot.Band(500, 2_000);
        for (int i = 0; i < 5_000; i++) {
            SafeSpot.Point p = SafeSpot.pick(rng, band, 0, 0, 10_000, 32);
            double r = Math.hypot(p.x(), p.z());
            assertTrue(r >= 499 && r <= 2_001, "im Ring: " + r);
        }
        // Ring größer als die Grenze → wird gekürzt
        SafeSpot.Band far = new SafeSpot.Band(5_000, 9_000);
        for (int i = 0; i < 5_000; i++) {
            SafeSpot.Point p = SafeSpot.pick(rng, far, 0, 0, 3_000, 32);
            assertTrue(Math.abs(p.x()) <= 2_968 && Math.abs(p.z()) <= 2_968, "innerhalb der Grenze");
        }
    }
}
