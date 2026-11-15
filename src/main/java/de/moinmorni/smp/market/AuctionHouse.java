package de.moinmorni.smp.market;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Database.Abort;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.economy.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Das Auktionshaus: Spieler bieten Gegenstände zu einem festen Preis an, andere kaufen. Der Server verkauft nichts –
 * er nimmt nur eine kleine Gebühr vom Erlös (Geld verschwindet, damit Taler nicht wertlos werden).
 *
 * <p>Ein Kauf ist ein einziger Auftrag: Angebot noch da? genug Geld? → abbuchen, Erlös gutschreiben, Gebühr, als
 * verkauft markieren, Gegenstand ins Abholfach des Käufers. Kommen zwei Käufer gleichzeitig, gewinnt genau einer; der
 * andere bezahlt nichts.
 */
public final class AuctionHouse {

    /** Sortierung beim Stöbern. */
    public enum Sort {
        CHEAPEST, NEWEST, PER_PIECE
    }

    /** Ein Angebot. */
    public record Listing(long id, UUID seller, String sellerName, ItemData item, long price, long created, long expires,
                          String state, UUID buyer) {
        /** Preis pro Stück (gerundet). */
        public long unitPrice() {
            return Math.max(1, Math.round((double) price / item.amount()));
        }
    }

    /** Ergebnis beim Einstellen. */
    public enum ListStatus {
        OK, TOO_MANY, INVALID_PRICE
    }

    public record ListResult(ListStatus status, long id) {
    }

    /** Ergebnis beim Kaufen. */
    public enum BuyStatus {
        OK, GONE, OWN, NOT_ENOUGH
    }

    public record BuyResult(BuyStatus status, Listing listing, long fee) {
    }

    public static final String ACTIVE = "active";
    public static final String SOLD = "sold";
    public static final String EXPIRED = "expired";
    public static final String CANCELLED = "cancelled";

    private static final String COLUMNS = "id, seller, seller_name, item, item_key, item_name, category, amount, price, "
            + "created, expires, state, buyer";

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

    public AuctionHouse(Database db, Bank bank, Mailbox mailbox, Notices notices, PriceHistory prices,
                        MarketStats stats, LongSupplier clock, int feeBasisPoints, long durationMs, int maxActive) {
        this.db = db;
        this.bank = bank;
        this.mailbox = mailbox;
        this.notices = notices;
        this.prices = prices;
        this.stats = stats;
        this.clock = clock;
        configure(feeBasisPoints, durationMs, maxActive);
    }

    /** Werte aus der config.yml (auch nach /smpadmin neuladen). */
    public void configure(int feeBasisPoints, long durationMs, int maxActive) {
        this.feeBasisPoints = feeBasisPoints;
        this.durationMs = Math.max(60_000, durationMs);
        this.maxActive = Math.max(1, maxActive);
    }

    public int feeBasisPoints() {
        return feeBasisPoints;
    }

    public int maxActive() {
        return maxActive;
    }

    public long durationMs() {
        return durationMs;
    }

    /** Angebot einstellen. Der Gegenstand muss dem Spieler vorher schon abgenommen sein. */
    public ListResult list(UUID seller, String sellerName, ItemData item, long price) {
        if (price <= 0 || price > Money.MAX) {
            return new ListResult(ListStatus.INVALID_PRICE, -1);
        }
        return db.tx(c -> {
            long active = Database.number(c, "SELECT COUNT(*) FROM listings WHERE seller = ? AND state = ?", 0,
                    seller, ACTIVE);
            if (active >= maxActive) {
                return new ListResult(ListStatus.TOO_MANY, -1);
            }
            long now = clock.getAsLong();
            long id = Database.insert(c, "INSERT INTO listings(seller, seller_name, item, item_key, item_name, amount, "
                            + "category, price, created, expires, state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    seller, sellerName, item.blob(), item.key(), item.name(), item.amount(), item.category(), price,
                    now, now + durationMs, ACTIVE);
            return new ListResult(ListStatus.OK, id);
        });
    }

