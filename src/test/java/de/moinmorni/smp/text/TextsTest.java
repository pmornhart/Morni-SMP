package de.moinmorni.smp.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.Category;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * Jeder Text, den der Code benutzt, gibt es auf Deutsch und Englisch; beide Sprachen haben dieselben Platzhalter,
 * und jeder Text lässt sich lesen (keine kaputte Formatierung).
 */
class TextsTest {

    /** Schlüssel-Literale im Code (wie "ah.sell.title"). */
    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z0-9_-]*(?:\\.[a-z0-9_-]+)+)\"");
    /** Platzhalter, die der Code übergibt: Msg.t("name", …), Msg.money("price", …), Msg.c("item", …). */
    private static final Pattern RESOLVER = Pattern.compile("(?:Msg\\.t|Msg\\.c|Msg\\.money|money)\\(\"([a-z_0-9]+)\"");
    /** Ein Platzhalter-Tag im Text (nicht escaped). */
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)<([a-z_0-9]+)>");

    /** Keine Texte, sondern Einstellungen, Rechte, Datenbank-Schlüssel. */
    private static final List<String> NOT_TEXT = List.of("minecraft:", "morni.smp", "town.version", "town.y",
            "welt.", "waehrung.", "ankauf.", "grundstuecke.", "kopfgeld.", "belohnung.", "anzeige.", "zufallsteleport.",
            "teleport.wartezeit", "teleport.admins", "teleport.zuhause", "auktionshaus.", "auftraege.", "laeden.",
            "marktstaende.", "moinmorni.net", "mornismp.db");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(Path.of("src/main/java"))) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void everyKeyUsedInCodeExists() throws IOException {
        Set<String> missing = new TreeSet<>();
        Set<String> used = new TreeSet<>();
        for (Path p : sources()) {
            if (p.toString().contains("/text/Texts")) {
                continue;
            }
            Matcher m = KEY.matcher(Files.readString(p));
            while (m.find()) {
                String k = m.group(1);
                if (NOT_TEXT.stream().anyMatch(k::startsWith)) {
                    continue;
                }
                used.add(k);
            }
        }
        // Zusammengesetzte Schlüssel
        for (Category c : Category.values()) {
            used.add("cat." + c.id());
        }
        for (AuctionHouse.Sort s : AuctionHouse.Sort.values()) {
            used.add("ah.sort." + s.name().toLowerCase(Locale.ROOT));
        }
        for (String r : List.of("ah_kauf", "ah_abgelaufen", "ah_zurueck", "ankauf_zurueck", "auftrag",
                "auftrag_zurueck", "belohnung", "laden", "stand_geraeumt")) {
            used.add("mail.reason." + r);
        }
        for (String n : List.of("ah.sold", "ah.removed", "ah.expired", "order.filled", "order.delivered",
                "order.expired", "shop.sold", "shop.bought", "stall.warn", "stall.expired", "pay.received")) {
            used.add("notice." + n);
        }
        for (String t : List.of("nah", "mittel", "weit", "nether", "end")) {
            used.add("rtp." + t);
            used.add("rtp." + t + ".lore");
        }
        for (String k : List.of("sichern", "aufgeben", "info", "liste", "vertrauen", "misstrauen", "pvp", "grenzen")) {
            used.add("claim.help." + k);
        }
        for (String k : List.of("geld", "bau", "stadt", "neuladen", "stand", "gs", "info")) {
            used.add("admin.help." + k);
        }
        for (String k : List.of("money", "market", "orders", "buyback", "stall", "shop", "claims", "travel", "homes",
                "tpa", "bounty", "reward", "sidebar")) {
            used.add("help." + k);
        }
        Map<String, String[]> all = Texts.all();
        for (String k : used) {
            if (!all.containsKey(k) && !all.containsKey("notice." + k)) {
                missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "Texte fehlen: " + missing);
    }

    @Test
    void bothLanguagesHaveTheSamePlaceholders() throws IOException {
        Set<String> names = new TreeSet<>();
        for (Path p : sources()) {
            Matcher m = RESOLVER.matcher(Files.readString(p));
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        // aus gespeicherten Nachrichten (Notices)
        names.addAll(List.of("item", "amount", "price", "fee", "buyer", "seller", "total", "delivered", "from",
                "refund", "id", "count", "paid", "name"));
        List<String> wrong = new ArrayList<>();
        Texts.all().forEach((k, v) -> {
            if (!tags(v[0], names).equals(tags(v[1], names))) {
                wrong.add(k + " " + tags(v[0], names) + " ≠ " + tags(v[1], names));
            }
        });
        assertTrue(wrong.isEmpty(), "Platzhalter unterschiedlich: " + wrong);
    }

    private static Set<String> tags(String s, Set<String> names) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(s);
        while (m.find()) {
            if (names.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    void everyTextRendersAndEscapedBracketsStay() {
        Texts.all().forEach((k, v) -> {
            for (String lang : new String[] {"de", "en"}) {
                String plain = PlainTextComponentSerializer.plainText().serialize(Texts.render(lang, k));
                assertTrue(!plain.isBlank() || k.startsWith("sidebar.place") , "leer: " + k);
                assertTrue(!plain.contains("</"), "kaputte Formatierung in " + k + ": " + plain);
            }
        });
        assertEquals("So geht's: /tpa <spieler>",
                PlainTextComponentSerializer.plainText().serialize(Texts.render("de", "tpa.usage")));
    }
}
