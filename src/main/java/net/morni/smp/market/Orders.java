package net.morni.smp.market;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.morni.smp.db.Database;
import net.morni.smp.db.Database.Abort;
import net.morni.smp.economy.Bank;
import net.morni.smp.economy.Money;

/**
 * Aufträge (Kaufgesuche): „Ich will 64 × Weizen für je 0,50 ₮.“ Das ganze Geld liegt ab dem Einstellen in Treuhand
 * ({@code escrow:order:<id>}) – wer liefert, wird sofort bezahlt, auch wenn der Auftraggeber offline ist. Teil-
 * lieferungen gehen; die Ware kommt ins Abholfach des Auftraggebers. Zurückziehen oder Ablauf gibt den Rest zurück.
 */
public final class Orders {

    /** Ein Auftrag; {@code item} ist die Vorlage (Anzahl 1). */
    public record Order(long id, UUID owner, String ownerName, ItemData item, int wanted, int delivered, long unitPrice,
                        long escrow, long created, long expires, String state) {
        public int remaining() {
            return wanted - delivered;
        }
    }

    public enum CreateStatus {
        OK, TOO_MANY, INVALID, NOT_ENOUGH
    }

    public record CreateResult(CreateStatus status, long id) {
    }

    public enum DeliverStatus {
        OK, GONE, OWN, NOTHING
    }

    /** {@code accepted}: so viele Stück muss der Lieferant jetzt abgeben; {@code paid}: Auszahlung nach Gebühr. */
    public record DeliverResult(DeliverStatus status, int accepted, long paid, long fee, Order order) {
    }

    public static final String ACTIVE = "active";
    public static final String FILLED = "filled";
    public static final String CANCELLED = "cancelled";
    public static final String EXPIRED = "expired";
    /** Höchstmenge pro Auftrag (36 Stapel à 64 = ein volles Inventar). */
    public static final int MAX_WANTED = 2304;

    private static final String COLUMNS = "id, owner, owner_name, item, item_key, item_name, category, wanted, "
            + "delivered, unit_price, escrow, created, expires, state";

    private final Database db;
    private final Bank bank;
    private final Mailbox mailbox;
    private final Notices notices;
    private final PriceHistory prices;
    private final MarketStats stats;
    private final LongSupplier clock;
    private volatile int feeBasisPoints;
    private volatile long durationMs;
    private volatile int maxActive;

    public Orders(Database db, Bank bank, Mailbox mailbox, Notices notices, PriceHistory prices, MarketStats stats,
                  LongSupplier clock, int feeBasisPoints, long durationMs, int maxActive) {
        this.db = db;
        this.bank = bank;
        this.mailbox = mailbox;
        this.notices = notices;
        this.prices = prices;
        this.stats = stats;
        this.clock = clock;
        configure(feeBasisPoints, durationMs, maxActive);
    }

    public void configure(int feeBasisPoints, long durationMs, int maxActive) {
        this.feeBasisPoints = feeBasisPoints;
        this.durationMs = Math.max(60_000, durationMs);
        this.maxActive = Math.max(1, maxActive);
    }

    public int maxActive() {
        return maxActive;
    }

    public int feeBasisPoints() {
        return feeBasisPoints;
    }

    static String escrow(long id) {
        return Bank.ESCROW + "order:" + id;
    }

