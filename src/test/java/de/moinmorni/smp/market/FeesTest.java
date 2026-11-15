package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FeesTest {

    @Test
    void twoPercentRoundsCommercially() {
        assertEquals(200, Fees.basisPoints(2.0));
        assertEquals(20, Fees.of(1_000, 200));     // 10 ₮ → 0,20 ₮
        assertEquals(1, Fees.of(50, 200));         // 0,50 ₮ → 0,01 ₮ (genau 1,0)
        assertEquals(1, Fees.of(25, 200));         // 0,25 ₮ → 0,005 → aufgerundet 0,01
        assertEquals(0, Fees.of(24, 200));         // 0,24 ₮ → 0,0048 → 0
        assertEquals(0, Fees.of(1_000, 0));        // abgeschaltet
        assertEquals(0, Fees.of(0, 200));
        assertEquals(5_000, Fees.basisPoints(80)); // höchstens 50 %
        assertEquals(1, Fees.of(1, 5_000));        // nie mehr als der Betrag
    }
}
