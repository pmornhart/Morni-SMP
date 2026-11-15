package de.moinmorni.smp.market;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Database.Abort;
import de.moinmorni.smp.economy.Bank;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Marktstände auf dem Marktplatz: wochenweise mieten (Miete geht an die Stadt), darin eigene Ladenkisten aufstellen.
 * Einen Tag vor Ablauf kommt eine Warnung; ist die Zeit um, räumt das Plugin den Stand (alles ins Abholfach) und gibt
 * ihn frei. Verlängern geht jederzeit, höchstens {@code maxWeeksAhead} Wochen im Voraus.
 */
public final class Stalls {

    /** Ein Stand. {@code tenant} null = frei. */
    public record Stall(int id, UUID tenant, String tenantName, long paidUntil, boolean warned) {
        public boolean free() {
            return tenant == null;
        }
    }

    /** Zustand eines Stands zu einem Zeitpunkt. */
    public enum State {
        FREE, ACTIVE, WARN, EXPIRED
    }

    public enum RentStatus {
        OK, EXTENDED, TAKEN, HAS_OTHER, NOT_ENOUGH, TOO_FAR_AHEAD, UNKNOWN, NOT_TENANT
    }

    public record RentResult(RentStatus status, Stall stall) {
    }

    public static final long WEEK_MS = 7L * 24 * 3600 * 1000;

    private final Database db;
    private final Bank bank;
    private final LongSupplier clock;
    private volatile long rent;
    private volatile long periodMs = WEEK_MS;
    private volatile long warnMs = 24L * 3600 * 1000;
    private volatile int maxWeeksAhead = 4;

    public Stalls(Database db, Bank bank, LongSupplier clock, long rent) {
        this.db = db;
        this.bank = bank;
        this.clock = clock;
        this.rent = rent;
    }

    public void configure(long rentCents, long period, long warn, int weeksAhead) {
        this.rent = Math.max(1, rentCents);
        this.periodMs = Math.max(60_000, period);
        this.warnMs = Math.max(0, warn);
        this.maxWeeksAhead = Math.max(1, weeksAhead);
    }

    public long rent() {
        return rent;
    }

    public long periodMs() {
        return periodMs;
    }

    /** Zustand – rein rechnerisch, ohne Datenbank. */
    public static State state(Stall s, long now, long warnMs) {
        if (s.free()) {
            return State.FREE;
        }
        if (s.paidUntil() <= now) {
            return State.EXPIRED;
        }
        return s.paidUntil() - now <= warnMs ? State.WARN : State.ACTIVE;
    }

    /** Stände 1…n anlegen (vorhandene bleiben). */
    public void ensure(int count) {
        db.run(c -> {
            for (int i = 1; i <= count; i++) {
                Database.update(c, "INSERT OR IGNORE INTO stalls(id) VALUES (?)", i);
            }
        });
    }

    public List<Stall> all() {
        return db.tx(c -> Database.query(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls ORDER BY id",
                Stalls::row));
    }

    public Optional<Stall> get(int id) {
        return Optional.ofNullable(db.tx(c -> Database.one(c,
                "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls WHERE id = ?", Stalls::row, id)));
    }

    public Optional<Stall> of(UUID tenant) {
        return Optional.ofNullable(db.tx(c -> Database.one(c,
                "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls WHERE tenant = ?", Stalls::row,
                tenant)));
    }

    /** Freien Stand mieten (eine Woche) – oder den eigenen verlängern. */
    public RentResult rent(int id, UUID who, String name) {
        try {
            return db.tx(c -> {
                Stall s = Database.one(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls WHERE id = ?",
                        Stalls::row, id);
                long now = clock.getAsLong();
                if (s == null) {
                    return new RentResult(RentStatus.UNKNOWN, null);
                }
                if (!s.free() && s.paidUntil() > now) {
                    if (!s.tenant().equals(who)) {
                        return new RentResult(RentStatus.TAKEN, s);
                    }
                    return extend(c, s, now);
                }
                if (!s.free()) {
                    // abgelaufen, aber noch nicht geräumt – erst räumen lassen
                    return new RentResult(RentStatus.TAKEN, s);
                }
                Stall other = Database.one(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls "
                        + "WHERE tenant = ?", Stalls::row, who);
                if (other != null) {
                    return new RentResult(RentStatus.HAS_OTHER, other);
                }
                bank.move(c, who.toString(), Bank.SERVER, rent, "stand_miete", "stall:" + id);
                Database.update(c, "UPDATE stalls SET tenant = ?, tenant_name = ?, paid_until = ?, warned = 0 "
                        + "WHERE id = ? AND tenant IS NULL", who, name, now + periodMs, id);
                return new RentResult(RentStatus.OK, new Stall(id, who, name, now + periodMs, false));
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new RentResult(RentStatus.NOT_ENOUGH, get(id).orElse(null));
            }
            throw a;
        }
    }

    /** Eigenen Stand um eine Woche verlängern. */
    public RentResult extend(int id, UUID who) {
        try {
            return db.tx(c -> {
                Stall s = Database.one(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls WHERE id = ?",
                        Stalls::row, id);
                long now = clock.getAsLong();
                if (s == null || s.free() || !s.tenant().equals(who) || s.paidUntil() <= now) {
                    return new RentResult(RentStatus.NOT_TENANT, s);
                }
                return extend(c, s, now);
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new RentResult(RentStatus.NOT_ENOUGH, get(id).orElse(null));
            }
            throw a;
        }
    }

    private RentResult extend(java.sql.Connection c, Stall s, long now) throws SQLException {
        long next = s.paidUntil() + periodMs;
        if (next - now > maxWeeksAhead * periodMs) {
            return new RentResult(RentStatus.TOO_FAR_AHEAD, s);
        }
        bank.move(c, s.tenant().toString(), Bank.SERVER, rent, "stand_miete", "stall:" + s.id());
        Database.update(c, "UPDATE stalls SET paid_until = ?, warned = 0 WHERE id = ?", next, s.id());
        return new RentResult(RentStatus.EXTENDED, new Stall(s.id(), s.tenant(), s.tenantName(), next, false));
    }

    /** Stände, die bald ablaufen und noch nicht gewarnt wurden – werden dabei als gewarnt markiert. */
    public List<Stall> warnDue() {
        return db.tx(c -> {
            long now = clock.getAsLong();
            List<Stall> due = Database.query(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls "
                    + "WHERE tenant IS NOT NULL AND warned = 0 AND paid_until > ? AND paid_until <= ?", Stalls::row,
                    now, now + warnMs);
            for (Stall s : due) {
                Database.update(c, "UPDATE stalls SET warned = 1 WHERE id = ?", s.id());
            }
            return due;
        });
    }

    /** Abgelaufene, noch besetzte Stände (das Plugin räumt sie und ruft dann {@link #free}). */
    public List<Stall> expired() {
        return db.tx(c -> Database.query(c, "SELECT id, tenant, tenant_name, paid_until, warned FROM stalls "
                + "WHERE tenant IS NOT NULL AND paid_until <= ?", Stalls::row, clock.getAsLong()));
    }

    /** Stand freigeben (nach dem Räumen). */
    public void free(int id) {
        db.run(c -> Database.update(c, "UPDATE stalls SET tenant = NULL, tenant_name = NULL, paid_until = 0, "
                + "warned = 0 WHERE id = ?", id));
    }

    private static Stall row(ResultSet rs) throws SQLException {
        String t = rs.getString(2);
        return new Stall(rs.getInt(1), t == null ? null : UUID.fromString(t), rs.getString(3), rs.getLong(4),
                rs.getInt(5) != 0);
    }
}