    /** Kaufen. Bei {@link BuyStatus#OK} liegt der Gegenstand danach im Abholfach des Käufers. */
    public BuyResult buy(long id, UUID buyer, String buyerName) {
        try {
            return db.tx(c -> {
                Listing l = Database.one(c, "SELECT " + COLUMNS + " FROM listings WHERE id = ?", AuctionHouse::row, id);
                long now = clock.getAsLong();
                if (l == null || !ACTIVE.equals(l.state()) || l.expires() <= now) {
                    return new BuyResult(BuyStatus.GONE, l, 0);
                }
                if (l.seller().equals(buyer)) {
                    return new BuyResult(BuyStatus.OWN, l, 0);
                }
                // erst das Angebot sichern – nur einer kann es von „aktiv“ auf „verkauft“ setzen
                int claimed = Database.update(c, "UPDATE listings SET state = ?, buyer = ?, ended = ? "
                        + "WHERE id = ? AND state = ?", SOLD, buyer, now, id, ACTIVE);
                if (claimed != 1) {
                    return new BuyResult(BuyStatus.GONE, l, 0);
                }
                long fee = Fees.of(l.price(), feeBasisPoints);
                String ref = "ah:" + id;
                bank.move(c, buyer.toString(), l.seller().toString(), l.price(), "ah_kauf", ref);
                if (fee > 0) {
                    bank.move(c, l.seller().toString(), Bank.SERVER, fee, "ah_gebuehr", ref);
                }
                mailbox.add(c, buyer, l.item(), "ah_kauf");
                prices.record(c, l.item().key(), l.price(), l.item().amount(), "ah");
                stats.add(c, l.seller(), l.sellerName(), 1, 0, 0, l.price() - fee);
                stats.add(c, buyer, buyerName, 0, 1, 0, 0);
                notices.add(c, l.seller(), "ah.sold", "item", l.item().key(), "amount",
                        Integer.toString(l.item().amount()), "price", Long.toString(l.price()), "fee",
                        Long.toString(fee), "buyer", buyerName == null ? "?" : buyerName);
                return new BuyResult(BuyStatus.OK, new Listing(l.id(), l.seller(), l.sellerName(), l.item(), l.price(),
                        l.created(), l.expires(), SOLD, buyer), fee);
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new BuyResult(BuyStatus.NOT_ENOUGH, get(id).orElse(null), 0);
            }
            throw a;
        }
    }

    /** Eigenes Angebot zurücknehmen: Gegenstand ins Abholfach. */
    public boolean cancel(long id, UUID seller, boolean admin) {
        return db.tx(c -> {
            Listing l = Database.one(c, "SELECT " + COLUMNS + " FROM listings WHERE id = ?", AuctionHouse::row, id);
            if (l == null || !ACTIVE.equals(l.state()) || !admin && !l.seller().equals(seller)) {
                return false;
            }
            int n = Database.update(c, "UPDATE listings SET state = ?, ended = ? WHERE id = ? AND state = ?",
                    CANCELLED, clock.getAsLong(), id, ACTIVE);
            if (n != 1) {
                return false;
            }
            mailbox.add(c, l.seller(), l.item(), "ah_zurueck");
            if (admin && !l.seller().equals(seller)) {
                notices.add(c, l.seller(), "ah.removed", "item", l.item().key(), "amount",
                        Integer.toString(l.item().amount()));
            }
            return true;
        });
    }

    /** Abgelaufene Angebote zurück ins Abholfach; liefert die Verkäufer, die eine Nachricht bekommen haben. */
    public List<UUID> expire() {
        return db.tx(c -> {
            long now = clock.getAsLong();
            List<Listing> due = Database.query(c, "SELECT " + COLUMNS + " FROM listings WHERE state = ? AND expires <= ?",
                    AuctionHouse::row, ACTIVE, now);
            List<UUID> sellers = new ArrayList<>();
            for (Listing l : due) {
                Database.update(c, "UPDATE listings SET state = ?, ended = ? WHERE id = ?", EXPIRED, now, l.id());
                mailbox.add(c, l.seller(), l.item(), "ah_abgelaufen");
                notices.add(c, l.seller(), "ah.expired", "item", l.item().key(), "amount",
                        Integer.toString(l.item().amount()));
                sellers.add(l.seller());
            }
            return sellers;
        });
    }

    public Optional<Listing> get(long id) {
        return Optional.ofNullable(db.tx(c -> Database.one(c, "SELECT " + COLUMNS + " FROM listings WHERE id = ?",
                AuctionHouse::row, id)));
    }

    /** Aktive Angebote eines Verkäufers. */
    public List<Listing> activeOf(UUID seller) {
        return db.tx(c -> Database.query(c, "SELECT " + COLUMNS + " FROM listings WHERE seller = ? AND state = ? "
                + "ORDER BY id", AuctionHouse::row, seller, ACTIVE));
    }

    public int countActive(UUID seller) {
        return db.<Long>tx(c -> Database.number(c, "SELECT COUNT(*) FROM listings WHERE seller = ? AND state = ?", 0,
                seller, ACTIVE)).intValue();
    }

    /**
     * Stöbern: nur aktive, nicht abgelaufene Angebote; optional Abteilung und Suchtext.
     *
     * @return höchstens {@code limit} Angebote ab {@code offset}
     */
    public List<Listing> browse(Category category, String search, Sort sort, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM listings WHERE state = ? AND expires > ?");
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
        sql.append(switch (sort) {
            case CHEAPEST -> " ORDER BY price ASC, id ASC";
            case NEWEST -> " ORDER BY id DESC";
            case PER_PIECE -> " ORDER BY CAST(price AS REAL) / amount ASC, id ASC";
        });
        sql.append(" LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return db.tx(c -> Database.query(c, sql.toString(), AuctionHouse::row, args.toArray()));
    }

    /** Wie viele Treffer {@link #browse} insgesamt hätte (für die Seitenzahl). */
    public int count(Category category, String search) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM listings WHERE state = ? AND expires > ?");
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

    private static Listing row(ResultSet rs) throws SQLException {
        String buyer = rs.getString(13);
        return new Listing(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                new ItemData(rs.getBytes(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)),
                rs.getLong(9), rs.getLong(10), rs.getLong(11), rs.getString(12),
                buyer == null ? null : UUID.fromString(buyer));
    }
}
