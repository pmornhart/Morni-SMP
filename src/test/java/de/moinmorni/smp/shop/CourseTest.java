package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Kurs-Rechnung der Börse: Druck, Halbieren, Grenzen, Erholung, Preise nie über/unter dem Grundpreis. */
class CourseTest {

    private static final Course.Rules R = new Course.Rules(true, 4, 6, 25, 300);
    private static final long HOUR = 3_600_000L;

    @Test
    void pressureMovesTheRateAndStaysInBounds() {
        assertEquals(1.0, Course.factor(0, R), 1e-12);
        assertEquals(0.5, Course.factor(-4, R), 1e-12, "4 Tagesmengen netto verkauft = halber Preis");
        assertEquals(2.0, Course.factor(4, R), 1e-12, "4 Tagesmengen netto gekauft = doppelter Preis");
        assertEquals(0.25, Course.factor(-100, R), 1e-12, "nie unter min-prozent");
        assertEquals(3.0, Course.factor(100, R), 1e-12, "nie über max-prozent");
        assertEquals(1.0, Course.factor(-4, Course.Rules.OFF), 1e-12, "Börse aus: immer Grundpreis");
        // Verkaufen von 64 bei Tagesmenge 16 = 4 Tagesmengen
        assertEquals(-4.0, Course.after(0, 64, 16, false, R), 1e-12);
        assertEquals(1.0, Course.after(0, 16, 16, true, R), 1e-12);
        assertEquals(R.minPressure(), Course.after(0, 1_000_000, 16, false, R), 1e-12, "sammelt sich nicht an");
        assertEquals(-8.0, R.minPressure(), 1e-9);
    }

    @Test
    void rateRecoversWithHalfLife() {
        assertEquals(-2.0, Course.decay(-4, 6 * HOUR, R), 1e-9, "nach der Erholungszeit halb so weit weg");
        assertEquals(-1.0, Course.decay(-4, 12 * HOUR, R), 1e-9);
        assertEquals(-4.0, Course.decay(-4, 0, R), 1e-12);
        assertEquals(0.0, Course.decay(-4, 30 * 24 * HOUR, R), 1e-12, "nach Wochen wieder genau beim Grundpreis");
    }

    @Test
    void merchantNeverPaysMoreNorChargesLessThanBase() {
        Random rnd = new Random(7);
        for (int i = 0; i < 20_000; i++) {
            long base = 1 + rnd.nextInt(10_000_000);
            double f = 0.01 + rnd.nextDouble() * 5;
            long b = Course.buyPrice(base, f);
            long s = Course.sellPrice(base, f);
            assertTrue(b >= base, "Kauf nie unter Grundpreis");
            assertTrue(s <= base && s >= 1, "Ankauf nie über Grundpreis, nie 0");
        }
        assertEquals(800, Course.buyPrice(800, 0.5), "fällt der Kurs, bleibt der Kaufpreis");
        assertEquals(400, Course.sellPrice(800, 0.5));
        assertEquals(1200, Course.buyPrice(800, 1.5));
        assertEquals(800, Course.sellPrice(800, 1.5), "steigt der Kurs, bleibt der Ankaufspreis");
        assertEquals(0, Course.buyPrice(0, 2), "nicht zu kaufen bleibt nicht zu kaufen");
        assertEquals(0, Course.sellPrice(0, 0.5));
        assertEquals(1, Course.sellPrice(2, 0.25), "ein Hundertstel bleibt");
    }

    @Test
    void sparklineShowsDirectionAndHeight() {
        assertEquals(1, Sparkline.trend(1.2));
        assertEquals(-1, Sparkline.trend(0.8));
        assertEquals(0, Sparkline.trend(1.003));
        int[] h = Sparkline.heights(List.of(1.0, 0.5, 2.0), 4);
        assertEquals(4, h[2]);
        assertEquals(1, h[1]);
        assertTrue(h[0] > 1 && h[0] < 4, "Grundpreis dazwischen");
        int[] flat = Sparkline.heights(List.of(1.0, 1.0, 1.0), 4);
        assertEquals(flat[0], flat[2]);
        String bars = Sparkline.bars(List.of(1.0, 0.9, 1.2, 1.2));
        assertTrue(bars.startsWith("<gray>") && bars.contains("<red>") && bars.contains("<green>"), bars);
        assertEquals(4, bars.replaceAll("<[^>]+>", "").length());
        assertEquals(12, Sparkline.change(1.0, 1.12));
        assertEquals(-30, Sparkline.change(1.0, 0.7));
    }
}
