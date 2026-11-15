package de.moinmorni.smp.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Die eine SQLite-Datei von MorniSMP. Alles, was Geld oder Gegenstände bewegt, läuft in {@link #tx}: eine Sperre
 * (immer nur ein Auftrag gleichzeitig), {@code BEGIN IMMEDIATE}, am Ende {@code COMMIT} – bei jedem Fehler
 * {@code ROLLBACK}. So geht nie Geld verloren und nichts wird doppelt.
 *
 * <p>Den Treiber ({@code org.sqlite.JDBC}) bringt Paper selbst mit; in den Tests kommt er aus Maven.
 */
public final class Database implements AutoCloseable {

    /** Arbeit mit der Verbindung, darf SQL-Fehler werfen. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    /** Arbeit ohne Ergebnis. */
    @FunctionalInterface
    public interface VoidWork {
        void run(Connection c) throws SQLException;
    }

    /**
     * Fachlicher Abbruch mitten in einem Auftrag (z. B. „zu wenig Geld“): alles wird zurückgerollt, der Grund kommt
     * beim Aufrufer an.
     */
    public static final class Abort extends RuntimeException {
        private final transient Object reason;

        public Abort(Object reason) {
            super(String.valueOf(reason), null, false, false);
            this.reason = reason;
        }

        public Object reason() {
            return reason;
        }
    }

    /** Technischer Fehler der Datenbank. */
    public static final class DbException extends RuntimeException {
        public DbException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Connection conn;
    private final ReentrantLock lock = new ReentrantLock(true);
    private int depth;

    private Database(Connection conn) {
        this.conn = conn;
    }

    /** Datei öffnen (oder anlegen) und auf den neuesten Stand bringen. */
    public static Database open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            return init(DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath()));
        } catch (Exception e) {
            throw new DbException("Datenbank lässt sich nicht öffnen: " + file, e);
        }
    }

    /** Nur im Speicher (Tests). */
    public static Database memory() {
        try {
            return init(DriverManager.getConnection("jdbc:sqlite::memory:"));
        } catch (SQLException e) {
            throw new DbException("Datenbank im Speicher lässt sich nicht öffnen", e);
        }
    }

    private static Database init(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
        }
        c.setAutoCommit(true);
        Database db = new Database(c);
        Schema.migrate(db);
        return db;
    }

    /**
     * Ein Auftrag am Stück. Läuft schon einer auf diesem Thread, gehört die Arbeit einfach dazu (gemeinsames
     * COMMIT/ROLLBACK am Ende des äußeren Auftrags).
     */
    public <T> T tx(Work<T> work) {
        lock.lock();
        try {
            if (depth > 0) {
                depth++;
                try {
                    return work.run(conn);
                } catch (SQLException e) {
                    throw new DbException("Datenbankfehler: " + e.getMessage(), e);
                } finally {
                    depth--;
                }
            }
            depth = 1;
            boolean ok = false;
            try (Statement s = conn.createStatement()) {
                s.execute("BEGIN IMMEDIATE");
            } catch (SQLException e) {
                depth = 0;
                throw new DbException("BEGIN fehlgeschlagen", e);
            }
            try {
                T result = work.run(conn);
                try (Statement s = conn.createStatement()) {
                    s.execute("COMMIT");
                }
                ok = true;
                return result;
            } catch (SQLException e) {
                throw new DbException("Datenbankfehler: " + e.getMessage(), e);
            } finally {
                if (!ok) {
                    try (Statement s = conn.createStatement()) {
                        s.execute("ROLLBACK");
                    } catch (SQLException ignored) {
                        // Verbindung kaputt – nichts mehr zu retten, der Fehler kommt oben an
                    }
                }
                depth = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Auftrag ohne Ergebnis. */
    public void run(VoidWork work) {
        tx(c -> {
            work.run(c);
            return null;
        });
    }

    /** Läuft gerade ein Auftrag auf diesem Thread? (für Prüfungen in Hilfsfunktionen) */
    public boolean inTx() {
        return lock.isHeldByCurrentThread() && depth > 0;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            conn.close();
        } catch (SQLException ignored) {
            // beim Herunterfahren egal
        } finally {
            lock.unlock();
        }
    }

    // ---------- kleine Helfer für die Dienste ----------

    /** Vorbereitete Anweisung mit Werten. */
    public static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof java.util.UUID u) {
                ps.setString(i + 1, u.toString());
            } else if (a instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else {
                ps.setObject(i + 1, a);
            }
        }
        return ps;
    }

    /** Schreiben; liefert die Zahl der geänderten Zeilen. */
    public static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args)) {
            return ps.executeUpdate();
        }
    }

    /** Einfügen; liefert die neue Zeilen-ID. */
    public static long insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args)) {
            ps.executeUpdate();
        }
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** Zeile → Objekt. */
    @FunctionalInterface
    public interface Row<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Alle Zeilen einer Abfrage. */
    public static <T> List<T> query(Connection c, String sql, Row<T> row, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) {
                out.add(row.map(rs));
            }
            return out;
        }
    }

    /** Erste Zeile oder null. */
    public static <T> T one(Connection c, String sql, Row<T> row, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? row.map(rs) : null;
        }
    }

    /** Eine Zahl (erste Spalte der ersten Zeile), sonst {@code fallback}. */
    public static long number(Connection c, String sql, long fallback, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long v = rs.getLong(1);
                return rs.wasNull() ? fallback : v;
            }
            return fallback;
        }
    }
}
