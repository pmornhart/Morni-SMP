package net.morni.smp.market;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.morni.smp.db.Database;

/**
 * Nachrichten für später: Wer offline ist, erfährt beim nächsten Betreten, was passiert ist („Dein Diamant wurde
 * verkauft“). Ist er online, holt das Plugin sie sofort ab.
 */
public final class Notices {

    /** Eine Nachricht: Text-Schlüssel und Werte für die Platzhalter. */
    public record Notice(long id, String key, Map<String, String> args) {
    }

    private static final char FIELD = '\u001f';
    private static final char RECORD = '\u001e';

    private final Database db;
    private final LongSupplier clock;

    public Notices(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Ablegen (innerhalb eines laufenden Auftrags). {@code args}: abwechselnd Name, Wert. */
    public void add(Connection c, UUID owner, String key, String... args) throws SQLException {
        Database.update(c, "INSERT INTO notices(owner, key, args, created) VALUES (?, ?, ?, ?)", owner, key,
                encode(args), clock.getAsLong());
    }

    public void add(UUID owner, String key, String... args) {
        db.run(c -> add(c, owner, key, args));
    }

    /** Alle abholen (und löschen). */
    public List<Notice> takeAll(UUID owner) {
        return db.tx(c -> {
            List<Notice> list = Database.query(c, "SELECT id, key, args FROM notices WHERE owner = ? ORDER BY id",
                    rs -> new Notice(rs.getLong(1), rs.getString(2), decode(rs.getString(3))), owner);
            if (!list.isEmpty()) {
                Database.update(c, "DELETE FROM notices WHERE owner = ?", owner);
            }
            return list;
        });
    }

    static String encode(String... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (sb.length() > 0) {
                sb.append(RECORD);
            }
            sb.append(clean(args[i])).append(FIELD).append(clean(args[i + 1]));
        }
        return sb.toString();
    }

    static Map<String, String> decode(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String rec : s.split(String.valueOf(RECORD))) {
            int f = rec.indexOf(FIELD);
            if (f > 0) {
                out.put(rec.substring(0, f), rec.substring(f + 1));
            }
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace(FIELD, ' ').replace(RECORD, ' ');
    }
}
