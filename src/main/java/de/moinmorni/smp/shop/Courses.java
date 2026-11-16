package de.moinmorni.smp.shop;

import de.moinmorni.smp.db.Database;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Die Börse: Druck und Kurs jeder Ware ({@link Course}) und ihr Verlauf. Gespeichert wird der Druck nach jedem Handel
 * (Tabelle {@code courses}) und je Ware und Stunde ein Eintrag mit Druck und Menge (Tabelle {@code course_log}) – daraus
 * entsteht der Verlauf. Gelesen wird aus dem Speicher (schnell für Menüs und Tafel), geschrieben im selben Auftrag wie
 * der Handel.
 */
public final class Courses {

    /** Druck und wann er zuletzt gesetzt wurde. */
    public record State(double pressure, long updated) {
    }

    /** Ein Punkt im Verlauf: Zeit (ms) und Kurs. */
    public record Point(long time, double factor) {
    }

    /** Eine Ware mit Kurs (für Steiger und Fallende). */
    public record Mover(String key, double factor) {
    }

    /** Gehandelte Stück in einem Zeitraum. */
    public record Volume(long bought, long sold) {
    }

    private static final long HOUR = 3_600_000L;
    /** Verlauf so lange aufheben. */
    static final long KEEP_MS = 30L * 24 * HOUR;

    private final Database db;
    private final LongSupplier clock;
    private final Map<String, State> cache = new ConcurrentHashMap<>();
    private volatile Course.Rules rules = Course.Rules.DEFAULT;

    public Courses(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
        reload();
    }

    public void configure(Course.Rules r) {
        this.rules = r == null ? Course.Rules.OFF : r;
    }

    public Course.Rules rules() {
        return rules;
    }

    /** Alles neu aus der Datenbank (nach einem Fehler oder beim Start). */
    public void reload() {
        Map<String, State> fresh = new ConcurrentHashMap<>();
        db.run(c -> {
            for (Object[] r : Database.query(c, "SELECT item_key, pressure, updated FROM courses",
                    rs -> new Object[] {rs.getString(1), rs.getDouble(2), rs.getLong(3)})) {
                fresh.put((String) r[0], new State((Double) r[1], (Long) r[2]));
            }
        });
        cache.clear();
        cache.putAll(fresh);
    }

    /** Druck jetzt (schon erholt). */
    public double pressure(String key) {
        State s = cache.get(key);
        return s == null ? 0 : Course.decay(s.pressure(), clock.getAsLong() - s.updated(), rules);
    }

    /** Kurs jetzt (1 = Grundpreis). */
    public double factor(String key) {
        return Course.factor(pressure(key), rules);
    }

    /**
     * Handel verbuchen – im Auftrag des Handels ({@code c}). {@code volume}: Tagesmenge der Ware. Liefert den Kurs
     * danach.
     */
    public double record(Connection c, String key, int amount, int volume, boolean bought) throws SQLException {
        if (amount <= 0) {
            return factor(key);
        }
        long now = clock.getAsLong();
        State old = Database.one(c, "SELECT pressure, updated FROM courses WHERE item_key = ?",
                rs -> new State(rs.getDouble(1), rs.getLong(2)), key);
        double p = old == null ? 0 : Course.decay(old.pressure(), now - old.updated(), rules);
        double next = rules.on() ? Course.after(p, amount, volume, bought, rules) : 0;
        write(c, key, next, now, bought ? amount : 0, bought ? 0 : amount);
        return Course.factor(next, rules);
    }

    private void write(Connection c, String key, double pressure, long now, long bought, long sold)
            throws SQLException {
        Database.update(c, "INSERT INTO courses(item_key, pressure, updated) VALUES (?, ?, ?) "
                + "ON CONFLICT(item_key) DO UPDATE SET pressure = excluded.pressure, updated = excluded.updated",
                key, pressure, now);
        Database.update(c, "INSERT INTO course_log(item_key, hour, pressure, updated, bought, sold) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(item_key, hour) DO UPDATE SET pressure = excluded.pressure, "
                + "updated = excluded.updated, bought = bought + excluded.bought, sold = sold + excluded.sold",
                key, Course.hour(now), pressure, now, bought, sold);
        cache.put(key, new State(pressure, now));
    }

