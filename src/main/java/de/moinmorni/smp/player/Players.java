package de.moinmorni.smp.player;

import de.moinmorni.smp.db.Database;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Was MorniSMP sich pro Spieler merkt: Spielzeit (gesamt und heute), besuchte Dimensionen (für /rtp nether|end),
 * Leiste an/aus, Serie der täglichen Belohnung und die Zuhause-Punkte.
 */
public final class Players {

    /** Ein Zuhause. */
    public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {
    }

    /** Der Stand eines Spielers. */
    public record Info(UUID uuid, String name, long playtime, String day, long daySeconds, int streak, String lastReward,
                       boolean nether, boolean end, boolean sidebar) {
    }

    public enum HomeStatus {
        OK, REPLACED, LIMIT, BAD_NAME
    }

    private final Database db;
    private final LongSupplier clock;

    public Players(Database db, LongSupplier clock) {
        this.db = db;
        this.clock = clock;
    }

    public Database db() {
        return db;
    }

    /** Beim Betreten: anlegen bzw. Namen aktualisieren. Liefert true beim allerersten Mal. */
    public boolean join(UUID uuid, String name) {
        return db.tx(c -> {
            long now = clock.getAsLong();
            int made = Database.update(c, "INSERT OR IGNORE INTO players(uuid, name, first_join, last_seen) "
                    + "VALUES (?, ?, ?, ?)", uuid, name, now, now);
            if (made == 0) {
                Database.update(c, "UPDATE players SET name = ?, last_seen = ? WHERE uuid = ?", name, now, uuid);
            }
            return made == 1;
        });
    }

    public Optional<Info> info(UUID uuid) {
        return Optional.ofNullable(db.tx(c -> Database.one(c, "SELECT uuid, name, playtime, day, day_seconds, streak, "
                + "last_reward, nether, the_end, sidebar FROM players WHERE uuid = ?", Players::row, uuid)));
    }

    /** Spielzeit gutschreiben; ein neuer Tag fängt bei 0 an. */
    public void addPlaytime(UUID uuid, int seconds, String today) {
        db.run(c -> Database.update(c, "UPDATE players SET playtime = playtime + ?, "
                + "day_seconds = CASE WHEN day = ? THEN day_seconds + ? ELSE ? END, day = ?, last_seen = ? "
                + "WHERE uuid = ?", seconds, today, seconds, seconds, today, clock.getAsLong(), uuid));
    }

    /** Heute gespielte Sekunden. */
    public long todaySeconds(UUID uuid, String today) {
        return db.tx(c -> Database.number(c, "SELECT CASE WHEN day = ? THEN day_seconds ELSE 0 END FROM players "
                + "WHERE uuid = ?", 0, today, uuid));
    }

    public void visited(UUID uuid, String dimension) {
        String col = switch (dimension) {
            case "nether" -> "nether";
            case "end" -> "the_end";
            default -> null;
        };
        if (col != null) {
            db.run(c -> Database.update(c, "UPDATE players SET " + col + " = 1 WHERE uuid = ?", uuid));
        }
    }

    public void sidebar(UUID uuid, boolean on) {
        db.run(c -> Database.update(c, "UPDATE players SET sidebar = ? WHERE uuid = ?", on, uuid));
    }

    // ---------- Zuhause ----------

    public List<Home> homes(UUID uuid) {
        return db.tx(c -> Database.query(c, "SELECT name, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ? "
                + "ORDER BY name", rs -> new Home(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4),
                rs.getDouble(5), rs.getFloat(6), rs.getFloat(7)), uuid));
    }

    public Optional<Home> home(UUID uuid, String name) {
        return homes(uuid).stream().filter(h -> h.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Name eines Zuhauses: 1–16 Buchstaben, Ziffern, _ oder -. */
    public static boolean validName(String name) {
        return name != null && name.matches("[\\p{L}0-9_-]{1,16}");
    }

    public HomeStatus setHome(UUID uuid, Home home, int max) {
        if (!validName(home.name())) {
            return HomeStatus.BAD_NAME;
        }
        return db.tx(c -> {
            Long existing = Database.one(c, "SELECT 1 FROM homes WHERE uuid = ? AND name = ? COLLATE NOCASE",
                    rs -> 1L, uuid, home.name());
            if (existing == null && Database.number(c, "SELECT COUNT(*) FROM homes WHERE uuid = ?", 0, uuid) >= max) {
                return HomeStatus.LIMIT;
            }
            Database.update(c, "DELETE FROM homes WHERE uuid = ? AND name = ? COLLATE NOCASE", uuid, home.name());
            Database.update(c, "INSERT INTO homes(uuid, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    uuid, home.name(), home.world(), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
            return existing == null ? HomeStatus.OK : HomeStatus.REPLACED;
        });
    }

    public boolean deleteHome(UUID uuid, String name) {
        return db.tx(c -> Database.update(c, "DELETE FROM homes WHERE uuid = ? AND name = ? COLLATE NOCASE", uuid,
                name)) > 0;
    }

    private static Info row(ResultSet rs) throws SQLException {
        return new Info(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3), rs.getString(4),
                rs.getLong(5), rs.getInt(6), rs.getString(7), rs.getInt(8) != 0, rs.getInt(9) != 0,
                rs.getInt(10) != 0);
    }
}
