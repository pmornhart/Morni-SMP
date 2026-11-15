package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopsTest {

    @Test
    void sellAndBuyMoveMoneyTheRightWay() {
        try (MarketFixture f = new MarketFixture()) {
            Shops shops = new Shops(f.db, f.bank, f.notices, f.prices, f.stats, f.clock, 0);
            UUID owner = f.player("Owner", 1_000);
            UUID customer = f.player("Customer", 1_000);
            Shops.Shop sell = shops.create("world", 1, 64, 1, 1, 65, 1, owner, "Owner",
                    MarketFixture.item("oak_log", 16), Shops.Mode.SELL, 300, 0);
            Shops.Shop buy = shops.create("world", 3, 64, 1, 3, 65, 1, owner, "Owner",
                    MarketFixture.item("cobblestone", 64), Shops.Mode.BUY, 50, 0);
            assertTrue(shops.isContainer("world", 1, 64, 1));
            assertTrue(shops.isSign("world", 3, 65, 1));
            assertEquals(16, shops.bySign("world", 1, 65, 1).orElseThrow().item().amount());

            assertEquals(Shops.TradeStatus.OK, shops.trade(sell.id(), customer, "Customer").status());
            assertEquals(700, f.bank.balance(customer));
            assertEquals(1_300, f.bank.balance(owner));
            assertEquals(Shops.TradeStatus.OK, shops.trade(buy.id(), customer, "Customer").status());
            assertEquals(750, f.bank.balance(customer));
            assertEquals(1_250, f.bank.balance(owner));
            assertEquals(Shops.TradeStatus.OWN, shops.trade(sell.id(), owner, "Owner").status());
            assertEquals(2, f.notices.takeAll(owner).size());

            UUID broke = f.player("Broke", 100);
            assertEquals(Shops.TradeStatus.NOT_ENOUGH, shops.trade(sell.id(), broke, "Broke").status());
            assertEquals(100, f.bank.balance(broke));
            shops.delete(sell.id());
            assertEquals(Shops.TradeStatus.GONE, shops.trade(sell.id(), customer, "Customer").status());
            assertEquals(false, shops.isContainer("world", 1, 64, 1));
        }
    }
}
