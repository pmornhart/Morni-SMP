package de.moinmorni.smp.market;

import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Der Ankauf der Stadt: Der Server kauft nur Feldfrüchte und Beute von Monstern, zu kleinen festen Preisen – die
 * Geldquelle für alle, die noch nichts zu verkaufen haben. Damit niemand mit einer Riesenfarm das Geld der Stadt
 * flutet, sinkt der Preis pro Spieler und Tag: Die ersten {@code daily} Stück gibt es voll, danach weniger (Stufen).
 */
public final class Buyback {

    /** Ein Angebot der Stadt: Sorte, Preis pro Stück (Hundertstel), volle Menge pro Tag. */
    public record Offer(String key, long unitPrice, int daily) {
    }

    /** Tagesmenge, wenn eine Ware keine eigene hat. */
    public static final int DEFAULT_DAILY = 256;

    /** Stufe: ab {@code fromDaily} × Tagesmenge zahlt die Stadt noch {@code percent} %. */
    public record Tier(double fromDaily, int percent) {
    }

    /** Eine Zeile der Abrechnung. */
    public record Line(String key, int amount, long payout) {
    }

    /** Abrechnung: Summe und Zeilen (nur Sorten, die die Stadt kauft). */
    public record Quote(long total, List<Line> lines) {
    }

    /**
     * Börse (gesetzt vom Händlerhaus): was die Stadt gerade pro Stück zahlt und was nach einem Verkauf mit dem Kurs
     * passiert. Ohne Börse gilt der feste Preis des Angebots.
     */
    public interface Market {
        /** Preis pro Stück jetzt (Hundertstel). */
        long unitPrice(Offer o);

        /** Im Auftrag des Verkaufs: verkaufte Menge verbuchen. */
        void sold(Connection c, String key, int amount) throws SQLException;
    }

    private final Database db;
    private final Bank bank;
    private volatile Market market;
    private volatile Map<String, Offer> offers = Map.of();
    private volatile List<Tier> tiers = List.of(new Tier(0, 100));

    public Buyback(Database db, Bank bank) {
        this.db = db;
        this.bank = bank;
    }

    /** Preise und Stufen aus der config.yml. */
    public void configure(List<Offer> list, List<Tier> tierList) {
        Map<String, Offer> m = new LinkedHashMap<>();
        for (Offer o : list) {
            if (o.unitPrice() > 0 && o.daily() > 0) {
                m.put(o.key(), o);
            }
        }
        this.offers = Map.copyOf(m);
        List<Tier> t = new ArrayList<>(tierList.isEmpty() ? List.of(new Tier(0, 100)) : tierList);
        t.sort(Comparator.comparingDouble(Tier::fromDaily));
        if (t.get(0).fromDaily() > 0) {
            t.add(0, new Tier(0, 100));
        }
        this.tiers = List.copyOf(t);
    }

    /** Börse anschließen (null = feste Preise). */
    public void market(Market m) {
        this.market = m;
    }

    /** Preis pro Stück jetzt (mit Kurs). */
    public long unitPrice(Offer o) {
        Market m = market;
        return m == null ? o.unitPrice() : m.unitPrice(o);
    }

    public Map<String, Offer> offers() {
        return offers;
    }

    public List<Tier> tiers() {
        return tiers;
    }

    /**
     * Auszahlung (Hundertstel) für {@code amount} Stück, wenn heute schon {@code soldBefore} verkauft wurden.
     * Jedes Stück zählt in der Stufe, in die es fällt; abgerundet wird erst am Ende.
     */
    public static long price(long unitPrice, int daily, long soldBefore, int amount, List<Tier> tiers) {
        if (amount <= 0 || unitPrice <= 0 || daily <= 0) {
            return 0;
        }
        long start = soldBefore;
        long end = soldBefore + amount;
        long hundredths = 0; // Hundertstel × Prozent
        for (int i = 0; i < tiers.size(); i++) {
            long from = (long) Math.floor(tiers.get(i).fromDaily() * daily);
            long to = i + 1 < tiers.size() ? (long) Math.floor(tiers.get(i + 1).fromDaily() * daily) : Long.MAX_VALUE;
            long overlap = Math.min(end, to) - Math.max(start, from);
            if (overlap > 0) {
                hundredths += overlap * unitPrice * tiers.get(i).percent();
            }
        }
        return hundredths / 100;
    }

    /** Heute schon verkauft: Sorte → Stück. */
    public Map<String, Long> soldToday(UUID who, String day) {
        return db.tx(c -> soldToday(c, who, day));
    }

    private static Map<String, Long> soldToday(Connection c, UUID who, String day) throws SQLException {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Object[] r : Database.query(c, "SELECT item_key, sold FROM buyback_daily WHERE uuid = ? AND day = ?",
                rs -> new Object[] {rs.getString(1), rs.getLong(2)}, who, day)) {
            m.put((String) r[0], (Long) r[1]);
        }
        return m;
    }

    /** Was gäbe es jetzt für diese Gegenstände (Sorte → Stück)? */
    public Quote quote(UUID who, Map<String, Integer> items, String day) {
        return db.tx(c -> quote(soldToday(c, who, day), items));
    }

    private Quote quote(Map<String, Long> sold, Map<String, Integer> items) {
        List<Line> lines = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            Offer o = offers.get(e.getKey());
            if (o == null || e.getValue() <= 0) {
                continue;
            }
            long p = price(unitPrice(o), o.daily(), sold.getOrDefault(o.key(), 0L), e.getValue(), tiers);
            lines.add(new Line(o.key(), e.getValue(), p));
            total += p;
        }
        return new Quote(total, lines);
    }

    /** Bezug fürs Kassenbuch: „minecraft:wheat×64,minecraft:bone×12“ (höchstens 500 Zeichen). */
    static String ref(Quote q) {
        StringBuilder sb = new StringBuilder();
        for (Line l : q.lines()) {
            String part = l.key() + "×" + l.amount();
            if (sb.length() + part.length() + 1 > 500) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(part);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** Verkaufen: Zähler hoch, Geld vom Server. Die Gegenstände muss das Plugin vorher schon abgenommen haben. */
    public Quote sell(UUID who, Map<String, Integer> items, String day) {
        return db.tx(c -> {
            Quote q = quote(soldToday(c, who, day), items);
            for (Line l : q.lines()) {
                Database.update(c, "INSERT INTO buyback_daily(uuid, day, item_key, sold) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT(uuid, day, item_key) DO UPDATE SET sold = sold + excluded.sold",
                        who, day, l.key(), l.amount());
            }
            if (q.total() > 0) {
                bank.move(c, Bank.SERVER, who.toString(), q.total(), "ankauf", ref(q));
            }
            Market m = market;
            if (m != null) {
                for (Line l : q.lines()) {
                    m.sold(c, l.key(), l.amount());
                }
            }
            Database.update(c, "DELETE FROM buyback_daily WHERE day < ?", day);
            return q;
        });
    }
}
