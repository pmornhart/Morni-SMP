package net.morni.smp.market;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import net.morni.smp.db.Database;

/** Zähler für die Bestenliste in der Stadt (Verkäufe, Käufe, belieferte Aufträge, Einnahmen). */
public final class MarketStats {

    /** Eintrag der Verkäufer-Bestenliste. */
    public record Seller(UUID uuid, String name, long sales, long earned) {
    }

    private final Database db;

    public MarketStats(Database db) {
        this.db = db;
    }

    /** Hochzählen (innerhalb eines laufenden Auftrags). */
    public void add(Connection c, UUID uuid, String name, int sales, int purchases, int ordersFilled, long earned)
            throws SQLException {
        Database.update(c, "INSERT INTO market_stats(uuid, name, sales, purchases, orders_filled, earned) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, "
                + "sales = sales + excluded.sales, purchases = purchases + excluded.purchases, "
                + "orders_filled = orders_filled + excluded.orders_filled, earned = earned + excluded.earned",
                uuid, name == null ? "?" : name, sales, purchases, ordersFilled, earned);
    }

    /** Die meisten Verkäufe (Auktionshaus, Aufträge, Läden). */
    public List<Seller> topSellers(int limit) {
        return db.tx(c -> Database.query(c, "SELECT uuid, name, sales, earned FROM market_stats WHERE sales > 0 "
                + "ORDER BY sales DESC, earned DESC LIMIT ?",
                rs -> new Seller(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getLong(4)),
                limit));
    }

    /** {sales, purchases, orders_filled} eines Spielers. */
    public long[] of(UUID uuid) {
        return db.tx(c -> {
            long[] r = Database.one(c, "SELECT sales, purchases, orders_filled FROM market_stats WHERE uuid = ?",
                    rs -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, uuid);
            return r == null ? new long[3] : r;
        });
    }
}
