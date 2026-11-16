package net.morni.smp.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.morni.smp.TestClock;
import net.morni.smp.db.Database;
import org.junit.jupiter.api.Test;

class BankTest {

    private final TestClock clock = new TestClock();

    @Test
    void newAccountsGetStartBalanceOnce() {
        try (Database db = Database.memory()) {
            Bank bank = new Bank(db, clock, 10_000);
            UUID a = UUID.randomUUID();
            bank.ensure(a, "Anna");
            bank.ensure(a, "Anna");
            assertEquals(10_000, bank.balance(a));
            assertEquals(10_000, bank.flow(a.toString()));
        }
    }

    @Test
    void payMovesExactlyAndRefusesOverdraft() {
        try (Database db = Database.memory()) {
            Bank bank = new Bank(db, clock, 10_000);
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            bank.ensure(a, "Anna");
            bank.ensure(b, "Bert");
            assertEquals(Bank.Result.OK, bank.pay(a, b, 2_550, "pay", null));
            assertEquals(7_450, bank.balance(a));
            assertEquals(12_550, bank.balance(b));
            assertEquals(Bank.Result.NOT_ENOUGH, bank.pay(a, b, 7_451, "pay", null));
            assertEquals(7_450, bank.balance(a));
            assertEquals(Bank.Result.SELF, bank.pay(a, a, 1, "pay", null));
            assertEquals(Bank.Result.INVALID, bank.pay(a, b, 0, "pay", null));
            assertEquals(Bank.Result.INVALID, bank.pay(a, b, -5, "pay", null));
            assertEquals(20_000, bank.total());
        }
    }

    @Test
    void concurrentPaymentsNeverCreateOrLoseMoney() throws Exception {
        try (Database db = Database.memory()) {
            Bank bank = new Bank(db, clock, 1_000);
            List<UUID> people = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                UUID u = UUID.randomUUID();
                bank.ensure(u, "P" + i);
                people.add(u);
            }
            long before = bank.total();
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger refused = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            List<Future<?>> jobs = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                jobs.add(pool.submit(() -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 400; i++) {
                        UUID from = people.get(r.nextInt(people.size()));
                        UUID to = people.get(r.nextInt(people.size()));
                        Bank.Result res = bank.pay(from, to, 1 + r.nextInt(600), "pay", null);
                        if (res == Bank.Result.OK) {
                            ok.incrementAndGet();
                        } else if (res == Bank.Result.NOT_ENOUGH) {
                            refused.incrementAndGet();
                        }
                    }
                }));
            }
            for (Future<?> f : jobs) {
                f.get();
            }
            pool.shutdown();
            assertEquals(before, bank.total(), "Geldmenge bleibt gleich");
            for (UUID u : people) {
                long bal = bank.balance(u);
                assertTrue(bal >= 0, "nie negativ");
                assertEquals(bal, bank.flow(u.toString()), "Kassenbuch passt zum Kontostand");
            }
            assertTrue(ok.get() > 100, "es wurde wirklich überwiesen");
            assertTrue(refused.get() > 0, "Überziehen wurde abgelehnt");
        }
    }

    @Test
    void depositWithdrawAndSet() {
        try (Database db = Database.memory()) {
            Bank bank = new Bank(db, clock, 0);
            UUID a = UUID.randomUUID();
            bank.ensure(a, "Anna");
            assertEquals(Bank.Result.OK, bank.deposit(a, 500, "admin", null));
            assertEquals(Bank.Result.NOT_ENOUGH, bank.withdraw(a, 501, "admin", null));
            assertEquals(Bank.Result.OK, bank.withdraw(a, 200, "admin", null));
            assertEquals(300, bank.balance(a));
            assertEquals(Bank.Result.OK, bank.set(a, 1_000, "admin"));
            assertEquals(1_000, bank.balance(a));
            assertEquals(Bank.Result.OK, bank.set(a, 0, "admin"));
            assertEquals(0, bank.balance(a));
            assertEquals(a, bank.byName("anna"));
        }
    }
}