    /** Kurs zu einem Zeitpunkt der Vergangenheit (aus dem Verlauf). */
    public double factorAt(String key, long time) {
        List<Point> pts = history(key, time, 1, 1);
        return pts.isEmpty() ? 1.0 : pts.get(0).factor();
    }

    /**
     * Verlauf: {@code points} Punkte im Abstand {@code stepMs}, der letzte bei {@code end}. Jeder Punkt ist der Kurs zu
     * dieser Zeit (letzter Handel davor, seitdem erholt).
     */
    public List<Point> history(String key, long end, long stepMs, int points) {
        long start = end - stepMs * (points - 1);
        long from = start - KEEP_MS;
        List<State> rows = db.tx(c -> Database.query(c, "SELECT pressure, updated FROM course_log WHERE item_key = ? "
                + "AND updated >= ? AND updated <= ? ORDER BY updated", rs -> new State(rs.getDouble(1), rs.getLong(2)),
                key, from, end));
        List<Point> out = new ArrayList<>(points);
        int i = 0;
        State last = null;
        for (int k = 0; k < points; k++) {
            long t = start + stepMs * k;
            while (i < rows.size() && rows.get(i).updated() <= t) {
                last = rows.get(i++);
            }
            double p = last == null ? 0 : Course.decay(last.pressure(), t - last.updated(), rules);
            out.add(new Point(t, Course.factor(p, rules)));
        }
        return out;
    }

    /** Gehandelte Stück seit {@code since} (ms). */
    public Volume volume(String key, long since) {
        return db.tx(c -> Database.one(c, "SELECT COALESCE(SUM(bought), 0), COALESCE(SUM(sold), 0) FROM course_log "
                + "WHERE item_key = ? AND hour >= ?", rs -> new Volume(rs.getLong(1), rs.getLong(2)), key,
                Course.hour(since)));
    }

    /** Alle Waren, deren Kurs gerade nicht beim Grundpreis steht, größter Ausschlag zuerst. */
    public List<Mover> movers() {
        List<Mover> out = new ArrayList<>();
        for (String key : cache.keySet()) {
            double f = factor(key);
            if (Course.percent(f) != 100) {
                out.add(new Mover(key, f));
            }
        }
        out.sort(Comparator.comparingDouble((Mover m) -> Math.abs(Math.log(m.factor()))).reversed()
                .thenComparing(Mover::key));
        return out;
    }

    /** Kurs von Hand setzen ({@code /smpadmin boerse setzen}); {@code factor} 1 = Grundpreis. */
    public void set(String key, double factor) {
        Course.Rules r = rules;
        double f = Math.max(r.minPercent() / 100.0, Math.min(r.maxPercent() / 100.0, factor));
        double p = r.halveAfter() * Math.log(f) / Math.log(2);
        db.run(c -> write(c, key, Math.abs(p) < 1e-9 ? 0 : p, clock.getAsLong(), 0, 0));
    }

    /** Kurse zurück auf den Grundpreis ({@code key} null = alle); der Verlauf bleibt. */
    public int reset(String key) {
        List<String> keys = key == null ? List.copyOf(cache.keySet()) : List.of(key);
        db.run(c -> {
            for (String k : keys) {
                write(c, k, 0, clock.getAsLong(), 0, 0);
            }
        });
        return keys.size();
    }

    /** Alten Verlauf löschen (einmal am Tag reicht). */
    public void prune() {
        long now = clock.getAsLong();
        db.run(c -> Database.update(c, "DELETE FROM course_log WHERE hour < ?", Course.hour(now - KEEP_MS)));
    }
}
