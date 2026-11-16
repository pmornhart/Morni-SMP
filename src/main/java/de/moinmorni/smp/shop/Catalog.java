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

    /**
     * Sortiment bauen. Was nicht passt, fällt weg und wird über {@code warn} gemeldet: unbekannte Abteilung, doppelte
     * Sorte, weder Kauf- noch Ankaufspreis, Sorte, die {@code known} nicht kennt. Ankaufspreise werden mit
     * {@link Pricing#capSell} gedeckelt.
     */
    public static Catalog of(List<Category> cats, List<Ware> list, int maxSellPercent,
                             java.util.function.Predicate<String> known, Consumer<String> warn) {
        Map<String, Category> catMap = new LinkedHashMap<>();
        for (Category c : cats) {
            if (catMap.putIfAbsent(c.id(), c) != null) {
                warn.accept("Abteilung doppelt: " + c.id());
            }
        }
        Map<String, Ware> wareMap = new LinkedHashMap<>();
        Map<String, List<Ware>> by = new LinkedHashMap<>();
        for (Category c : catMap.values()) {
            by.put(c.id(), new ArrayList<>());
        }
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
            long sell = Pricing.capSell(w.buy(), w.sell(), maxSellPercent);
            if (sell != w.sell()) {
                warn.accept("Ankaufspreis von " + w.key() + " auf " + sell + " Hundertstel gesenkt (höchstens "
                        + maxSellPercent + " % vom Kaufpreis)");
            }
            Ware fixed = new Ware(w.key(), w.category(), Math.max(0, w.buy()), sell, Math.max(1, w.daily()),
                    Math.max(0, w.buyDaily()), w.months());
            if (!fixed.buyable() && !fixed.sellable()) {
                warn.accept("Ware " + w.key() + " hat weder Kauf- noch Ankaufspreis");
                continue;
            }
            wareMap.put(fixed.key(), fixed);
            by.get(fixed.category()).add(fixed);
        }
        Map<String, List<Ware>> frozen = new LinkedHashMap<>();
        by.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return new Catalog(List.copyOf(catMap.values()), Collections.unmodifiableMap(wareMap),
                Collections.unmodifiableMap(frozen));
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
        return out;
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
