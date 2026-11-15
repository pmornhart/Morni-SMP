package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuybackTest {

    private static final List<Buyback.Tier> TIERS = List.of(new Buyback.Tier(0, 100), new Buyback.Tier(1, 50),
            new Buyback.Tier(2, 25), new Buyback.Tier(4, 10));

    @Test
    void tiersShrinkThePricePerPiece() {
        // 10 Hundertstel pro Stück, 256 am Tag voll
        assertEquals(2_560, Buyback.price(10, 256, 0, 256, TIERS));
        assertEquals(1_280, Buyback.price(10, 256, 256, 256, TIERS)); // zweite Stufe: halber Preis
        assertEquals(1_280, Buyback.price(10, 256, 512, 512, TIERS)); // dritte: ein Viertel
        assertEquals(256, Buyback.price(10, 256, 1_024, 256, TIERS)); // ab 4×: 10 %
        // ein Verkauf über die Grenze hinweg wird aufgeteilt: 6 voll + 4 halb
        assertEquals(60 + 20, Buyback.price(10, 256, 250, 10, TIERS));
        // gleiche Summe, egal ob in einem Rutsch oder in Teilen
        long whole = Buyback.price(7, 100, 0, 1_000, TIERS);
        long parts = Buyback.price(7, 100, 0, 333, TIERS) + Buyback.price(7, 100, 333, 333, TIERS)
                + Buyback.price(7, 100, 666, 334, TIERS);
        assertEquals(whole, parts, 2); // höchstens Rundungsreste
        assertEquals(0, Buyback.price(10, 256, 0, 0, TIERS));
    }

    @Test
    void sellCountsPerPlayerAndDay() {
        try (MarketFixture f = new MarketFixture()) {
            Buyback bb = new Buyback(f.db, f.bank);
            bb.configure(List.of(new Buyback.Offer("minecraft:wheat", 10, 100),
                    new Buyback.Offer("minecraft:bone", 20, 50)), TIERS);
            UUID a = f.player("A", 0);
            UUID b = f.player("B", 0);
            Buyback.Quote q1 = bb.sell(a, Map.of("minecraft:wheat", 100, "minecraft:diamond", 5), "2026-11-15");
            assertEquals(1_000, q1.total());
            assertEquals(1, q1.lines().size(), "Diamanten kauft die Stadt nicht");
            Buyback.Quote q2 = bb.sell(a, Map.of("minecraft:wheat", 100), "2026-11-15");
            assertEquals(500, q2.total(), "zweite Ladung am selben Tag: halber Preis");
            assertEquals(1_000, bb.sell(b, Map.of("minecraft:wheat", 100), "2026-11-15").total(), "pro Spieler");
            assertEquals(1_000, bb.sell(a, Map.of("minecraft:wheat", 100), "2026-11-16").total(), "neuer Tag");
            assertEquals(2_500, f.bank.balance(a));
            assertEquals(Map.of(), bb.soldToday(a, "2026-11-15"), "alte Tage werden aufgeräumt");
            assertEquals(1_000, bb.quote(a, Map.of("minecraft:bone", 50), "2026-11-16").total());
        }
    }
}
