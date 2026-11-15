package de.moinmorni.smp.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StallsTest {

    private static final long DAY = 24L * 3600_000;

    @Test
    void rentExtendWarnExpire() {
        try (MarketFixture f = new MarketFixture()) {
            Stalls stalls = new Stalls(f.db, f.bank, f.clock, 0);
            stalls.configure(30_000, 7 * DAY, DAY, 4);
            stalls.ensure(16);
            assertEquals(16, stalls.all().size());
            UUID a = f.player("A", 200_000);
            UUID b = f.player("B", 200_000);
            assertEquals(Stalls.RentStatus.OK, stalls.rent(3, a, "A").status());
            assertEquals(170_000, f.bank.balance(a));
            assertEquals(Stalls.RentStatus.TAKEN, stalls.rent(3, b, "B").status());
            assertEquals(Stalls.RentStatus.HAS_OTHER, stalls.rent(4, a, "A").status());
            assertEquals(Stalls.RentStatus.EXTENDED, stalls.rent(3, a, "A").status(), "eigener Stand: verlängern");
            assertEquals(Stalls.RentStatus.EXTENDED, stalls.extend(3, a).status());
            assertEquals(Stalls.RentStatus.EXTENDED, stalls.extend(3, a).status());
            assertEquals(Stalls.RentStatus.TOO_FAR_AHEAD, stalls.extend(3, a).status(), "höchstens 4 Wochen im Voraus");
            assertEquals(80_000, f.bank.balance(a));

            Stalls.Stall s = stalls.get(3).orElseThrow();
            assertEquals(Stalls.State.ACTIVE, Stalls.state(s, f.clock.getAsLong(), DAY));
            f.clock.advance(27 * DAY + 1);
            assertEquals(Stalls.State.WARN, Stalls.state(stalls.get(3).orElseThrow(), f.clock.getAsLong(), DAY));
            assertEquals(1, stalls.warnDue().size());
            assertEquals(0, stalls.warnDue().size(), "nur einmal warnen");
            f.clock.advance(DAY);
            assertEquals(Stalls.State.EXPIRED, Stalls.state(stalls.get(3).orElseThrow(), f.clock.getAsLong(), DAY));
            assertEquals(1, stalls.expired().size());
            assertEquals(Stalls.RentStatus.TAKEN, stalls.rent(3, b, "B").status(), "erst räumen, dann neu vermieten");
            stalls.free(3);
            assertTrue(stalls.get(3).orElseThrow().free());
            assertEquals(Stalls.RentStatus.OK, stalls.rent(3, b, "B").status());
        }
    }

    @Test
    void rentNeedsMoney() {
        try (MarketFixture f = new MarketFixture()) {
            Stalls stalls = new Stalls(f.db, f.bank, f.clock, 30_000);
            stalls.ensure(2);
            UUID poor = f.player("Poor", 29_999);
            assertEquals(Stalls.RentStatus.NOT_ENOUGH, stalls.rent(1, poor, "Poor").status());
            assertTrue(stalls.get(1).orElseThrow().free());
            assertEquals(29_999, f.bank.balance(poor));
        }
    }
}
