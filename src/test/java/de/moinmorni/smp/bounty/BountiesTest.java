package de.moinmorni.smp.bounty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BountiesTest {

    @Test
    void escrowUntilSomeoneWinsTheFight() {
        try (Database db = Database.memory()) {
            TestClock clock = new TestClock();
            Bank bank = new Bank(db, clock, 10_000);
            Bounties bounties = new Bounties(db, bank, clock);
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UUID target = UUID.randomUUID();
            bank.ensure(a, "A");
            bank.ensure(b, "B");
            bank.ensure(target, "T");
            long before = bank.total();
            assertEquals(Bounties.Status.OK, bounties.place(a, target, "T", 2_000, 1_000));
            assertEquals(Bounties.Status.OK, bounties.place(b, target, "T", 1_500, 1_000));
            assertEquals(Bounties.Status.TOO_LOW, bounties.place(b, target, "T", 999, 1_000));
            assertEquals(Bounties.Status.SELF, bounties.place(target, target, "T", 5_000, 1_000));
            assertEquals(Bounties.Status.NOT_ENOUGH, bounties.place(a, target, "T", 9_000, 1_000));
            assertEquals(3_500, bounties.of(target));
            assertEquals(before, bank.total() + bounties.total());
            assertEquals(0, bounties.claim(target, target), "nicht selbst kassieren");
            assertEquals(3_500, bounties.claim(target, b));
            assertEquals(10_000 - 1_500 + 3_500, bank.balance(b));
            assertEquals(0, bounties.claim(target, a), "nur einmal");
            assertEquals(before, bank.total());
        }
    }
}
