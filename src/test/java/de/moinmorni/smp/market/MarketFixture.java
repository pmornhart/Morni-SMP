package de.moinmorni.smp.market;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Alles, was die Markt-Tests brauchen, auf einer Datenbank im Speicher. */
final class MarketFixture implements AutoCloseable {

    final TestClock clock = new TestClock();
    final Database db = Database.memory();
    final Bank bank = new Bank(db, clock, 0);
    final Mailbox mailbox = new Mailbox(db, clock);
    final Notices notices = new Notices(db, clock);
    final PriceHistory prices = new PriceHistory(db, clock);
    final MarketStats stats = new MarketStats(db);
    final AuctionHouse ah = new AuctionHouse(db, bank, mailbox, notices, prices, stats, clock, 200, 48L * 3600_000, 10);
    final Orders orders = new Orders(db, bank, mailbox, notices, prices, stats, clock, 200, 7L * 24 * 3600_000, 5);

    UUID player(String name, long cents) {
        UUID u = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        bank.ensure(u, name);
        if (cents > 0) {
            bank.deposit(u, cents, "test", null);
        }
        return u;
    }

    static ItemData item(String key, int amount) {
        return new ItemData(("blob:" + key).getBytes(StandardCharsets.UTF_8), "minecraft:" + key, key.replace('_', ' '),
                Category.of(key, false, false).id(), amount);
    }

    /** Geld, das irgendwo im Spiel ist: Konten + Treuhand der Aufträge. */
    long moneyInGame() {
        return bank.total() + orders.escrowTotal();
    }

    /** Was der Server über Gebühren eingenommen hat. */
    long fees() {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(amount), 0) FROM ledger WHERE dst = 'server'", 0));
    }

    @Override
    public void close() {
        db.close();
    }
}
