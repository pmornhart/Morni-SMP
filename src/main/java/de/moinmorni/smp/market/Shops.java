package de.moinmorni.smp.market;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Database.Abort;
import de.moinmorni.smp.economy.Bank;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Ladenkisten: eine Kiste (oder ein Fass) mit Schild „[Laden]“. {@link Mode#SELL} = der Laden verkauft (Ware aus der
 * Kiste, Geld an den Besitzer), {@link Mode#BUY} = der Laden kauft an (Ware in die Kiste, Geld vom Besitzer). Die
 * Ware bewegt das Plugin; hier passiert nur das Geld – in einem Auftrag, mit Kassenbuch.
 */
public final class Shops {

    public enum Mode {
        SELL, BUY
    }

    /** Ein Laden. {@code item}: Ware mit Menge pro Klick. {@code stall}: Nummer des Marktstands oder 0. */
    public record Shop(long id, String world, int x, int y, int z, int sx, int sy, int sz, UUID owner, String ownerName,
                       ItemData item, Mode mode, long price, int stall) {
    }

    public enum TradeStatus {
        OK, NOT_ENOUGH, OWN, GONE
    }

    public record TradeResult(TradeStatus status, long fee) {
    }

    private static final String COLUMNS = "id, world, x, y, z, sx, sy, sz, owner, owner_name, item, item_key, "
            + "item_name, mode, amount, price, stall";

    private final Database db;
    private final Bank bank;
    private final Notices notices;
    private final PriceHistory prices;
    private final MarketStats stats;
    private final LongSupplier clock;
    private volatile int feeBasisPoints;
    /** Schnelle Suche in Ereignissen: „welt:x:y:z“ der Kiste bzw. des Schilds → Laden-ID. */
    private final Map<String, Long> containers = new ConcurrentHashMap<>();
    private final Map<String, Long> signs = new ConcurrentHashMap<>();

    public Shops(Database db, Bank bank, Notices notices, PriceHistory prices, MarketStats stats, LongSupplier clock,
                 int feeBasisPoints) {
        this.db = db;
        this.bank = bank;
        this.notices = notices;
        this.prices = prices;
        this.stats = stats;
        this.clock = clock;
        this.feeBasisPoints = feeBasisPoints;
        reload();
    }

    public void configure(int basisPoints) {
        this.feeBasisPoints = basisPoints;
    }

    /** Merkliste neu aus der Datenbank. */
    public void reload() {
        containers.clear();
        signs.clear();
        for (Shop s : all()) {
            remember(s);
        }
    }

    private void remember(Shop s) {
        containers.put(key(s.world(), s.x(), s.y(), s.z()), s.id());
        signs.put(key(s.world(), s.sx(), s.sy(), s.sz()), s.id());
    }

    public static String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public boolean isContainer(String world, int x, int y, int z) {
        return containers.containsKey(key(world, x, y, z));
    }

    public boolean isSign(String world, int x, int y, int z) {
        return signs.containsKey(key(world, x, y, z));
    }

    public Optional<Shop> byContainer(String world, int x, int y, int z) {
        Long id = containers.get(key(world, x, y, z));
        return id == null ? Optional.empty() : get(id);
    }

    public Optional<Shop> bySign(String world, int x, int y, int z) {
        Long id = signs.get(key(world, x, y, z));
        return id == null ? Optional.empty() : get(id);
    }

    public Shop create(String world, int x, int y, int z, int sx, int sy, int sz, UUID owner, String ownerName,
                       ItemData item, Mode mode, long price, int stall) {
        Shop s = db.tx(c -> {
            Database.update(c, "DELETE FROM shops WHERE world = ? AND ((x = ? AND y = ? AND z = ?) OR "
                    + "(sx = ? AND sy = ? AND sz = ?))", world, x, y, z, sx, sy, sz);
            long id = Database.insert(c, "INSERT INTO shops(world, x, y, z, sx, sy, sz, owner, owner_name, item, "
                            + "item_key, item_name, mode, amount, price, stall, created) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    world, x, y, z, sx, sy, sz, owner, ownerName, item.withAmount(1).blob(), item.key(), item.name(),
                    mode.name(), item.amount(), price, stall, clock.getAsLong());
            return new Shop(id, world, x, y, z, sx, sy, sz, owner, ownerName, item, mode, price, stall);
        });
        reload();
        return s;
    }

    public Optional<Shop> get(long id) {
        return Optional.ofNullable(db.tx(c -> Database.one(c, "SELECT " + COLUMNS + " FROM shops WHERE id = ?",
                Shops::row, id)));
    }

    public List<Shop> all() {
        return db.tx(c -> Database.query(c, "SELECT " + COLUMNS + " FROM shops ORDER BY id", Shops::row));
    }

    public List<Shop> ofOwner(UUID owner) {
        return db.tx(c -> Database.query(c, "SELECT " + COLUMNS + " FROM shops WHERE owner = ? ORDER BY id",
                Shops::row, owner));
    }

    public List<Shop> inStall(int stall) {
        return db.tx(c -> Database.query(c, "SELECT " + COLUMNS + " FROM shops WHERE stall = ? ORDER BY id",
                Shops::row, stall));
    }

    public void delete(long id) {
        db.run(c -> Database.update(c, "DELETE FROM shops WHERE id = ?", id));
        reload();
    }

    /**
     * Geld für einen Klick: Beim Verkaufen zahlt der Kunde an den Besitzer, beim Ankauf der Besitzer an den Kunden.
     * Die Gebühr (falls eingestellt) trägt, wer das Geld bekommt.
     */
    public TradeResult trade(long shopId, UUID customer, String customerName) {
        try {
            return db.tx(c -> {
                Shop s = Database.one(c, "SELECT " + COLUMNS + " FROM shops WHERE id = ?", Shops::row, shopId);
                if (s == null) {
                    return new TradeResult(TradeStatus.GONE, 0);
                }
                if (s.owner().equals(customer)) {
                    return new TradeResult(TradeStatus.OWN, 0);
                }
                UUID payer = s.mode() == Mode.SELL ? customer : s.owner();
                UUID payee = s.mode() == Mode.SELL ? s.owner() : customer;
                String ref = "shop:" + s.id();
                long fee = Fees.of(s.price(), feeBasisPoints);
                bank.move(c, payer.toString(), payee.toString(), s.price(), "laden", ref);
                if (fee > 0) {
                    bank.move(c, payee.toString(), Bank.SERVER, fee, "laden_gebuehr", ref);
                }
                prices.record(c, s.item().key(), s.price(), s.item().amount(), "shop");
                if (s.mode() == Mode.SELL) {
                    stats.add(c, s.owner(), s.ownerName(), 1, 0, 0, s.price() - fee);
                    stats.add(c, customer, customerName, 0, 1, 0, 0);
                    notices.add(c, s.owner(), "shop.sold", "item", s.item().key(), "amount",
                            Integer.toString(s.item().amount()), "price", Long.toString(s.price() - fee), "buyer",
                            customerName == null ? "?" : customerName);
                } else {
                    stats.add(c, customer, customerName, 1, 0, 0, s.price() - fee);
                    stats.add(c, s.owner(), s.ownerName(), 0, 1, 0, 0);
                    notices.add(c, s.owner(), "shop.bought", "item", s.item().key(), "amount",
                            Integer.toString(s.item().amount()), "price", Long.toString(s.price()), "seller",
                            customerName == null ? "?" : customerName);
                }
                return new TradeResult(TradeStatus.OK, fee);
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new TradeResult(TradeStatus.NOT_ENOUGH, 0);
            }
            throw a;
        }
    }

    private static Shop row(ResultSet rs) throws SQLException {
        return new Shop(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                rs.getInt(7), rs.getInt(8), UUID.fromString(rs.getString(9)), rs.getString(10),
                new ItemData(rs.getBytes(11), rs.getString(12), rs.getString(13), null, rs.getInt(15)),
                Mode.valueOf(rs.getString(14)), rs.getLong(16), rs.getInt(17));
    }
}
