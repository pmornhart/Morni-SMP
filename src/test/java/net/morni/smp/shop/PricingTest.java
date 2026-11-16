package net.morni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morni.smp.economy.Money;
import org.junit.jupiter.api.Test;

class PricingTest {

    @Test
    void sellPriceStaysWellBelowBuyPrice() {
        assertEquals(40, Pricing.capSell(100, 90, 40), "auf 40 % gedeckelt");
        assertEquals(30, Pricing.capSell(100, 30, 40), "darunter bleibt es");
        assertEquals(0, Pricing.capSell(100, 0, 40));
        assertEquals(0, Pricing.capSell(1, 1, 90), "1 Hundertstel Kaufpreis: Ankauf geht nicht mehr");
        assertEquals(1, Pricing.capSell(2, 5, 90), "immer echt kleiner als der Kaufpreis");
        assertEquals(500, Pricing.capSell(0, 500, 40), "nur Ankauf: keine Grenze");
        for (long buy = 1; buy < 3_000; buy += 7) {
            for (int pct : new int[] {0, 10, 40, 90, 100, 150}) {
                long sell = Pricing.capSell(buy, buy * 5, pct);
                assertTrue(sell < buy, "kein Gewinn mit Kaufen und Verkaufen: " + buy + "/" + pct);
            }
        }
    }

    @Test
    void costIsExactAndGuarded() {
        assertEquals(9_600, Pricing.cost(150, 64));
        assertEquals(-1, Pricing.cost(0, 5));
        assertEquals(-1, Pricing.cost(5, 0));
        assertEquals(-1, Pricing.cost(Money.MAX, 2), "über dem Höchstbetrag");
        assertEquals(-1, Pricing.cost(Long.MAX_VALUE / 2, 4), "kein Überlauf");
    }

    @Test
    void affordableAndDailyLimits() {
        assertEquals(66, Pricing.affordable(10_000, 150));
        assertEquals(0, Pricing.affordable(149, 150));
        assertEquals(Pricing.MAX_AMOUNT, Pricing.affordable(Money.MAX, 1), "höchstens ein volles Inventar");
        assertEquals(Integer.MAX_VALUE, Pricing.leftToday(0, 500));
        assertEquals(3, Pricing.leftToday(16, 13));
        assertEquals(0, Pricing.leftToday(16, 20));
        assertTrue(Pricing.validAmount(1));
        assertTrue(Pricing.validAmount(2304));
        assertFalse(Pricing.validAmount(0));
        assertFalse(Pricing.validAmount(2305));
    }
}
