package net.morni.smp.shop;

import java.util.Locale;
import java.util.Set;

/**
 * Eine Ware im Händlerhaus.
 *
 * @param key      Sorte, z. B. „minecraft:oak_log“
 * @param category Kennung der Abteilung
 * @param buy      Preis pro Stück, den Spieler beim Händler zahlen (Hundertstel; 0 = nicht zu kaufen)
 * @param sell     Preis pro Stück, den der Händler zahlt (Hundertstel; 0 = kauft er nicht) – immer deutlich unter
 *                 {@code buy}, siehe {@link Pricing#capSell}
 * @param daily    so viele Stück pro Spieler und Tag nimmt der Händler zum vollen Preis (danach Stufen des Ankaufs)
 * @param buyDaily so viele Stück darf jeder pro Tag kaufen (0 = ohne Grenze)
 * @param months   nur in diesen Monaten (1–12) im Angebot; leer = immer (Saison-Ware)
 */
public record Ware(String key, String category, long buy, long sell, int daily, int buyDaily, Set<Integer> months) {

    public Ware {
        key = key.toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }
        months = Set.copyOf(months);
    }

    public boolean buyable() {
        return buy > 0;
    }

    public boolean sellable() {
        return sell > 0;
    }

    /** Gibt es die Ware in diesem Monat (1–12)? */
    public boolean inSeason(int month) {
        return months.isEmpty() || months.contains(month);
    }

    /** Sorte ohne „minecraft:“ (für Suche und Anzeige). */
    public String id() {
        return key.substring(key.indexOf(':') + 1);
    }
}
