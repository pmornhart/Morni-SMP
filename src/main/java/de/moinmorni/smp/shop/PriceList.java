package de.moinmorni.smp.shop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Neue Preisliste für bestehende Server: Steht in der config.yml eines Servers noch die <b>unveränderte</b> Warenliste
 * einer älteren Fassung ({@code haendler.waren}), wird sie beim Start entfernt – dann gilt die mitgelieferte Liste mit
 * den neuen Preisen. Hat der Betreiber die Liste geändert, bleibt seine (mit Hinweis im Log). Erkannt wird die alte Liste
 * an ihrem Fingerabdruck (SHA-256 über alle Waren und Werte). {@code haendler.preisliste} merkt sich, dass es erledigt
 * ist.
 */
public final class PriceList {

    /** Fassung der mitgelieferten Preisliste (0.2.0 = 2, 0.3.0 = 3 mit den teuren Erzen). */
    public static final int CURRENT = 3;
    /** Fingerabdrücke der unveränderten Warenlisten älterer Fassungen. */
    static final Set<String> OLD = Set.of(
            // 0.2.0 (Händlerhaus)
            "d807837986c70fa8cd8a0d2d5f40e0e807dd40927626dd951fad71d13a86ee1d");

    /** Was beim Start passiert ist. */
    public enum Result {
        /** nichts zu tun (schon aktuell oder keine eigene Liste in der Datei) */
        NONE,
        /** alte, unveränderte Liste entfernt – die neuen Preise gelten */
        UPDATED,
        /** eigene, geänderte Liste bleibt */
        KEPT
    }

    private PriceList() {
    }

    /**
     * Prüft die config.yml (nur die Datei, nicht die Vorgaben) und entfernt eine alte, unveränderte Warenliste. Ändert
     * {@code file} – speichern muss der Aufrufer (nur bei {@link Result#UPDATED} oder {@link Result#KEPT}).
     */
    public static Result migrate(FileConfiguration file) {
        if (file.getInt("haendler.preisliste", 0) >= CURRENT && file.contains("haendler.preisliste", true)) {
            return Result.NONE;
        }
        if (!file.contains("haendler.waren", true)) {
            return Result.NONE;
        }
        ConfigurationSection waren = file.getConfigurationSection("haendler.waren");
        file.set("haendler.preisliste", CURRENT);
        if (waren != null && OLD.contains(fingerprint(waren))) {
            file.set("haendler.waren", null);
            return Result.UPDATED;
        }
        return Result.KEPT;
    }

    /** Fingerabdruck einer Warenliste: Abteilung, Ware und jeder Wert, in fester Reihenfolge. */
    public static String fingerprint(ConfigurationSection waren) {
        StringBuilder sb = new StringBuilder();
        for (String cat : new TreeSet<>(waren.getKeys(false))) {
            ConfigurationSection c = waren.getConfigurationSection(cat);
            if (c == null) {
                continue;
            }
            for (String ware : new TreeSet<>(c.getKeys(false))) {
                ConfigurationSection w = c.getConfigurationSection(ware);
                if (w == null) {
                    continue;
                }
                sb.append(cat).append('.').append(ware).append('{');
                for (String k : new TreeSet<>(w.getKeys(false))) {
                    Object v = w.get(k);
                    sb.append(k).append('=').append(v instanceof Number n ? normal(n) : String.valueOf(v)).append(';');
                }
                sb.append('}');
            }
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 2 und 2.0 sind derselbe Preis. */
    private static String normal(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
