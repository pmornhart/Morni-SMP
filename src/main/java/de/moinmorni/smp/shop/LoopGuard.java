package de.moinmorni.smp.shop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Schleifen-Wächter: Er rechnet für jede Sorte aus, wie billig man sie bekommt – beim Händler kaufen oder aus
 * gekauften Sachen über Werkbank, Ofen oder Abbauen machen ({@link Recipes}) – und meldet Ankaufspreise, die darüber
 * liegen. So eine Ware wäre eine Geld-Schleife: kaufen, umwandeln, verkaufen, Gewinn – beliebig oft.
 *
 * <p>Gerechnet wird mit den Grundpreisen. Weil der Kurs der Börse Kaufpreise nur hebt und Ankaufspreise nur senkt
 * ({@link Course}), gilt das Ergebnis zu jedem Kurs.
 */
public final class LoopGuard {

    /** Höchstens so viele Runden (jede Runde kann einen Umweg mehr einrechnen). */
    private static final int ROUNDS = 64;

    private LoopGuard() {
    }

    /**
     * Billigster Weg zu jeder Sorte (Hundertstel pro Stück). Sorten, an die man nicht kommt, fehlen in der Karte.
     *
     * @param buy Kaufpreise beim Händler (Sorte → Hundertstel; 0 = nicht zu kaufen)
     */
    public static Map<String, Double> cheapest(Map<String, Long> buy, List<Recipes.Recipe> recipes) {
        Map<String, Double> cost = new HashMap<>();
        buy.forEach((k, v) -> {
            if (v != null && v > 0) {
                cost.put(k, v.doubleValue());
            }
        });
        for (int round = 0; round < ROUNDS; round++) {
            boolean changed = false;
            for (Recipes.Recipe r : recipes) {
                double total = 0;
                boolean all = true;
                for (Map.Entry<String, Integer> in : r.in().entrySet()) {
                    Double c = cost.get(in.getKey());
                    if (c == null) {
                        all = false;
                        break;
                    }
                    total += c * in.getValue();
                }
                if (!all) {
                    continue;
                }
                double each = total / r.count();
                Double old = cost.get(r.out());
                if (old == null || each < old - 1e-9) {
                    cost.put(r.out(), each);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return cost;
    }

    /**
     * Höchster erlaubter Ankaufspreis für eine Ware: echt weniger als der billigste Weg zu ihr. {@link Long#MAX_VALUE},
     * wenn man nicht über den Händler an sie kommt.
     */
    public static long maxSell(String key, Map<String, Double> cheapest) {
        Double c = cheapest.get(key);
        if (c == null) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, (long) Math.ceil(c - 1e-9) - 1);
    }

    /** Der billigste Umweg zu einer Sorte als Text („9× minecraft:iron_nugget (werkbank)“) – für Warnungen. */
    public static String route(String key, Map<String, Double> cheapest, List<Recipes.Recipe> recipes) {
        Double target = cheapest.get(key);
        if (target == null) {
            return "";
        }
        for (Recipes.Recipe r : recipes) {
            if (!r.out().equals(key)) {
                continue;
            }
            double total = 0;
            boolean all = true;
            for (Map.Entry<String, Integer> in : r.in().entrySet()) {
                Double c = cheapest.get(in.getKey());
                if (c == null) {
                    all = false;
                    break;
                }
                total += c * in.getValue();
            }
            if (all && Math.abs(total / r.count() - target) < 1e-6) {
                StringBuilder sb = new StringBuilder();
                r.in().forEach((k, v) -> sb.append(sb.isEmpty() ? "" : " + ").append(v).append("× ")
                        .append(k.substring(k.indexOf(':') + 1)));
                return sb + " → " + r.count() + "× (" + r.how() + ")";
            }
        }
        return "Händler";
    }
}
