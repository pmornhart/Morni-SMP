package de.moinmorni.smp.shop;

import de.moinmorni.smp.market.Buyback;
import de.moinmorni.smp.market.SearchWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Das Sortiment des Händlerhauses: Abteilungen und Waren aus der config.yml, schon geprüft (jede Sorte nur einmal,
 * nur bekannte Abteilungen, Ankaufspreise gedeckelt). Unveränderlich – bei {@code /smpadmin neuladen} kommt ein neues.
 */
public final class Catalog {

    /** Eine Abteilung mit Symbol (Sorte) und Namen auf Deutsch und Englisch. */
    public record Category(String id, String icon, String de, String en) {
        public String name(String lang) {
            return "en".equals(lang) && en != null && !en.isBlank() ? en : de;
        }
    }

    private final List<Category> categories;
    private final Map<String, Ware> wares;
    private final Map<String, List<Ware>> byCategory;

    private Catalog(List<Category> categories, Map<String, Ware> wares, Map<String, List<Ware>> byCategory) {
        this.categories = categories;
        this.wares = wares;
        this.byCategory = byCategory;
    }

    public static Catalog empty() {
        return new Catalog(List.of(), Map.of(), Map.of());
    }

    /** Sortiment ohne Preis-Faktoren (alle 1). */
    public static Catalog of(List<Category> cats, List<Ware> list, int maxSellPercent,
                             java.util.function.Predicate<String> known, Consumer<String> warn) {
        return of(cats, list, maxSellPercent, Map.of(), known, warn);
    }

    /**
     * Sortiment bauen. Was nicht passt, fällt weg und wird über {@code warn} gemeldet: unbekannte Abteilung, doppelte
     * Sorte, weder Kauf- noch Ankaufspreis, Sorte, die {@code known} nicht kennt. Die Preise jeder Abteilung werden mit
     * ihrem Faktor ({@code haendler.faktoren}) multipliziert, Ankaufspreise mit {@link Pricing#capSell} gedeckelt und
     * vom {@link LoopGuard} so weit gesenkt, dass kein Umweg über Werkbank, Ofen oder Abbauen Gewinn bringt.
     */
    public static Catalog of(List<Category> cats, List<Ware> list, int maxSellPercent, Map<String, Double> factors,
                             java.util.function.Predicate<String> known, Consumer<String> warn) {
        Map<String, Category> catMap = new LinkedHashMap<>();
        for (Category c : cats) {
            if (catMap.putIfAbsent(c.id(), c) != null) {
                warn.accept("Abteilung doppelt: " + c.id());
            }
        }
        Map<String, Ware> wareMap = new LinkedHashMap<>();
        for (Ware w : list) {
            if (!catMap.containsKey(w.category())) {
                warn.accept("Ware " + w.key() + ": unbekannte Abteilung " + w.category());
                continue;
            }
            if (wareMap.containsKey(w.key())) {
                warn.accept("Ware doppelt: " + w.key());
                continue;
            }
            if (known != null && !known.test(w.key())) {
                warn.accept("Unbekannte Sorte: " + w.key());
                continue;
            }
            double f = factor(factors, w.category());
            long buy = scale(w.buy(), f);
            long sell = Pricing.capSell(buy, scale(w.sell(), f), maxSellPercent);
            if (sell != scale(w.sell(), f)) {
                warn.accept("Ankaufspreis von " + w.key() + " auf " + sell + " Hundertstel gesenkt (höchstens "
                        + maxSellPercent + " % vom Kaufpreis)");
            }
            Ware fixed = new Ware(w.key(), w.category(), buy, sell, Math.max(1, w.daily()),
                    Math.max(0, w.buyDaily()), w.months());
            if (!fixed.buyable() && !fixed.sellable()) {
                warn.accept("Ware " + w.key() + " hat weder Kauf- noch Ankaufspreis");
                continue;
            }
            wareMap.put(fixed.key(), fixed);
        }
        guard(wareMap, warn);
        Map<String, List<Ware>> by = new LinkedHashMap<>();
        for (Category c : catMap.values()) {
            by.put(c.id(), new ArrayList<>());
        }
        for (Ware w : wareMap.values()) {
            by.get(w.category()).add(w);
        }
        Map<String, List<Ware>> frozen = new LinkedHashMap<>();
        by.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return new Catalog(List.copyOf(catMap.values()), Collections.unmodifiableMap(wareMap),
                Collections.unmodifiableMap(frozen));
    }

