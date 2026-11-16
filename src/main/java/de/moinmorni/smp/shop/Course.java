package de.moinmorni.smp.shop;

/**
 * Der Kurs einer Ware an der Börse – reine Rechnung, ohne Server, mit Tests.
 *
 * <p>Jede Ware hat einen <b>Druck</b>: Kaufen Spieler beim Händler, steigt er, verkaufen sie an ihn, sinkt er – gemessen
 * in Tagesmengen der Ware ({@code tag} in der config.yml). Aus dem Druck wird der <b>Kurs</b> (Faktor auf den
 * Grundpreis): {@code 2 ^ (Druck / halbieren-nach)}, begrenzt auf {@code min-prozent … max-prozent}. Mit der Zeit
 * verfliegt der Druck (Halbwertszeit {@code erholung-stunden}), der Kurs kehrt zum Grundpreis zurück.
 *
 * <p>Der Händler zahlt <b>nie mehr</b> als den Grundpreis (Ankauf = Grundpreis × min(1, Kurs)) und verlangt <b>nie
 * weniger</b> (Verkauf = Grundpreis × max(1, Kurs)). Darum bringt „billig kaufen, warten, teuer verkaufen“ beim Händler nie
 * Gewinn, und alles, was der Schleifen-Wächter für die Grundpreise prüft, gilt zu jedem Kurs.
 */
public final class Course {

    /** Ein Hundertstel Toleranz gegen Rundungsfehler beim Runden der Preise. */
    private static final double EPS = 1e-9;
    private static final double HOUR_MS = 3_600_000.0;

    private Course() {
    }

    /**
     * Einstellungen der Börse ({@code boerse.*}).
     *
     * @param on            Kurse an (aus: immer Grundpreis)
     * @param halveAfter    so viele Tagesmengen Netto-Verkauf halbieren den Kurs (Netto-Kauf verdoppelt ihn)
     * @param recoveryHours Halbwertszeit des Drucks in Stunden
     * @param minPercent    tiefster Kurs (Prozent vom Grundpreis, 1–100)
     * @param maxPercent    höchster Kurs (Prozent vom Grundpreis, 100–1000)
     */
    public record Rules(boolean on, double halveAfter, double recoveryHours, int minPercent, int maxPercent) {

        public static final Rules OFF = new Rules(false, 4, 6, 25, 300);
        public static final Rules DEFAULT = new Rules(true, 4, 6, 25, 300);

        public Rules {
            halveAfter = Double.isFinite(halveAfter) ? Math.max(0.25, Math.min(1000, halveAfter)) : 4;
            recoveryHours = Double.isFinite(recoveryHours) ? Math.max(0.05, Math.min(24 * 60, recoveryHours)) : 6;
            minPercent = Math.max(1, Math.min(100, minPercent));
            maxPercent = Math.max(100, Math.min(1000, maxPercent));
        }

        /** Tiefster Druck (darunter fällt der Kurs nicht weiter – mehr Verkäufe sammeln sich nicht an). */
        double minPressure() {
            return halveAfter * log2(minPercent / 100.0);
        }

        /** Höchster Druck. */
        double maxPressure() {
            return halveAfter * log2(maxPercent / 100.0);
        }
    }

    /** Druck nach {@code dtMs} Millisekunden Erholung. */
    public static double decay(double pressure, long dtMs, Rules r) {
        if (dtMs <= 0 || pressure == 0) {
            return pressure;
        }
        double p = pressure * Math.pow(0.5, dtMs / (r.recoveryHours() * HOUR_MS));
        return Math.abs(p) < 1e-6 ? 0 : p;
    }

    /** Kurs (Faktor auf den Grundpreis) zum Druck. Börse aus: immer 1. */
    public static double factor(double pressure, Rules r) {
        if (!r.on()) {
            return 1.0;
        }
        double f = Math.pow(2, pressure / r.halveAfter());
        return Math.max(r.minPercent() / 100.0, Math.min(r.maxPercent() / 100.0, f));
    }

    /**
     * Druck nach einem Handel: {@code amount} Stück, gemessen in Tagesmengen {@code volume}; Kauf hebt, Verkauf senkt.
     * Der Druck bleibt in den Grenzen des Kurses – wer weiter verkauft, drückt nicht ins Bodenlose, und der Kurs erholt
     * sich danach in der üblichen Zeit.
     */
    public static double after(double pressure, int amount, int volume, boolean bought, Rules r) {
        if (amount <= 0) {
            return pressure;
        }
        double delta = (double) amount / Math.max(1, volume);
        double p = pressure + (bought ? delta : -delta);
        return Math.max(r.minPressure(), Math.min(r.maxPressure(), p));
    }

    /** Was der Händler beim Kurs {@code f} verlangt: nie weniger als der Grundpreis (aufgerundet). */
    public static long buyPrice(long base, double f) {
        if (base <= 0) {
            return 0;
        }
        return (long) Math.ceil(base * Math.max(1.0, f) - EPS);
    }

    /** Was der Händler beim Kurs {@code f} zahlt: nie mehr als der Grundpreis (abgerundet, mindestens 1 Hundertstel). */
    public static long sellPrice(long base, double f) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, (long) Math.floor(base * Math.min(1.0, f) + EPS)); // nie 0: sonst wäre die Ware weg
    }

    /** Kurs in ganzen Prozent (100 = Grundpreis). */
    public static int percent(double f) {
        return (int) Math.round(f * 100);
    }

    /** Volle Stunde (seit 1970) einer Uhrzeit – Eimer für den Verlauf. */
    public static long hour(long ms) {
        return Math.floorDiv(ms, (long) HOUR_MS);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
