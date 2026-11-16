package de.moinmorni.smp.shop;

import de.moinmorni.smp.economy.Money;

/**
 * Die Rechenregeln des Händlerhauses – ohne Server, mit Tests.
 *
 * <p>Damit der Händler den freien Markt nicht kaputtmacht, gilt: Er verkauft teuer (Obergrenze für Preise unter
 * Spielern) und kauft billig (Untergrenze) – dazwischen ist Platz für Auktionshaus, Aufträge und Ladenkisten. Der
 * Ankaufspreis liegt höchstens bei {@code maxPercent} % des Verkaufspreises und immer mindestens einen Hundertstel
 * darunter; so bringt Kaufen und gleich wieder Verkaufen nie Gewinn.
 */
public final class Pricing {

    /** Höchstens so viel auf einmal: ein ganzes Inventar voller Stapel (36 × 64). */
    public static final int MAX_AMOUNT = 36 * 64;

    private Pricing() {
    }

    /** Ankaufspreis begrenzen: ≤ {@code maxPercent} % des Verkaufspreises und echt kleiner als er. */
    public static long capSell(long buy, long sell, int maxPercent) {
        if (sell <= 0) {
            return 0;
        }
        if (buy <= 0) {
            return sell; // nur Ankauf (z. B. Mob-Beute, die der Händler nicht verkauft)
        }
        long cap = Math.min(buy * Math.max(0, Math.min(100, maxPercent)) / 100, buy - 1);
        return Math.max(0, Math.min(sell, cap));
    }

    /** Was {@code amount} Stück kosten, oder -1, wenn es zu viel wäre. */
    public static long cost(long unit, int amount) {
        if (unit <= 0 || amount <= 0) {
            return -1;
        }
        try {
            long total = Math.multiplyExact(unit, (long) amount);
            return total > Money.MAX ? -1 : total;
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    /** So viele Stück kann man sich mit {@code balance} leisten (höchstens {@link #MAX_AMOUNT}). */
    public static int affordable(long balance, long unit) {
        if (unit <= 0 || balance <= 0) {
            return 0;
        }
        return (int) Math.min(MAX_AMOUNT, balance / unit);
    }

    /** Wie viel darf man heute noch kaufen? {@code limit} 0 = ohne Grenze ({@link Integer#MAX_VALUE}). */
    public static int leftToday(int limit, long boughtToday) {
        if (limit <= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0, limit - boughtToday);
    }

    /** Ist die Menge erlaubt (1 … {@link #MAX_AMOUNT})? */
    public static boolean validAmount(int amount) {
        return amount >= 1 && amount <= MAX_AMOUNT;
    }
}
