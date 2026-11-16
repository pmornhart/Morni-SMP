package net.morni.smp.shop;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.morni.smp.db.Database;
import net.morni.smp.db.Database.Abort;
import net.morni.smp.economy.Bank;
import net.morni.smp.market.Buyback;

/**
 * Das Händlerhaus der Stadt (Server-Shop): Spieler kaufen hier zu festen Preisen und verkaufen an den Händler.
 * Verkaufen an den Händler ist der Ankauf der Stadt ({@link Buyback}: volle Preise bis zur Tagesmenge, danach
 * Stufen) – es gibt nur ein System, das Hineinlegen-Menü {@code /verkaufen} nutzt dieselben Preise.
 *
 * <p>Jeder Kauf ist ein Auftrag in der Datenbank: Tagesgrenze prüfen, Zähler hoch, Geld an den Server (Kassenbuch
 * „shop“, Bezug „Sorte×Menge“). Reicht das Geld nicht, wird alles zurückgerollt – auch wenn zwei Käufe gleichzeitig
 * kommen. Die Gegenstände gibt das Plugin erst nach erfolgreicher Buchung heraus.
 */
public final class ServerShop {

    /** Kassenbuch-Grund eines Kaufs beim Händler (Verkäufe an ihn stehen als „ankauf“ drin). */
    public static final String REASON_BUY = "shop";
    public static final String REASON_SELL = "ankauf";

    /** Ergebnis. */
    public enum Status {
        OK, UNKNOWN, NOT_FOR_SALE, NOT_BOUGHT, OFF_SEASON, BAD_AMOUNT, LIMIT, NOT_ENOUGH
    }

    /**
     * Ergebnis eines Kaufs oder Verkaufs.
     *
     * @param left beim Kauf: wie viel heute noch geht ({@link Integer#MAX_VALUE} = ohne Grenze)
     */
    public record Deal(Status status, Ware ware, int amount, long total, int left) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    /** Umsatz seit einem Zeitpunkt: Einnahmen (Spieler kaufen) und Auszahlungen (Händler kauft an). */
    public record Turnover(long income, long purchases, long payout, long sales, List<Top> top) {
    }

    /** Meistgehandelte Ware: Stück gekauft / verkauft. */
    public record Top(String key, long bought, long sold) {
    }

    private final Database db;
    private final Bank bank;
    private final Buyback buyback;
    private volatile Catalog catalog = Catalog.empty();

    public ServerShop(Database db, Bank bank, Buyback buyback) {
        this.db = db;
        this.bank = bank;
        this.buyback = buyback;
    }

    /** Neues Sortiment; der Ankauf übernimmt die Ankaufspreise daraus. */
    public void configure(Catalog c, List<Buyback.Tier> tiers) {
        this.catalog = c;
        buyback.configure(c.sellOffers(), tiers);
    }

    public Catalog catalog() {
        return catalog;
    }

    public Buyback buyback() {
        return buyback;
    }

    /** Heute schon gekauft (Stück). */
    public long boughtToday(UUID who, String key, String day) {
        return db.tx(c -> boughtToday(c, who, key, day));
    }

    private static long boughtToday(Connection c, UUID who, String key, String day) throws SQLException {
        return Database.number(c, "SELECT bought FROM shop_daily WHERE uuid = ? AND day = ? AND item_key = ?", 0,
                who, day, key);
    }

    /** Wie viel geht heute noch (Tagesgrenze der Ware)? */
    public int leftToday(UUID who, Ware w, String day) {
        if (w.buyDaily() <= 0) {
            return Integer.MAX_VALUE;
        }
        return Pricing.leftToday(w.buyDaily(), boughtToday(who, w.key(), day));
    }

    /**
     * Kaufen: {@code amount} Stück von {@code key} für {@code amount × Preis}. Prüft Sortiment, Saison
     * ({@code month} 1–12), Menge, Tagesgrenze und Geld – alles in einem Auftrag.
     */
    public Deal buy(UUID who, String key, int amount, String day, int month) {
        Ware w = catalog.ware(key);
        if (w == null) {
            return new Deal(Status.UNKNOWN, null, amount, 0, 0);
        }
        if (!w.buyable()) {
            return new Deal(Status.NOT_FOR_SALE, w, amount, 0, 0);
        }
        if (!w.inSeason(month)) {
            return new Deal(Status.OFF_SEASON, w, amount, 0, 0);
        }
        long total = Pricing.cost(w.buy(), amount);
        if (!Pricing.validAmount(amount) || total < 0) {
            return new Deal(Status.BAD_AMOUNT, w, amount, 0, 0);
        }
        try {
            return db.tx(c -> {
                int left = Pricing.leftToday(w.buyDaily(), w.buyDaily() > 0 ? boughtToday(c, who, w.key(), day) : 0);
                if (amount > left) {
                    return new Deal(Status.LIMIT, w, amount, total, left);
                }
                Database.update(c, "INSERT INTO shop_daily(uuid, day, item_key, bought) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(uuid, day, item_key) DO UPDATE SET bought = bought + excluded.bought",
                        who, day, w.key(), amount);
                Database.update(c, "DELETE FROM shop_daily WHERE day < ?", day);
                bank.move(c, who.toString(), Bank.SERVER, total, REASON_BUY, ref(w.key(), amount));
                return new Deal(Status.OK, w, amount, total,
                        left == Integer.MAX_VALUE ? left : left - amount);
            });
        } catch (Abort a) {
            if (a.reason() == Bank.Result.NOT_ENOUGH) {
                return new Deal(Status.NOT_ENOUGH, w, amount, total, 0);
            }
            throw a;
        }
    }