    /** Auftrag einstellen: das ganze Geld ({@code wanted × unitPrice}) wird sofort festgelegt. */
    public CreateResult create(UUID owner, String ownerName, ItemData template, int wanted, long unitPrice) {
        if (wanted <= 0 || wanted > MAX_WANTED || unitPrice <= 0 || unitPrice > Money.MAX / MAX_WANTED) {
            return new CreateResult(CreateStatus.INVALID, -1);
        }
        long total = unitPrice * wanted;
        try {
            return db.tx(c -> {
                long active = Database.number(c, "SELECT COUNT(*) FROM orders WHERE owner = ? AND state = ?", 0,
                        owner, ACTIVE);
                if (active >= maxActive) {
                    return new CreateResult(CreateStatus.TOO_MANY, -1);
                }
                long now = clock.getAsLong();
                ItemData one = template.withAmount(1);
                long id = Database.insert(c, "INSERT INTO orders(owner, owner_name, item, item_key, item_name, category, "
                                + "wanted, delivered, unit_price, escrow, created, expires, state) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)",
                        owner, ownerName, one.blob(), one.key(), one.name(), one.category(), wanted, unitPrice, total,
                        now, now + durationMs, ACTIVE);
                bank.move(c, owner.toString(), escrow(id), total, "auftrag_treuhand", "order:" + id);
                return new CreateResult(CreateStatus.OK, id);
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new CreateResult(CreateStatus.NOT_ENOUGH, -1);
            }
            throw a;
        }
    }

    /**
     * Liefern: {@code offered} passende Stück liegen beim Lieferanten bereit. Angenommen wird höchstens, was noch
     * fehlt; genau so viele muss das Plugin danach aus dem Inventar nehmen.
     */
    public DeliverResult deliver(long id, UUID deliverer, String delivererName, int offered) {
        return db.tx(c -> {
            Order o = Database.one(c, "SELECT " + COLUMNS + " FROM orders WHERE id = ?", Orders::row, id);
            long now = clock.getAsLong();
            if (o == null || !ACTIVE.equals(o.state()) || o.expires() <= now) {
                return new DeliverResult(DeliverStatus.GONE, 0, 0, 0, o);
            }
            if (o.owner().equals(deliverer)) {
                return new DeliverResult(DeliverStatus.OWN, 0, 0, 0, o);
            }
            int k = Math.min(Math.max(0, offered), o.remaining());
            if (k <= 0) {
                return new DeliverResult(DeliverStatus.NOTHING, 0, 0, 0, o);
            }
            long gross = o.unitPrice() * k;
            long fee = Fees.of(gross, feeBasisPoints);
            int delivered = o.delivered() + k;
            long escrowLeft = o.escrow() - gross;
            if (escrowLeft < 0) {
                throw new IllegalStateException("Treuhand von Auftrag " + id + " reicht nicht");
            }
            String state = delivered >= o.wanted() ? FILLED : ACTIVE;
            int n = Database.update(c, "UPDATE orders SET delivered = ?, escrow = ?, state = ? "
                    + "WHERE id = ? AND delivered = ? AND state = ?", delivered, escrowLeft, state, id, o.delivered(),
                    ACTIVE);
            if (n != 1) {
                return new DeliverResult(DeliverStatus.GONE, 0, 0, 0, o);
            }
            String ref = "order:" + id;
            bank.move(c, escrow(id), deliverer.toString(), gross - fee, "auftrag_lieferung", ref);
            if (fee > 0) {
                bank.move(c, escrow(id), Bank.SERVER, fee, "auftrag_gebuehr", ref);
            }
            if (state.equals(FILLED) && escrowLeft > 0) {
                // kann nur bei Rundung übrig bleiben – zurück an den Auftraggeber
                bank.move(c, escrow(id), o.owner().toString(), escrowLeft, "auftrag_rest", ref);
                Database.update(c, "UPDATE orders SET escrow = 0 WHERE id = ?", id);
            }
            mailbox.add(c, o.owner(), o.item().withAmount(k), "auftrag");
            prices.record(c, o.item().key(), gross, k, "order");
            stats.add(c, deliverer, delivererName, 1, 0, 1, gross - fee);
            stats.add(c, o.owner(), o.ownerName(), 0, 1, 0, 0);
            notices.add(c, o.owner(), state.equals(FILLED) ? "order.filled" : "order.delivered", "item",
                    o.item().key(), "amount", Integer.toString(k), "total", Integer.toString(o.wanted()),
                    "delivered", Integer.toString(delivered), "from", delivererName == null ? "?" : delivererName);
            Order after = new Order(o.id(), o.owner(), o.ownerName(), o.item(), o.wanted(), delivered, o.unitPrice(),
                    state.equals(FILLED) ? 0 : escrowLeft, o.created(), o.expires(), state);
            return new DeliverResult(DeliverStatus.OK, k, gross - fee, fee, after);
        });
    }

    /** Zurückziehen: übriges Geld zurück. Bereits gelieferte Ware bleibt im Abholfach. */
    public long cancel(long id, UUID owner, boolean admin) {
        return db.tx(c -> {
            Order o = Database.one(c, "SELECT " + COLUMNS + " FROM orders WHERE id = ?", Orders::row, id);
            if (o == null || !ACTIVE.equals(o.state()) || !admin && !o.owner().equals(owner)) {
                return -1L;
            }
            return close(c, o, CANCELLED);
        });
    }

    /** Abgelaufene Aufträge schließen, Geld zurück; liefert die betroffenen Auftraggeber. */
    public List<UUID> expire() {
        return db.tx(c -> {
            List<Order> due = Database.query(c, "SELECT " + COLUMNS + " FROM orders WHERE state = ? AND expires <= ?",
                    Orders::row, ACTIVE, clock.getAsLong());
            List<UUID> owners = new ArrayList<>();
            for (Order o : due) {
                close(c, o, EXPIRED);
                notices.add(c, o.owner(), "order.expired", "item", o.item().key(), "refund",
                        Long.toString(o.escrow()));
                owners.add(o.owner());
            }
            return owners;
        });
    }

    private long close(Connection c, Order o, String state) throws SQLException {
        int n = Database.update(c, "UPDATE orders SET state = ?, escrow = 0 WHERE id = ? AND state = ?", state, o.id(),
                ACTIVE);
        if (n != 1) {
            return -1;
        }
        if (o.escrow() > 0) {
            bank.move(c, escrow(o.id()), o.owner().toString(), o.escrow(), "auftrag_zurueck", "order:" + o.id());
        }
        return o.escrow();
    }

    public Optional<Order> get(long id) {
        return Optional.ofNullable(db.tx(c -> Database.one(c, "SELECT " + COLUMNS + " FROM orders WHERE id = ?",
                Orders::row, id)));
    }

    public List<Order> activeOf(UUID owner) {
        return db.tx(c -> Database.query(c, "SELECT " + COLUMNS + " FROM orders WHERE owner = ? AND state = ? "
                + "ORDER BY id", Orders::row, owner, ACTIVE));
    }

    public int countActive(UUID owner) {
        return db.<Long>tx(c -> Database.number(c, "SELECT COUNT(*) FROM orders WHERE owner = ? AND state = ?", 0,
                owner, ACTIVE)).intValue();
    }

    /** Offene Aufträge (beste Bezahlung pro Stück zuerst oder neueste). */
    public List<Order> browse(Category category, String search, boolean newest, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM orders WHERE state = ? AND expires > ?");
        args.add(ACTIVE);
        args.add(clock.getAsLong());
        if (category != null) {
            sql.append(" AND category = ?");
            args.add(category.id());
        }
        List<Set<String>> terms = SearchWords.terms(search);
        if (!terms.isEmpty()) {
            sql.append(" AND ").append(SearchWords.sql("item_key || ' ' || item_name", terms, args));
        }
        sql.append(newest ? " ORDER BY id DESC" : " ORDER BY unit_price DESC, id ASC");
        sql.append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return db.tx(c -> Database.query(c, sql.toString(), Orders::row, args.toArray()));
    }

    public int count(Category category, String search) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM orders WHERE state = ? AND expires > ?");
        args.add(ACTIVE);
        args.add(clock.getAsLong());
        if (category != null) {
            sql.append(" AND category = ?");
            args.add(category.id());
        }
        List<Set<String>> terms = SearchWords.terms(search);
        if (!terms.isEmpty()) {
            sql.append(" AND ").append(SearchWords.sql("item_key || ' ' || item_name", terms, args));
        }
        return db.<Long>tx(c -> Database.number(c, sql.toString(), 0, args.toArray())).intValue();
    }

    /** Summe aller Treuhandgelder offener Aufträge (für Prüfungen). */
    public long escrowTotal() {
        return db.tx(c -> Database.number(c, "SELECT COALESCE(SUM(escrow), 0) FROM orders WHERE state = ?", 0, ACTIVE));
    }

    private static Order row(ResultSet rs) throws SQLException {
        return new Order(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                new ItemData(rs.getBytes(4), rs.getString(5), rs.getString(6), rs.getString(7), 1),
                rs.getInt(8), rs.getInt(9), rs.getLong(10), rs.getLong(11), rs.getLong(12), rs.getLong(13),
                rs.getString(14));
    }
}
