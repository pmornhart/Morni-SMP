package de.moinmorni.smp.bounty;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Database.Abort;
import de.moinmorni.smp.economy.Bank;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Kopfgeld: Wer eins aussetzt, zahlt sofort in die Treuhand ({@code escrow:bounty:<ziel>}). Wer das Ziel im Kampf
 * besiegt, bekommt alles. Man kann kein Kopfgeld auf sich selbst aussetzen und es nicht selbst kassieren.
 */
public final class Bounties {

    public record Bounty(UUID target, String targetName, long amount) {
    }

    public enum Status {
        OK, SELF, TOO_LOW, NOT_ENOUGH
    }

    private final Database db;
    private final Bank bank;
    private final LongSupplier clock;

    public Bounties(Database db, Bank bank, LongSupplier clock) {
        this.db = db;
        this.bank = bank;
        this.clock = clock;
    }

    static String escrow(UUID target) {
        return Bank.ESCROW + "bounty:" + target;
    }

    public Status place(UUID from, UUID target, String targetName, long amount, long minimum) {
        if (from.equals(target)) {
            return Status.SELF;
        }
        if (amount < minimum || amount <= 0) {
            return Status.TOO_LOW;
        }
        try {
            db.run(c -> {
                bank.move(c, from.toString(), escrow(target), amount, "kopfgeld", target.toString());
                Database.update(c, "INSERT INTO bounties(target, target_name, amount, updated) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(target) DO UPDATE SET amount = amount + excluded.amount, "
                        + "target_name = excluded.target_name, updated = excluded.updated",
                        target, targetName, amount, clock.getAsLong());
            });
            return Status.OK;
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return Status.NOT_ENOUGH;
            }
            throw a;
        }
    }

    /** Ziel wurde besiegt: Kopfgeld an den Sieger. Liefert den Betrag (0 = keins). */
    public long claim(UUID target, UUID killer) {
        if (killer == null || killer.equals(target)) {
            return 0;
        }
        return db.tx(c -> {
            long amount = Database.number(c, "SELECT amount FROM bounties WHERE target = ?", 0, target);
            if (amount <= 0) {
                return 0L;
            }
            Database.update(c, "DELETE FROM bounties WHERE target = ?", target);
            bank.move(c, escrow(target), killer.toString(), amount, "kopfgeld_beute", target.toString());
            return amount;
        });
    }

    public long of(UUID target) {
        return db.tx(c -> Database.number(c, "SELECT amount FROM bounties WHERE target = ?", 0, target));
    }

    public List<Bounty> top(int limit) {
        return db.tx(c -> Database.query(c, "SELECT target, target_name, amount FROM bounties ORDER BY amount DESC LIMIT ?",
                rs -> new Bounty(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)), limit));
    }

    /** Summe aller Kopfgelder (für Prüfungen). */
    public long total() {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(amount), 0) FROM bounties", 0));
    }
}