    /** Was gäbe der Händler jetzt für {@code amount} Stück (mit Tagesstufen)? */
    public Deal quoteSell(UUID who, String key, int amount, String day) {
        Ware w = catalog.ware(key);
        if (w == null || !w.sellable()) {
            return new Deal(w == null ? Status.UNKNOWN : Status.NOT_BOUGHT, w, amount, 0, 0);
        }
        if (!Pricing.validAmount(amount)) {
            return new Deal(Status.BAD_AMOUNT, w, amount, 0, 0);
        }
        return new Deal(Status.OK, w, amount, buyback.quote(who, Map.of(w.key(), amount), day).total(), 0);
    }

    /**
     * Verkaufen an den Händler. Die Gegenstände muss das Plugin vorher schon abgenommen haben (geht die Buchung
     * schief, gibt es sie zurück).
     */
    public Deal sell(UUID who, String key, int amount, String day) {
        Ware w = catalog.ware(key);
        if (w == null || !w.sellable()) {
            return new Deal(w == null ? Status.UNKNOWN : Status.NOT_BOUGHT, w, amount, 0, 0);
        }
        if (!Pricing.validAmount(amount)) {
            return new Deal(Status.BAD_AMOUNT, w, amount, 0, 0);
        }
        Buyback.Quote q = buyback.sell(who, Map.of(w.key(), amount), day);
        return new Deal(Status.OK, w, amount, q.total(), 0);
    }

    /** Umsatz des Händlerhauses seit {@code since} (Millisekunden), mit den fünf meistgehandelten Waren. */
    public Turnover turnover(long since) {
        return db.tx(c -> {
            long[] in = Database.one(c, "SELECT COALESCE(SUM(amount), 0), COUNT(*) FROM ledger WHERE reason = ? "
                    + "AND dst = ? AND time >= ?", rs -> new long[] {rs.getLong(1), rs.getLong(2)},
                    REASON_BUY, Bank.SERVER, since);
            long[] out = Database.one(c, "SELECT COALESCE(SUM(amount), 0), COUNT(*) FROM ledger WHERE reason = ? "
                    + "AND src = ? AND time >= ?", rs -> new long[] {rs.getLong(1), rs.getLong(2)},
                    REASON_SELL, Bank.SERVER, since);
            Map<String, long[]> count = new LinkedHashMap<>();
            for (Object[] r : Database.query(c, "SELECT reason, ref FROM ledger WHERE reason IN (?, ?) AND time >= ? "
                    + "AND ref IS NOT NULL", rs -> new Object[] {rs.getString(1), rs.getString(2)},
                    REASON_BUY, REASON_SELL, since)) {
                boolean bought = REASON_BUY.equals(r[0]);
                for (String part : ((String) r[1]).split(",")) {
                    int x = part.lastIndexOf('×');
                    if (x <= 0) {
                        continue;
                    }
                    try {
                        long n = Long.parseLong(part.substring(x + 1));
                        long[] v = count.computeIfAbsent(part.substring(0, x), k -> new long[2]);
                        v[bought ? 0 : 1] += n;
                    } catch (NumberFormatException ignored) {
                        // fremder Bezug – nicht mitzählen
                    }
                }
            }
            List<Top> top = new ArrayList<>();
            count.forEach((k, v) -> top.add(new Top(k, v[0], v[1])));
            top.sort(Comparator.comparingLong((Top t) -> t.bought() + t.sold()).reversed());
            return new Turnover(in[0], in[1], out[0], out[1], List.copyOf(top.subList(0, Math.min(5, top.size()))));
        });
    }

    /** Bezug fürs Kassenbuch: „minecraft:oak_log×64“. */
    public static String ref(String key, long amount) {
        return key + "×" + amount;
    }
}
