package net.morni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AuctionHouseTest {

    @Test
    void buyPaysSellerMinusFeeAndDeliversToMailbox() {
        try (MarketFixture f = new MarketFixture()) {
            UUID seller = f.player("Seller", 0);
            UUID buyer = f.player("Buyer", 10_000);
            long id = f.ah.list(seller, "Seller", MarketFixture.item("diamond", 16), 5_000).id();
            AuctionHouse.BuyResult r = f.ah.buy(id, buyer, "Buyer");
            assertEquals(AuctionHouse.BuyStatus.OK, r.status());
            assertEquals(100, r.fee());                       // 2 % von 50 ₮
            assertEquals(5_000, f.bank.balance(buyer));
            assertEquals(4_900, f.bank.balance(seller));
            assertEquals(100, f.fees());
            List<Mailbox.Mail> mail = f.mailbox.list(buyer, 10);
            assertEquals(1, mail.size());
            assertEquals(16, mail.get(0).item().amount());
            assertEquals("minecraft:diamond", mail.get(0).item().key());
            assertEquals(1, f.notices.takeAll(seller).size()); // Verkäufer erfährt es
            assertEquals(313, f.prices.median("minecraft:diamond").getAsLong()); // 50 ₮ / 16
            assertEquals(AuctionHouse.BuyStatus.GONE, f.ah.buy(id, buyer, "Buyer").status());
        }
    }

    @Test
    void cannotBuyOwnOrWithoutMoney() {
        try (MarketFixture f = new MarketFixture()) {
            UUID seller = f.player("Seller", 0);
            UUID poor = f.player("Poor", 100);
            long id = f.ah.list(seller, "Seller", MarketFixture.item("emerald", 1), 101).id();
            assertEquals(AuctionHouse.BuyStatus.OWN, f.ah.buy(id, seller, "Seller").status());
            assertEquals(AuctionHouse.BuyStatus.NOT_ENOUGH, f.ah.buy(id, poor, "Poor").status());
            assertEquals(100, f.bank.balance(poor));
            assertEquals(AuctionHouse.ACTIVE, f.ah.get(id).orElseThrow().state());
            assertEquals(0, f.mailbox.count(poor));
        }
    }

    @Test
    void raceTwoBuyersOneItemExactlyOneWins() throws Exception {
        for (int round = 0; round < 25; round++) {
            try (MarketFixture f = new MarketFixture()) {
                UUID seller = f.player("Seller", 0);
                UUID a = f.player("A", 1_000);
                UUID b = f.player("B", 1_000);
                long id = f.ah.list(seller, "Seller", MarketFixture.item("netherite_ingot", 1), 800).id();
                long before = f.moneyInGame();
                CountDownLatch start = new CountDownLatch(1);
                ExecutorService pool = Executors.newFixedThreadPool(2);
                Future<AuctionHouse.BuyResult> ra = pool.submit(() -> {
                    start.await();
                    return f.ah.buy(id, a, "A");
                });
                Future<AuctionHouse.BuyResult> rb = pool.submit(() -> {
                    start.await();
                    return f.ah.buy(id, b, "B");
                });
                start.countDown();
                AuctionHouse.BuyResult x = ra.get();
                AuctionHouse.BuyResult y = rb.get();
                pool.shutdown();
                int wins = (x.status() == AuctionHouse.BuyStatus.OK ? 1 : 0)
                        + (y.status() == AuctionHouse.BuyStatus.OK ? 1 : 0);
                assertEquals(1, wins, "genau ein Käufer");
                UUID winner = x.status() == AuctionHouse.BuyStatus.OK ? a : b;
                UUID loser = winner.equals(a) ? b : a;
                assertEquals(AuctionHouse.BuyStatus.GONE, (winner.equals(a) ? y : x).status());
                assertEquals(200, f.bank.balance(winner));
                assertEquals(1_000, f.bank.balance(loser), "der andere zahlt nichts");
                assertEquals(784, f.bank.balance(seller));
                assertEquals(1, f.mailbox.count(winner));
                assertEquals(0, f.mailbox.count(loser));
                assertEquals(before - 16, f.moneyInGame(), "nur die Gebühr verschwindet");
            }
        }
    }

    @Test
    void manyBuyersManyListingsStayConsistent() throws Exception {
        try (MarketFixture f = new MarketFixture()) {
            UUID seller = f.player("Seller", 0);
            List<UUID> buyers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                buyers.add(f.player("B" + i, 5_000));
            }
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                ids.add(f.ah.list(seller, "Seller", MarketFixture.item("gold_ingot", 4), 700 + i).id());
            }
            long before = f.moneyInGame();
            ExecutorService pool = Executors.newFixedThreadPool(6);
            List<Future<Integer>> jobs = new ArrayList<>();
            for (UUID b : buyers) {
                jobs.add(pool.submit(() -> {
                    int won = 0;
                    for (long id : ids) {
                        if (f.ah.buy(id, b, "x").status() == AuctionHouse.BuyStatus.OK) {
                            won++;
                        }
                    }
                    return won;
                }));
            }
            int total = 0;
            for (Future<Integer> j : jobs) {
                total += j.get();
            }
            pool.shutdown();
            assertEquals(10, total, "jedes Angebot genau einmal verkauft");
            int mails = 0;
            for (UUID b : buyers) {
                mails += f.mailbox.count(b);
                assertTrue(f.bank.balance(b) >= 0);
            }
            assertEquals(10, mails);
            assertEquals(before - f.fees(), f.moneyInGame());
        }
    }

    @Test
    void limitExpiryAndCancelReturnItems() {
        try (MarketFixture f = new MarketFixture()) {
            UUID seller = f.player("Seller", 0);
            for (int i = 0; i < 10; i++) {
                assertEquals(AuctionHouse.ListStatus.OK,
                        f.ah.list(seller, "Seller", MarketFixture.item("stone", 64), 100).status());
            }
            assertEquals(AuctionHouse.ListStatus.TOO_MANY,
                    f.ah.list(seller, "Seller", MarketFixture.item("stone", 64), 100).status());
            long first = f.ah.activeOf(seller).get(0).id();
            assertTrue(f.ah.cancel(first, seller, false));
            assertFalse(f.ah.cancel(first, seller, false));
            assertEquals(1, f.mailbox.count(seller));
            f.clock.advance(48L * 3600_000 + 1);
            assertEquals(9, f.ah.expire().size());
            assertEquals(10, f.mailbox.count(seller));
            assertEquals(0, f.ah.countActive(seller));
            assertEquals(0, f.ah.count(null, null));
        }
    }

    @Test
    void browseFiltersSortsAndSearchesInGerman() {
        try (MarketFixture f = new MarketFixture()) {
            UUID s = f.player("S", 0);
            f.ah.list(s, "S", MarketFixture.item("diamond_sword", 1), 3_000);
            f.ah.list(s, "S", MarketFixture.item("diamond", 10), 2_000);
            f.ah.list(s, "S", MarketFixture.item("diamond", 1), 250);
            f.ah.list(s, "S", MarketFixture.item("bread", 32), 100);
            assertEquals(3, f.ah.count(null, "Diamant"));
            assertEquals(1, f.ah.count(null, "Diamant Schwert"));
            assertEquals(1, f.ah.count(Category.TOOLS, null));
            List<AuctionHouse.Listing> cheap = f.ah.browse(null, "diamond", AuctionHouse.Sort.CHEAPEST, 0, 10);
            assertEquals(250, cheap.get(0).price());
            List<AuctionHouse.Listing> unit = f.ah.browse(null, "diamond", AuctionHouse.Sort.PER_PIECE, 0, 10);
            assertEquals(2_000, unit.get(0).price()); // 2 ₮ pro Stück
            List<AuctionHouse.Listing> newest = f.ah.browse(null, null, AuctionHouse.Sort.NEWEST, 0, 2);
            assertEquals(2, newest.size());
            assertEquals("minecraft:bread", newest.get(0).item().key());
        }
    }
}
