package de.moinmorni.smp.shop;

import java.util.List;

/**
 * Kleine Kurs-Bilder aus Zeichen – ohne Server, mit Tests. Für Chat und Tooltips eine Zeile aus Balken
 * („▁▂▃▅▇“, grün über dem Grundpreis, rot darunter), für das Börsen-Menü die Höhe jeder Säule (1 … {@code rows}).
 */
public final class Sparkline {

    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    /** Unter einem halben Prozent Abstand gilt ein Kurs als „beim Grundpreis“. */
    private static final double FLAT = 0.005;

    private Sparkline() {
    }

    /** Richtung eines Kurses: 1 über dem Grundpreis, -1 darunter, 0 beim Grundpreis. */
    public static int trend(double factor) {
        return factor > 1 + FLAT ? 1 : factor < 1 - FLAT ? -1 : 0;
    }

    /**
     * Höhe jeder Säule ({@code 1 … rows}). Der Bereich reicht immer über den Grundpreis (1,0), damit man sieht, wo er
     * liegt; ohne Bewegung sind alle Säulen gleich hoch (in der Mitte).
     */
    public static int[] heights(List<Double> values, int rows) {
        int[] out = new int[values.size()];
        double lo = 1.0;
        double hi = 1.0;
        for (double v : values) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        for (int i = 0; i < out.length; i++) {
            if (hi - lo < 1e-9) {
                out[i] = Math.max(1, (rows + 1) / 2);
            } else {
                out[i] = 1 + (int) Math.round((values.get(i) - lo) / (hi - lo) * (rows - 1));
            }
        }
        return out;
    }

    /** Eine Zeile Balken als MiniMessage (grün/rot/grau je Richtung, gleiche Farben zusammengefasst). */
    public static String bars(List<Double> values) {
        int[] h = heights(values, BARS.length);
        StringBuilder sb = new StringBuilder();
        String open = null;
        for (int i = 0; i < h.length; i++) {
            String colour = switch (trend(values.get(i))) {
                case 1 -> "green";
                case -1 -> "red";
                default -> "gray";
            };
            if (!colour.equals(open)) {
                if (open != null) {
                    sb.append("</").append(open).append('>');
                }
                sb.append('<').append(colour).append('>');
                open = colour;
            }
            sb.append(BARS[h[i] - 1]);
        }
        if (open != null) {
            sb.append("</").append(open).append('>');
        }
        return sb.toString();
    }

    /** Änderung in ganzen Prozent von {@code from} nach {@code to} (z. B. +12, -30). */
    public static int change(double from, double to) {
        if (from <= 0) {
            return 0;
        }
        return (int) Math.round((to / from - 1) * 100);
    }
}
