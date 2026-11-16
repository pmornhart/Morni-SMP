package net.morni.smp.market;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.morni.smp.db.Database;

/**
 * Das Abholfach: Gegenstände, die gerade nicht ins Inventar passen oder während man weg war ankamen (gekauft,
 * Auftrag beliefert, abgelaufene Angebote, geräumter Marktstand). Abholen löscht die Zeile erst und gibt dann – so
 * kann nichts doppelt herauskommen.
 */
public final class Mailbox {

    /** Ein Eintrag im Abholfach. */
    public record Mail(long id, UUID owner, ItemData item, String reason, long created) {
    }

    private final Database db;
    private final LongSupplier clock;

    public Mailbox(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Hineinlegen (innerhalb eines laufenden Auftrags). */
    public void add(Connection c, UUID owner, ItemData item, String reason) throws SQLException {
        Database.update(c, "INSERT INTO mailbox(owner, item, item_key, item_name, amount, reason, created) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", owner, item.blob(), item.key(), item.name(), item.amount(), reason,
                clock.getAsLong());
    }

    /** Hineinlegen (eigener Auftrag). */
    public void add(UUID owner, ItemData item, String reason) {
        db.run(c -> add(c, owner, item, reason));
    }

    public List<Mail> list(UUID owner, int limit) {
        return db.tx(c -> Database.query(c,
                "SELECT id, owner, item, item_key, item_name, amount, reason, created FROM mailbox WHERE owner = ? "
                        + "ORDER BY id LIMIT ?", Mailbox::row, owner, limit));
    }

    public int count(UUID owner) {
        return db.<Long>tx(c -> Database.number(c, "SELECT COUNT(*) FROM mailbox WHERE owner = ?", 0, owner)).intValue();
    }

    /** Herausnehmen: Zeile wird gelöscht und zurückgegeben (null, wenn es sie nicht mehr gibt). */
    public Mail take(long id, UUID owner) {
        return db.tx(c -> {
            Mail m = Database.one(c, "SELECT id, owner, item, item_key, item_name, amount, reason, created FROM mailbox "
                    + "WHERE id = ? AND owner = ?", Mailbox::row, id, owner);
            if (m == null) {
                return null;
            }
            Database.update(c, "DELETE FROM mailbox WHERE id = ?", id);
            return m;
        });
    }

    private static Mail row(ResultSet rs) throws SQLException {
        return new Mail(rs.getLong(1), UUID.fromString(rs.getString(2)),
                new ItemData(rs.getBytes(3), rs.getString(4), rs.getString(5), null, rs.getInt(6)),
                rs.getString(7), rs.getLong(8));
    }
}
