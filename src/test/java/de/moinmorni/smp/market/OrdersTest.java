package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OrdersTest {

    @Test
    void escrowPartialDeliveryAndFill() {
        try (MarketFixture f = new MarketFixture()) {
            UUID owner = f.player("Owner", 10_000);
            UUID farmer = f.player("Farmer", 0);
            Orders.CreateResult cr = f.orders.create(owner, "Owner", MarketFixture.item("wheat", 1), 100, 50);
            assertEquals(Orders.CreateStatus.OK, cr.status());
            assertEquals(5_000, f.bank.balance(owner), "100 × 0,50 ₮ liegen in Treuhand");
            assertEquals(5_000, f.orders.escrowTotal());

            Orders.DeliverResult d1 = f.orders.deliver(cr.id(), farmer, "Farmer", 30);
            assertEquals(Orders.DeliverStatus.OK, d1.status());
            assertEquals(30, d1.accepted());
            assertEquals(30, d1.fee());               // 2 % von 15 ₮
            assertEquals(1_470, d1.paid());
            assertEquals(1_470, f.bank.balance(farmer));
            assertEquals(3_500, f.orders.escrowTotal());
            assertEquals(30, f.mailbox.list(owner, 10).get(0).item().amount());

            Orders.DeliverResult d2 = f.orders.deliver(cr.id(), farmer, "Farmer", 500);
            assertEquals(70, d2.accepted(), "nur was noch fehlt");
            assertEquals(Orders.FILLED, d2.order().state());
            assertEquals(0, f.orders.escrowTotal());
            assertEquals(100, f.mailbox.list(owner, 10).stream().mapToInt(m -> m.item().amount()).sum());
            assertEquals(Orders.DeliverStatus.GONE, f.orders.deliver(cr.id(), farmer, "Farmer", 1).status());
            assertEquals(10_000 - f.fees(), f.moneyInGame());
            assertEquals(f.orders.escrowTotal(), f.bank.flow("escrow:order:" + cr.id()));
        }
    }

    @Test
    void cancelAndExpiryRefundTheRest() {
        try (MarketFixture f = new MarketFixture()) {
            UUID owner = f.player("Owner", 10_000);
            UUID other = f.player("Other", 0);
            long id = f.orders.create(owner, "Owner", MarketFixture.item("bone", 1), 64, 10).id();
            f.orders.deliver(id, other, "Other", 4);
            assertEquals(-1, f.orders.cancel(id, other, false), "nur der Auftraggeber");
            assertEquals(600, f.orders.cancel(id, owner, false));
            assertEquals(10_000 - 40, f.bank.balance(owner));
            assertEquals(0, f.orders.escrowTotal());

            long id2 = f.orders.create(owner, "Owner", MarketFixture.item("string", 1), 10, 100).id();
            assertEquals(9_960 - 1_000, f.bank.balance(owner));
            f.clock.advance(7L * 24 * 3600_000 + 1);
            assertEquals(List.of(owner), f.orders.expire());
            assertEquals(9_960, f.bank.balance(owner));
            assertEquals(Orders.EXPIRED, f.orders.get(id2).orElseThrow().state());
        }
    }

    @Test
    void limitsAndMoney() {
        try (MarketFixture f = new MarketFixture()) {
            UUID owner = f.player("Owner", 1_000);
            assertEquals(Orders.CreateStatus.NOT_ENOUGH,
                    f.orders.create(owner, "Owner", MarketFixture.item("dirt", 1), 11, 100).status());
            assertEquals(1_000, f.bank.balance(owner));
            assertEquals(0, f.orders.countActive(owner), "nichts halb angelegt");
            for (int i = 0; i < 5; i++) {
                assertEquals(Orders.CreateStatus.OK,
                        f.orders.create(owner, "Owner", MarketFixture.item("dirt", 1), 1, 1).status());
            }
            assertEquals(Orders.CreateStatus.TOO_MANY,
                    f.orders.create(owner, "Owner", MarketFixture.item("dirt", 1), 1, 1).status());
            assertEquals(Orders.CreateStatus.INVALID,
                    f.orders.create(owner, "Owner", MarketFixture.item("dirt", 1), 0, 1).status());
            assertEquals(Orders.DeliverStatus.OWN,
                    f.orders.deliver(f.orders.activeOf(owner).get(0).id(), owner, "Owner", 1).status());
        }
    }

    @Test
    void concurrentDeliveriesNeverOverfill() throws Exception {
        try (MarketFixture f = new MarketFixture()) {
            UUID owner = f.player("Owner", 100_000);
            long id = f.orders.create(owner, "Owner", MarketFixture.item("cactus", 1), 500, 7).id();
            List<UUID> farmers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                farmers.add(f.player("F" + i, 0));
            }
            long before = f.moneyInGame();
            ExecutorService pool = Executors.newFixedThreadPool(5);
            List<Future<Integer>> jobs = new ArrayList<>();
            for (UUID farmer : farmers) {
                jobs.add(pool.submit(() -> {
                    int got = 0;
                    for (int i = 0; i < 40; i++) {
                        got += f.orders.deliver(id, farmer, "F", 7).accepted();
                    }
                    return got;
                }));
            }
            int accepted = 0;
            for (Future<Integer> j : jobs) {
                accepted += j.get();
            }
            pool.shutdown();
            assertEquals(500, accepted);
            Orders.Order o = f.orders.get(id).orElseThrow();
            assertEquals(500, o.delivered());
            assertEquals(Orders.FILLED, o.state());
            assertEquals(before - f.fees(), f.moneyInGame());
            long paid = farmers.stream().mapToLong(f.bank::balance).sum();
            assertEquals(500 * 7 - f.fees(), paid);
            assertTrue(f.fees() > 0);
        }
    }
}