    /** Ankaufspreise senken, die eine Geld-Schleife über Werkbank, Ofen oder Abbauen öffnen würden. */
    private static void guard(Map<String, Ware> wares, Consumer<String> warn) {
        Map<String, Long> buy = new LinkedHashMap<>();
        wares.values().forEach(w -> buy.put(w.key(), w.buy()));
        Map<String, Double> cheapest = LoopGuard.cheapest(buy, Recipes.ALL);
        for (Ware w : List.copyOf(wares.values())) {
            long max = LoopGuard.maxSell(w.key(), cheapest);
            if (w.sell() <= max) {
                continue;
            }
            warn.accept("Ankaufspreis von " + w.key() + " auf " + max + " Hundertstel gesenkt (sonst Geld-Schleife: "
                    + LoopGuard.route(w.key(), cheapest, Recipes.ALL) + ")");
            Ware fixed = new Ware(w.key(), w.category(), w.buy(), max, w.daily(), w.buyDaily(), w.months());
            if (fixed.buyable() || fixed.sellable()) {
                wares.put(w.key(), fixed);
            } else {
                wares.remove(w.key());
            }
        }
    }

    private static double factor(Map<String, Double> factors, String category) {
        Double f = factors == null ? null : factors.get(category);
        return f == null || !Double.isFinite(f) || f <= 0 ? 1.0 : f;
    }

    /** Preis × Faktor, auf Hundertstel gerundet (ein Preis über 0 bleibt über 0). */
    static long scale(long price, double f) {
        if (price <= 0) {
            return 0;
        }
        if (f == 1.0) {
            return price;
        }
        double v = Math.min(de.moinmorni.smp.economy.Money.MAX, Math.round(price * f));
        return Math.max(1, (long) v);
    }

    public List<Category> categories() {
        return categories;
    }

    public Category category(String id) {
        for (Category c : categories) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Alle Waren (auch außerhalb der Saison). */
    public Map<String, Ware> wares() {
        return wares;
    }

    public Ware ware(String key) {
        return key == null ? null : wares.get(key);
    }

    public int size() {
        return wares.size();
    }

    /** Waren einer Abteilung, die es in diesem Monat gibt. */
    public List<Ware> wares(String category, int month) {
        List<Ware> out = new ArrayList<>();
        for (Ware w : byCategory.getOrDefault(category, List.of())) {
            if (w.inSeason(month)) {
                out.add(w);
            }
        }
        return out;
    }

    /** Suche (deutsch oder englisch, siehe {@link SearchWords}) über alle Waren dieses Monats. */
    public List<Ware> search(String query, int month) {
        List<Set<String>> terms = SearchWords.terms(query);
        List<Ware> out = new ArrayList<>();
        if (terms.isEmpty()) {
            return out;
        }
        for (Ware w : wares.values()) {
            if (w.inSeason(month) && SearchWords.matches(w.id() + " " + w.id().replace('_', ' '), terms)) {
                out.add(w);
            }
        }
        // Treffer, die genau so heißen, zuerst („Weizen“ → Weizen vor Weizenkörnern), dann kürzere Namen
        out.sort(java.util.Comparator.comparingInt((Ware w) -> rank(w.id(), terms)).thenComparingInt(w -> w.id().length()));
        return out;
    }

    /** Die Ware, die genau so heißt (deutsch oder englisch, z. B. „diamant“ → Diamant), oder null. */
    public Ware exact(String query, int month) {
        List<Set<String>> terms = SearchWords.terms(query);
        for (Ware w : search(query, month)) {
            if (rank(w.id(), terms) == 0) {
                return w;
            }
        }
        return null;
    }

    private static int rank(String id, List<Set<String>> terms) {
        String joined = id.replace('_', ' ');
        for (Set<String> variants : terms) {
            for (String v : variants) {
                String t = v.replace('_', ' ').strip();
                if (joined.equals(t)) {
                    return 0;
                }
            }
        }
        return 1;
    }

    /** Was der Händler ankauft – für den Ankauf (Hineinlegen-Menü und {@code /verkaufen}). */
    public List<Buyback.Offer> sellOffers() {
        List<Buyback.Offer> out = new ArrayList<>();
        for (Ware w : wares.values()) {
            if (w.sellable()) {
                out.add(new Buyback.Offer(w.key(), w.sell(), w.daily()));
            }
        }
        return out;
    }
}
