package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Die Börse in der Datenbank: Handel verbuchen, Erholung, Verlauf, Steiger/Fallende, Zurücksetzen. */
class CoursesTest {

    private static final long HOUR = 3_600_000L;
    private static final String DIAMOND = "minecraft:diamond";
    private static final String WHEAT = "minecraft:wheat";

    private final TestClock clock = new TestClock();
    private final Database db = Database.memory();
    private final Courses courses = new Courses(db, clock);

    CoursesTest() {
        courses.configure(new Course.Rules(true, 4, 6, 25, 300));
    }

    @AfterEach
    void close() {
        db.close();
    }

    private void trade(String key, int amount, int volume, boolean bought) {
        db.run(c -> courses.record(c, key, amount, volume, bought));
    }

    @Test
    void sellingPushesDownBuyingPushesUpAndItRecovers() {
        assertEquals(1.0, courses.factor(DIAMOND), 1e-12);
        trade(DIAMOND, 64, 16, false);
        assertEquals(0.5, courses.factor(DIAMOND), 1e-9);
        clock.advance(6 * HOUR);
        assertEquals(Math.pow(2, -0.5), courses.factor(DIAMOND), 1e-9, "nach 6 Std. halb erholt");
        trade(WHEAT, 512, 256, true);
        assertTrue(courses.factor(WHEAT) > 1.4);
        // neu geladen aus der Datenbank: gleich
        Courses again = new Courses(db, clock);
        again.configure(courses.rules());
        assertEquals(courses.factor(DIAMOND), again.factor(DIAMOND), 1e-9);
    }

    @Test
    void historyVolumeMoversResetAndSet() {
        long start = clock.getAsLong();
        trade(DIAMOND, 32, 16, false);
        clock.advance(2 * HOUR);
        trade(DIAMOND, 32, 16, false);
        trade(WHEAT, 256, 256, true);
        clock.advance(HOUR);
        List<Courses.Point> pts = courses.history(DIAMOND, clock.getAsLong(), HOUR, 5);
        assertEquals(5, pts.size());
        assertEquals(1.0, pts.get(0).factor(), 1e-9, "vor dem ersten Handel: Grundpreis");
        assertTrue(pts.get(4).factor() < pts.get(2).factor(), "nach dem zweiten Verkauf tiefer");
        assertEquals(courses.factor(DIAMOND), pts.get(4).factor(), 1e-9, "letzter Punkt = jetzt");
        Courses.Volume v = courses.volume(DIAMOND, start);
        assertEquals(0, v.bought());
        assertEquals(64, v.sold());
        List<Courses.Mover> movers = courses.movers();
        assertEquals(DIAMOND, movers.get(0).key(), "größter Ausschlag zuerst");
        assertEquals(2, movers.size());
        courses.set(WHEAT, 5.0);
        assertEquals(3.0, courses.factor(WHEAT), 1e-9, "gesetzt, aber in den Grenzen");
        assertEquals(2, courses.reset(null));
        assertEquals(1.0, courses.factor(DIAMOND), 1e-12);
        assertTrue(courses.movers().isEmpty());
        assertTrue(courses.history(DIAMOND, clock.getAsLong(), HOUR, 5).get(1).factor() < 1, "Verlauf bleibt");
    }

    @Test
    void exchangeOffKeepsBasePrices() {
        courses.configure(Course.Rules.OFF);
        trade(DIAMOND, 1000, 16, false);
        assertEquals(1.0, courses.factor(DIAMOND), 1e-12);
    }

    @Test
    void oldHistoryIsPruned() {
        trade(DIAMOND, 16, 16, false);
        clock.advance(Courses.KEEP_MS + 2 * HOUR);
        courses.prune();
        long rows = db.tx(c -> Database.number(c, "SELECT COUNT(*) FROM course_log", 0));
        assertEquals(0, rows);
    }
}
