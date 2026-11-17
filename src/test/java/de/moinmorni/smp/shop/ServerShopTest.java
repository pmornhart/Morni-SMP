package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.market.Buyback;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerShopTest {

    private static final String DAY = "2026-11-16";
    private static final List<Buyback.Tier> TIERS = List.of(new Buyback.Tier(0, 100), new Buyback.Tier(1, 50),
            new Buyback.Tier(2, 20), new Buyback.Tier(4, 5));

    private final TestClock clock = new TestClock();
    private final Database db = Database.memory();
    private final Bank bank = new Bank(db, clock, 0);
    private final Courses courses = new Courses(db, clock);
    private final ServerShop shop = new ServerShop(db, bank, new Buyback(db, bank), courses);

    ServerShopTest() {
        courses.configure(Course.Rules.OFF); // feste Preise – die Börse hat eigene Tests
        Catalog c = Catalog.of(List.of(new Catalog.Category("essen", "bread", "Essen", "Food"),
                        new Catalog.Category("erze", "diamond", "Erze", "Ores")),
                List.of(new Ware("wheat", "essen", 150, 50, 100, 0, Set.of()),
                        new Ware("bread", "essen", 150, 0, 256, 0, Set.of()),
                        new Ware("rotten_flesh", "essen", 0, 10, 50, 0, Set.of()),
                        new Ware("diamond", "erze", 8_000, 2_000, 16, 3, Set.of()),
                        new Ware("snowball", "essen", 20, 0, 256, 0, Set.of(12, 1, 2))),
                40, null, w -> { });
        shop.configure(c, TIERS);
    }

    @AfterEach
    void close() {
        db.close();
    }

    private UUID player(String name, long cents) {
        UUID u = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        bank.ensure(u, name);
        if (cents > 0) {
            bank.deposit(u, cents, "test", null);
        }
        return u;
    }

    private long ledger(String reason) {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(amount), 0) FROM ledger WHERE reason = ?", 0,
                reason));
    }

    @Test
    void buyChargesExactlyAndWritesTheLedger() {
        UUID a = player("Anna", 10_000);
        ServerShop.Deal d = shop.buy(a, "minecraft:wheat", 64, DAY, 11);
        assertEquals(ServerShop.Status.OK, d.status());
        assertEquals(9_600, d.total());
        assertEquals(400, bank.balance(a));
        String ref = db.tx(c -> Database.one(c, "SELECT ref FROM ledger WHERE reason = 'shop'", rs -> rs.getString(1)));
        assertEquals("minecraft:wheat×64", ref);
        assertEquals(9_600, ledger("shop"));
        assertEquals(9_600 - 10_000, bank.flow(Bank.SERVER), "das Geld ging an den Server (10 000 kamen vorher von ihm)");
    }

    @Test
    void tooLittleMoneyChangesNothing() {
        UUID a = player("Anna", 1_000);
        ServerShop.Deal d = shop.buy(a, "minecraft:wheat", 7, DAY, 11);
        assertEquals(ServerShop.Status.NOT_ENOUGH, d.status());
        assertEquals(1_000, bank.balance(a));
        assertEquals(0, ledger("shop"));
        assertEquals(0, shop.boughtToday(a, "minecraft:wheat", DAY), "auch kein Zähler");
    }

    @Test
    void wrongWaresAndAmounts() {
        UUID a = player("Anna", 100_000);
        assertEquals(ServerShop.Status.UNKNOWN, shop.buy(a, "minecraft:elytra", 1, DAY, 11).status());
        assertEquals(ServerShop.Status.NOT_FOR_SALE, shop.buy(a, "minecraft:rotten_flesh", 1, DAY, 11).status());
        assertEquals(ServerShop.Status.OFF_SEASON, shop.buy(a, "minecraft:snowball", 1, DAY, 7).status());
        assertEquals(ServerShop.Status.OK, shop.buy(a, "minecraft:snowball", 1, DAY, 12).status());
        assertEquals(ServerShop.Status.BAD_AMOUNT, shop.buy(a, "minecraft:wheat", 0, DAY, 11).status());
        assertEquals(ServerShop.Status.BAD_AMOUNT, shop.buy(a, "minecraft:wheat", 2_305, DAY, 11).status());
        assertEquals(ServerShop.Status.NOT_BOUGHT, shop.sell(a, "minecraft:bread", 1, DAY).status());
        assertEquals(100_000 - 20, bank.balance(a));
    }

    @Test
    void dailyBuyLimitPerPlayerAndDay() {
        UUID a = player("Anna", 1_000_000);
        UUID b = player("Bert", 1_000_000);
        assertEquals(ServerShop.Status.OK, shop.buy(a, "minecraft:diamond", 2, DAY, 11).status());
        ServerShop.Deal d = shop.buy(a, "minecraft:diamond", 2, DAY, 11);
        assertEquals(ServerShop.Status.LIMIT, d.status());
        assertEquals(1, d.left());
        assertEquals(ServerShop.Status.OK, shop.buy(a, "minecraft:diamond", 1, DAY, 11).status());
        assertEquals(ServerShop.Status.OK, shop.buy(b, "minecraft:diamond", 3, DAY, 11).status(), "pro Spieler");
        assertEquals(ServerShop.Status.OK, shop.buy(a, "minecraft:diamond", 3, "2026-11-17", 11).status(), "neuer Tag");
        assertEquals(0, shop.boughtToday(a, "minecraft:diamond", DAY), "alte Tage werden aufgeräumt");
    }

    @Test
    void sellUsesTheBuyBackTiersAndTheLedger() {
        UUID a = player("Anna", 0);
        assertEquals(5_000, shop.quoteSell(a, "minecraft:wheat", 100, DAY).total());
        assertEquals(5_000, shop.sell(a, "minecraft:wheat", 100, DAY).total());
        assertEquals(2_500, shop.quoteSell(a, "minecraft:wheat", 100, DAY).total(), "Tagesmenge voll: halber Preis");
        assertEquals(2_500, shop.sell(a, "minecraft:wheat", 100, DAY).total());
        assertEquals(7_500, bank.balance(a));
        assertEquals(7_500, ledger("ankauf"));
        String ref = db.tx(c -> Database.one(c, "SELECT ref FROM ledger WHERE reason = 'ankauf' LIMIT 1",
                rs -> rs.getString(1)));
        assertEquals("minecraft:wheat×100", ref);
    }

    /** Kaufen und gleich wieder verkaufen bringt nie Gewinn – egal in welchen Mengen und wie oft. */
    @Test
    void noEndlessMoneyLoop() {
        UUID a = player("Anna", 100_000);
        long start = bank.balance(a);
        for (int round = 0; round < 20; round++) {
            for (int n : new int[] {1, 7, 64, 99}) {
                ServerShop.Deal b = shop.buy(a, "minecraft:wheat", n, DAY, 11);
                if (!b.ok()) {
                    continue;
                }
                shop.sell(a, "minecraft:wheat", n, DAY);
                assertTrue(bank.balance(a) < start, "Runde " + round + ": Gewinn!");
                start = bank.balance(a);
            }
        }
    }

    @Test
    void parallelPurchasesNeverOverspendOrBreakTheLimit() throws Exception {
        UUID a = player("Anna", 50_000); // reicht für 6 Diamanten, Tagesgrenze 3
        UUID b = player("Bert", 1_000); // reicht für 6 Weizen
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ServerShop.Deal>> diamonds = new ArrayList<>();
        List<Future<ServerShop.Deal>> wheat = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            diamonds.add(pool.submit(() -> {
                go.await();
                return shop.buy(a, "minecraft:diamond", 1, DAY, 11);
            }));
            wheat.add(pool.submit(() -> {
                go.await();
                return shop.buy(b, "minecraft:wheat", 1, DAY, 11);
            }));
        }
        go.countDown();
        int okD = 0;
        int okW = 0;
        for (Future<ServerShop.Deal> f : diamonds) {
            okD += f.get(10, TimeUnit.SECONDS).ok() ? 1 : 0;
        }
        for (Future<ServerShop.Deal> f : wheat) {
            okW += f.get(10, TimeUnit.SECONDS).ok() ? 1 : 0;
        }
        pool.shutdownNow();
        assertEquals(3, okD, "Tagesgrenze hält");
        assertEquals(6, okW, "Geld reicht genau für 6");
        assertEquals(50_000 - 3 * 8_000, bank.balance(a));
        assertEquals(1_000 - 6 * 150, bank.balance(b));
        assertEquals(3, shop.boughtToday(a, "minecraft:diamond", DAY));
        assertEquals(3 * 8_000 + 6 * 150, ledger("shop"), "Kassenbuch stimmt");
    }

    @Test
    void turnoverCountsBothDirections() {
        UUID a = player("Anna", 100_000);
        shop.buy(a, "minecraft:wheat", 10, DAY, 11);
        shop.buy(a, "minecraft:bread", 2, DAY, 11);
        shop.sell(a, "minecraft:wheat", 4, DAY);
        shop.sell(a, "minecraft:rotten_flesh", 0 + 5, DAY);
        ServerShop.Turnover t = shop.turnover(0);
        assertEquals(1_800, t.income());
        assertEquals(2, t.purchases());
        assertEquals(200 + 50, t.payout());
        assertEquals(2, t.sales());
        assertEquals("minecraft:wheat", t.top().get(0).key());
        assertEquals(10, t.top().get(0).bought());
        assertEquals(4, t.top().get(0).sold());
        assertEquals(0, shop.turnover(clock.getAsLong() + 1).purchases(), "nur ab dem Zeitpunkt");
    }

    // ---------------------------------------------------------------- Börse

    @Test
    void sellingLowersWhatVroniPaysAndBuyingRaisesWhatSheCharges() {
        courses.configure(new Course.Rules(true, 4, 6, 25, 300));
        UUID a = player("Anna", 10_000_000);
        bank.deposit(UUID.nameUUIDFromBytes("x".getBytes(StandardCharsets.UTF_8)), 0, "test", null);
        // Diamant: Tagesmenge 16, Ankauf 20 ₮
        ServerShop.Deal first = shop.sell(a, "minecraft:diamond", 16, DAY);
        assertEquals(16 * 2_000, first.total(), "erster Verkauf zum Grundpreis");
        Ware d = shop.catalog().ware("minecraft:diamond");
        ServerShop.Price after = shop.price(d);
        assertTrue(after.sell() < 2_000, "danach zahlt Vroni weniger: " + after.sell());
        assertEquals(8_000, after.buy(), "verlangt aber nicht weniger");
        assertTrue(after.percent() < 100);
        // Weizen kaufen hebt den Preis (Tagesmenge 100)
        ServerShop.Deal b1 = shop.buy(a, "minecraft:wheat", 200, DAY, 11);
        assertEquals(200 * 150, b1.total(), "zum Grundpreis");
        ServerShop.Price wheat = shop.price(shop.catalog().ware("minecraft:wheat"));
        assertTrue(wheat.buy() > 150, "teurer: " + wheat.buy());
        assertEquals(50, wheat.sell(), "zahlt aber nicht mehr");
        ServerShop.Deal b2 = shop.buy(a, "minecraft:wheat", 10, DAY, 11);
        assertEquals(10 * wheat.buy(), b2.total(), "Preis von jetzt");
        // Erholung
        clock.advance(48 * 3_600_000L);
        assertEquals(100, shop.price(d).percent(), "nach zwei Tagen wieder beim Grundpreis");
    }

    @Test
    void shownPriceProtectsTheBuyer() {
        courses.configure(new Course.Rules(true, 4, 6, 25, 300));
        UUID a = player("Anna", 1_000_000);
        UUID b = player("Bert", 1_000_000);
        long shown = 50 * 150;
        shop.buy(b, "minecraft:wheat", 300, DAY, 11); // Bert treibt den Preis hoch
        ServerShop.Deal d = shop.buy(a, "minecraft:wheat", 50, DAY, 11, shown);
        assertEquals(ServerShop.Status.PRICE_CHANGED, d.status());
        assertEquals(1_000_000, bank.balance(a), "nichts abgebucht");
    }

    /**
     * Über die Zeit kein Gewinn beim Händler: egal wann gekauft und wann verkauft wird, ein Ankauf bringt nie mehr, als
     * ein Kauf gekostet hätte (200 zufällige Handelstage).
     */
    @Test
    void buyLowSellHighNeverWorksAtTheMerchant() {
        courses.configure(new Course.Rules(true, 2, 3, 10, 500));
        UUID a = player("Anna", 100_000_000);
        java.util.Random rnd = new java.util.Random(99);
        long minBuy = Long.MAX_VALUE;
        long maxSell = 0;
        for (int i = 0; i < 200; i++) {
            if (rnd.nextBoolean()) {
                ServerShop.Deal d = shop.buy(a, "minecraft:wheat", 1 + rnd.nextInt(300), DAY, 11);
                if (d.ok()) {
                    minBuy = Math.min(minBuy, d.total() / d.amount());
                }
            } else {
                ServerShop.Deal d = shop.sell(a, "minecraft:wheat", 1 + rnd.nextInt(100), "2026-11-" + (10 + i % 15));
                if (d.ok() && d.total() > 0) {
                    maxSell = Math.max(maxSell, (d.total() + d.amount() - 1) / d.amount());
                }
            }
            clock.advance(rnd.nextInt(8) * 3_600_000L);
        }
        assertTrue(maxSell < minBuy, "höchster Ankauf " + maxSell + " < billigster Kauf " + minBuy);
        assertTrue(minBuy >= 150 && maxSell <= 50);
    }
}
