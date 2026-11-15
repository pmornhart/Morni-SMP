package de.moinmorni.smp.economy;

/**
 * Taler als ganze Zahl in Hundertsteln ({@code 150} = 1,50 ₮) – so rundet nichts falsch. Anzeige ohne „,00“ bei
 * glatten Beträgen; Eingaben verstehen „12“, „12,5“, „12.50“, „1.500“, „1k“, „2,5k“, „1m“.
 */
public final class Money {

    /** Hundertstel pro Taler. */
    public static final long CENTS = 100;
    /** Größter Betrag, den MorniSMP annimmt (10 Mrd. Taler) – weit weg von jedem Überlauf. */
    public static final long MAX = 1_000_000_000_000L;

    private static volatile String symbol = "₮";

    private Money() {
    }

    /** Währungszeichen aus der config.yml. */
    public static void symbol(String s) {
        symbol = s == null || s.isBlank() ? "₮" : s.strip();
    }

    public static String symbol() {
        return symbol;
    }

    /** Taler (auch mit Komma) → Hundertstel; aus der config.yml. */
    public static long ofTaler(double taler) {
        return Math.round(taler * CENTS);
    }

    /** „1.234,50 ₮“ (de) bzw. „1,234.50 ₮“ (en). */
    public static String format(long cents, String lang) {
        return amount(cents, lang) + " " + symbol;
    }

    /** Nur die Zahl, ohne Zeichen. */
    public static String amount(long cents, String lang) {
        boolean en = "en".equals(lang);
        char group = en ? ',' : '.';
        char decimal = en ? '.' : ',';
        boolean negative = cents < 0;
        long abs = Math.abs(cents);
        long whole = abs / CENTS;
        long rest = abs % CENTS;
        StringBuilder sb = new StringBuilder(Long.toString(whole));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, group);
        }
        if (rest != 0) {
            sb.append(decimal).append(rest < 10 ? "0" : "").append(rest);
        }
        if (negative) {
            sb.insert(0, '-');
        }
        return sb.toString();
    }

    /** Kurz für Schilder und Leisten: „1,2k“, „3,4M“. */
    public static String compact(long cents, String lang) {
        long taler = cents / CENTS;
        if (Math.abs(taler) < 10_000) {
            return format(cents, lang);
        }
        String d = "en".equals(lang) ? "." : ",";
        if (Math.abs(taler) < 1_000_000) {
            return (taler / 1000) + d + (Math.abs(taler) % 1000 / 100) + "k " + symbol;
        }
        return (taler / 1_000_000) + d + (Math.abs(taler) % 1_000_000 / 100_000) + "M " + symbol;
    }

    /**
     * Eingabe eines Spielers → Hundertstel, oder -1 wenn sie nicht passt (leer, negativ, zu viele Nachkommastellen,
     * zu groß). Höchstens zwei Nachkommastellen; drei Ziffern nach einem einzelnen Trenner gelten als Tausender
     * („1.500“ = 1500).
     */
    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.strip().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace("_", "");
        for (String suffix : new String[] {symbol.toLowerCase(java.util.Locale.ROOT), "taler", "₮"}) {
            if (!suffix.isEmpty() && s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
            }
        }
        long factor = 1;
        if (s.endsWith("k")) {
            factor = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            factor = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty() || !s.matches("[0-9.,]+") || !Character.isDigit(s.charAt(0))) {
            return -1;
        }
        String intPart;
        String fracPart = "";
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int dots = count(s, '.');
        int commas = count(s, ',');
        if (dots > 0 && commas > 0) {
            // beide: der letzte Trenner ist das Komma der Nachkommastellen
            int dec = Math.max(lastDot, lastComma);
            char other = dec == lastDot ? ',' : '.';
            if (count(s.substring(dec + 1), other) > 0 || count(s.substring(0, dec), s.charAt(dec)) > 0) {
                return -1;
            }
            intPart = s.substring(0, dec).replace(String.valueOf(other), "");
            fracPart = s.substring(dec + 1);
        } else if (dots + commas == 1) {
            int sep = Math.max(lastDot, lastComma);
            String after = s.substring(sep + 1);
            if (after.length() == 3) {
                intPart = s.substring(0, sep) + after; // Tausender
            } else {
                intPart = s.substring(0, sep);
                fracPart = after;
            }
        } else if (dots + commas > 1) {
            // nur Tausender-Trenner, je genau drei Ziffern
            String[] groups = s.split("[.,]");
            for (int i = 1; i < groups.length; i++) {
                if (groups[i].length() != 3) {
                    return -1;
                }
            }
            intPart = String.join("", groups);
        } else {
            intPart = s;
        }
        if (intPart.isEmpty() || fracPart.length() > 2 && factor == 1 || fracPart.length() > 6) {
            return -1;
        }
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(intPart + (fracPart.isEmpty() ? "" : "." + fracPart))
                    .multiply(java.math.BigDecimal.valueOf(factor * CENTS));
            if (v.scale() > 0 && v.stripTrailingZeros().scale() > 0) {
                return -1; // nach k/m noch Bruchteile von Hundertsteln
            }
            long cents = v.longValueExact();
            return cents <= 0 || cents > MAX ? -1 : cents;
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
