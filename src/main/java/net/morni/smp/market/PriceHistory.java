package net.morni.smp.market;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import net.morni.smp.db.Database;

/**
 * Marktpreis: Mittelwert (Median) der Stückpreise der letzten Verkäufe einer Sorte – Auktionshaus, Aufträge und
 * Läden zählen. Der Median lässt sich von einem einzelnen Fantasiepreis nicht verschieben.
 */
public final class PriceHistory {

    /** So viele letzte Verkäufe zählen. */
    static final int WINDOW = 50;
    /** Älter als 30 Tage zählt nicht mehr. */
    static final long MAX_AGE_MS = 30L * 24 * 3600 * 1000;

    private final Database db;
    private final LongSupplier clock;

    public PriceHistory(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Verkauf merken: {@code total} Hundertstel für {@code amount} Stück. */
    public void record(Connection c, String key, long total, int amount, String source) throws SQLException {
        if (amount <= 0 || total <= 0) {
            return;
        }
        long unit = Math.max(1, Math.round((double) total / amount));
        Database.update(c, "INSERT INTO sales(item_key, unit_price, amount, time, source) VALUES (?, ?, ?, ?, ?)",
                key, unit, amount, clock.getAsLong(), source);
    }

    /** Marktpreis pro Stück, oder leer, wenn die Sorte noch nie gehandelt wurde. */
    public OptionalLong median(String key) {
        List<Long> prices = db.tx(c -> Database.query(c,
                "SELECT unit_price FROM sales WHERE item_key = ? AND time >= ? ORDER BY time DESC LIMIT ?",
                rs -> rs.getLong(1), key, clock.getAsLong() - MAX_AGE_MS, WINDOW));
        return median(prices);
    }

    /** Median einer Liste (bei gerader Anzahl der Mittelwert der beiden mittleren, abgerundet). */
    public static OptionalLong median(List<Long> values) {
        if (values.isEmpty()) {
            return OptionalLong.empty();
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return OptionalLong.of(sorted.get(n / 2));
        }
        return OptionalLong.of((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2);
    }
}
