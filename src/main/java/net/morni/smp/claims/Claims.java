package net.morni.smp.claims;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.morni.smp.db.Database;
import net.morni.smp.db.Database.Abort;
import net.morni.smp.economy.Bank;

/**
 * Grundstücke: je ein Chunk (16 × 16 Blöcke, von ganz unten bis ganz oben). Die ersten {@code free} Chunks kosten
 * nichts, jeder weitere {@code price} Taler (an die Stadt). Alles liegt zusätzlich im Speicher, damit die Schutz-
 * Ereignisse (Blöcke, Kisten, Wasser …) ohne Datenbank auskommen.
 */
public final class Claims {

    /** Ein Grundstück. */
    public record Claim(String world, int cx, int cz, UUID owner, String ownerName, boolean pvp, long created) {
    }

    public enum Status {
        OK, TAKEN, OWN, LIMIT, NOT_ENOUGH, FORBIDDEN
    }

    /** {@code paid}: was es gekostet hat (0 = gratis). */
    public record ClaimResult(Status status, long paid, Claim claim) {
    }

    private final Database db;
    private final Bank bank;
    private final LongSupplier clock;
    private final Map<String, Claim> byChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trust = new ConcurrentHashMap<>();

    public Claims(Database db, Bank bank, LongSupplier clock) {
        this.db = db;
        this.bank = bank;
        this.clock = clock;
        reload();
    }

    public void reload() {
        byChunk.clear();
        trust.clear();
        db.run(c -> {
            for (Claim cl : Database.query(c, "SELECT world, cx, cz, owner, owner_name, pvp, created FROM claims",
                    Claims::row)) {
                byChunk.put(key(cl.world(), cl.cx(), cl.cz()), cl);
            }
            for (UUID[] t : Database.query(c, "SELECT owner, trusted FROM trust",
                    rs -> new UUID[] {UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2))})) {
                trust.computeIfAbsent(t[0], k -> ConcurrentHashMap.newKeySet()).add(t[1]);
            }
        });
    }

    static String key(String world, int cx, int cz) {
        return world + ':' + cx + ':' + cz;
    }

    /** Grundstück an diesem Chunk, oder null (Wildnis). */
    public Claim at(String world, int cx, int cz) {
        return byChunk.get(key(world, cx, cz));
    }

    /** Darf {@code who} auf den Grundstücken von {@code owner} bauen? (Besitzer oder vertraut) */
    public boolean trusted(UUID owner, UUID who) {
        if (owner.equals(who)) {
            return true;
        }
        Set<UUID> s = trust.get(owner);
        return s != null && s.contains(who);
    }

    public Set<UUID> trustedOf(UUID owner) {
        Set<UUID> s = trust.get(owner);
        return s == null ? Set.of() : Collections.unmodifiableSet(new HashSet<>(s));
    }

    public int count(UUID owner) {
        int n = 0;
        for (Claim c : byChunk.values()) {
            if (c.owner().equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public List<Claim> of(UUID owner) {
        return byChunk.values().stream().filter(c -> c.owner().equals(owner))
                .sorted((a, b) -> Long.compare(a.created(), b.created())).toList();
    }

    /**
     * Chunk sichern. {@code free}: so viele sind gratis, {@code price}: Preis für jeden weiteren, {@code max}: höchstens.
     */
    public ClaimResult claim(String world, int cx, int cz, UUID owner, String ownerName, int free, long price, int max) {
        try {
            ClaimResult r = db.tx(c -> {
                Claim existing = Database.one(c, "SELECT world, cx, cz, owner, owner_name, pvp, created FROM claims "
                        + "WHERE world = ? AND cx = ? AND cz = ?", Claims::row, world, cx, cz);
                if (existing != null) {
                    return new ClaimResult(existing.owner().equals(owner) ? Status.OWN : Status.TAKEN, 0, existing);
                }
                long owned = Database.number(c, "SELECT COUNT(*) FROM claims WHERE owner = ?", 0, owner);
                if (owned >= max) {
                    return new ClaimResult(Status.LIMIT, 0, null);
                }
                long cost = owned >= free ? price : 0;
                if (cost > 0) {
                    bank.move(c, owner.toString(), Bank.SERVER, cost, "grundstueck", world + ":" + cx + ":" + cz);
                }
                long now = clock.getAsLong();
                Database.update(c, "INSERT INTO claims(world, cx, cz, owner, owner_name, pvp, created) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?)", world, cx, cz, owner, ownerName, now);
                return new ClaimResult(Status.OK, cost, new Claim(world, cx, cz, owner, ownerName, false, now));
            });
            if (r.status() == Status.OK) {
                byChunk.put(key(world, cx, cz), r.claim());
            }
            return r;
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new ClaimResult(Status.NOT_ENOUGH, price, null);
            }
            throw a;
        }
    }

    /** Aufgeben (Besitzer oder Admin). Kein Geld zurück. */
    public boolean unclaim(String world, int cx, int cz, UUID who, boolean admin) {
        Claim cl = at(world, cx, cz);
        if (cl == null || !admin && !cl.owner().equals(who)) {
            return false;
        }
        db.run(c -> Database.update(c, "DELETE FROM claims WHERE world = ? AND cx = ? AND cz = ?", world, cx, cz));
        byChunk.remove(key(world, cx, cz));
        return true;
    }

    /** PvP auf diesem Grundstück erlauben/verbieten (nur Besitzer). */
    public boolean pvp(String world, int cx, int cz, UUID who, boolean on) {
        Claim cl = at(world, cx, cz);
        if (cl == null || !cl.owner().equals(who)) {
            return false;
        }
        db.run(c -> Database.update(c, "UPDATE claims SET pvp = ? WHERE world = ? AND cx = ? AND cz = ?", on, world,
                cx, cz));
        byChunk.put(key(world, cx, cz), new Claim(cl.world(), cl.cx(), cl.cz(), cl.owner(), cl.ownerName(), on,
                cl.created()));
        return true;
    }

    public void trust(UUID owner, UUID other, String otherName) {
        db.run(c -> Database.update(c, "INSERT OR REPLACE INTO trust(owner, trusted, trusted_name) VALUES (?, ?, ?)",
                owner, other, otherName));
        trust.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(other);
    }

    public boolean untrust(UUID owner, UUID other) {
        int n = db.tx(c -> Database.update(c, "DELETE FROM trust WHERE owner = ? AND trusted = ?", owner, other));
        Set<UUID> s = trust.get(owner);
        if (s != null) {
            s.remove(other);
        }
        return n > 0;
    }

    /** Namen der Vertrauten. */
    public List<String> trustedNames(UUID owner) {
        return db.tx(c -> Database.query(c, "SELECT trusted_name FROM trust WHERE owner = ? ORDER BY trusted_name",
                rs -> rs.getString(1), owner));
    }

    private static Claim row(ResultSet rs) throws SQLException {
        return new Claim(rs.getString(1), rs.getInt(2), rs.getInt(3), UUID.fromString(rs.getString(4)),
                rs.getString(5), rs.getInt(6) != 0, rs.getLong(7));
    }
}
