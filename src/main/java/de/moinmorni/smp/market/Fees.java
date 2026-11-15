package de.moinmorni.smp.market;

/** Gebühren in Basispunkten (1 % = 100). Gerundet wird kaufmännisch auf ganze Hundertstel. */
public final class Fees {

    private Fees() {
    }

    /** Prozent aus der config.yml (z. B. 2.0) → Basispunkte (200), zwischen 0 und 50 %. */
    public static int basisPoints(double percent) {
        return (int) Math.max(0, Math.min(5000, Math.round(percent * 100)));
    }

    /** Gebühr für einen Betrag. Nie mehr als der Betrag selbst. */
    public static long of(long amount, int basisPoints) {
        if (amount <= 0 || basisPoints <= 0) {
            return 0;
        }
        long fee = (amount * basisPoints + 5_000) / 10_000;
        return Math.min(fee, amount);
    }
}
