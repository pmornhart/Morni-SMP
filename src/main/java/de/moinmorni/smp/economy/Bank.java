package de.moinmorni.smp.economy;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Database.Abort;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Konten und Überweisungen. Jede Bewegung steht genau einmal im Kassenbuch ({@code ledger}): von wem ({@code src})
 * an wen ({@code dst}). Beteiligte sind Spieler (UUID), der Server ({@link #SERVER}: Geldquelle oder -senke) oder ein
 * Treuhandkonto ({@code escrow:…}: Geld, das z. B. in einem Auftrag festliegt und dort mitgezählt wird).
 *
 * <p>Abbuchen geht nur, wenn genug da ist ({@code WHERE balance >= ?}, dazu {@code CHECK (balance >= 0)} in der
 * Tabelle) – auch wenn zwei Überweisungen gleichzeitig kommen, wird kein Konto negativ.
 */
public final class Bank {

    /** Der Server als Quelle (Belohnung, Ankauf) oder Senke (Gebühren, Grundstücke, Miete). */
    public static final String SERVER = "server";
    /** Vorsilbe der Treuhandkonten (Aufträge, Kopfgeld). */
    public static final String ESCROW = "escrow:";

    /** Ergebnis einer Geldbewegung. */
    public enum Result {
        OK, NOT_ENOUGH, INVALID, SELF
    }

    /** Eintrag der Reichsten-Liste. */
    public record Rich(UUID uuid, String name, long balance) {
    }

    private final Database db;
    private final LongSupplier clock;
    private volatile long startBalance;

    public Bank(Database db, LongSupplier clock, long startBalance) {
        this.db = db;
        this.clock = clock;
        this.startBalance = Math.max(0, startBalance);
    }

    public Database db() {
        return db;
    }

    public long now() {
        return clock.getAsLong();
    }

    public void startBalance(long cents) {
        this.startBalance = Math.max(0, cents);
    }

    /** Konto anlegen (mit Startguthaben), falls es noch keins gibt; den Namen immer aktuell halten. */
    public void ensure(UUID uuid, String name) {
        db.run(c -> ensure(c, uuid, name));
    }

    /** Wie {@link #ensure(UUID, String)}, innerhalb eines laufenden Auftrags. */
    public void ensure(Connection c, UUID uuid, String name) throws SQLException {
        String n = name == null ? "?" : name;
        int made = Database.update(c,
                "INSERT OR IGNORE INTO accounts(uuid, name, balance, created) VALUES (?, ?, 0, ?)", uuid, n, now());
        if (made == 1) {
            if (startBalance > 0) {
                move(c, SERVER, uuid.toString(), startBalance, "start", null);
            }
        } else {
            Database.update(c, "UPDATE accounts SET name = ? WHERE uuid = ? AND name <> ?", n, uuid, n);
        }
    }

    public boolean exists(UUID uuid) {
        return db.tx(c -> Database.number(c, "SELECT 1 FROM accounts WHERE uuid = ?", 0, uuid) == 1);
    }

    public long balance(UUID uuid) {
        return db.tx(c -> balance(c, uuid));
    }

    public static long balance(Connection c, UUID uuid) throws SQLException {
        return Database.number(c, "SELECT balance FROM accounts WHERE uuid = ?", 0, uuid);
    }

    /** Name zu einer UUID (aus dem Konto), oder null. */
    public String name(UUID uuid) {
        return db.tx(c -> Database.one(c, "SELECT name FROM accounts WHERE uuid = ?", rs -> rs.getString(1), uuid));
    }

    /** UUID zu einem Namen (ohne Groß/Klein), oder null – für Spieler, die gerade nicht online sind. */
    public UUID byName(String name) {
        return db.tx(c -> Database.one(c,
                "SELECT uuid FROM accounts WHERE name = ? COLLATE NOCASE ORDER BY created DESC LIMIT 1",
                rs -> UUID.fromString(rs.getString(1)), name));
    }

    /** Überweisung zwischen zwei Spielern. */
    public Result pay(UUID from, UUID to, long amount, String reason, String ref) {
        if (amount <= 0 || amount > Money.MAX) {
            return Result.INVALID;
        }
        if (from.equals(to)) {
            return Result.SELF;
        }
        return attempt(() -> db.run(c -> move(c, from.toString(), to.toString(), amount, reason, ref)));
    }

    /** Geld vom Server (Belohnung, Ankauf, Admin). */
    public Result deposit(UUID to, long amount, String reason, String ref) {
        if (amount <= 0 || amount > Money.MAX) {
            return Result.INVALID;
        }
        return attempt(() -> db.run(c -> move(c, SERVER, to.toString(), amount, reason, ref)));
    }

    /** Geld an den Server (Gebühren, Grundstücke, Admin). */
    public Result withdraw(UUID from, long amount, String reason, String ref) {
        if (amount <= 0 || amount > Money.MAX) {
            return Result.INVALID;
        }
        return attempt(() -> db.run(c -> move(c, from.toString(), SERVER, amount, reason, ref)));
    }

    /** Kontostand fest setzen (Admin); die Differenz läuft über den Server. */
    public Result set(UUID who, long amount, String reason) {
        if (amount < 0 || amount > Money.MAX) {
            return Result.INVALID;
        }
        return attempt(() -> db.run(c -> {
            long now = balance(c, who);
            if (amount > now) {
                move(c, SERVER, who.toString(), amount - now, reason, null);
            } else if (amount < now) {
                move(c, who.toString(), SERVER, now - amount, reason, null);
            }
        }));
    }

    /** Die Reichsten. */
    public List<Rich> top(int limit) {
        return db.tx(c -> Database.query(c,
                "SELECT uuid, name, balance FROM accounts ORDER BY balance DESC, name LIMIT ?",
                rs -> new Rich(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)), limit));
    }

    /** Alles Geld auf allen Spielerkonten (für Prüfungen). */
    public long total() {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(balance), 0) FROM accounts", 0));
    }

    /** Summe aller Bewegungen von/zu einem Konto im Kassenbuch (für Prüfungen der Treuhand). */
    public long flow(String account) {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(amount), 0) FROM ledger WHERE dst = ?", 0, account)
                - Database.number(c, "SELECT COALESCE(SUM(amount), 0) FROM ledger WHERE src = ?", 0, account));
    }

    // ---------- Baustein für andere Dienste (innerhalb eines Auftrags) ----------

    /**
     * Geld bewegen: Spielerkonten werden belastet bzw. gutgeschrieben, Server und Treuhand stehen nur im Kassenbuch.
     * Zu wenig Geld → {@link Abort} mit {@link Result#NOT_ENOUGH}: der ganze Auftrag wird zurückgerollt.
     */
    public void move(Connection c, String src, String dst, long amount, String reason, String ref) throws SQLException {
        if (amount <= 0 || amount > Money.MAX || src.equals(dst)) {
            throw new Abort(Result.INVALID);
        }
        UUID from = player(src);
        if (from != null) {
            int n = Database.update(c, "UPDATE accounts SET balance = balance - ? WHERE uuid = ? AND balance >= ?",
                    amount, from, amount);
            if (n != 1) {
                throw new Abort(Result.NOT_ENOUGH);
            }
        }
        UUID to = player(dst);
        if (to != null) {
            int n = Database.update(c, "UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
            if (n != 1) {
                Database.update(c, "INSERT INTO accounts(uuid, name, balance, created) VALUES (?, '?', ?, ?)",
                        to, amount, now());
            }
        }
        Database.update(c, "INSERT INTO ledger(time, src, dst, amount, reason, ref) VALUES (?, ?, ?, ?, ?, ?)",
                now(), src, dst, amount, reason, ref);
    }

    /** UUID, wenn das Konto ein Spieler ist; sonst null (Server, Treuhand). */
    static UUID player(String account) {
        if (account.equals(SERVER) || account.startsWith(ESCROW) || account.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(account);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Läuft {@code r}; ein fachlicher Abbruch wird zum Ergebnis. */
    public static Result attempt(Runnable r) {
        try {
            r.run();
            return Result.OK;
        } catch (Abort a) {
            if (a.reason() instanceof Result res) {
                return res;
            }
            throw a;
        }
    }
}
