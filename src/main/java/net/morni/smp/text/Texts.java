package net.morni.smp.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Alle Texte für Spieler, auf Deutsch und Englisch, als MiniMessage. Eigene Farben: {@code <em>} Smaragd,
 * {@code <au>} Gold, {@code <bl>} Weiß-Blau, {@code <mu>} gedämpft. Namen von Spielern und andere Eingaben kommen
 * immer als Platzhalter ({@link Msg#t}) hinein – nie als Formatierung.
 */
public final class Texts {

    public static final int EMERALD = 0x2BB673;
    public static final int GOLD = 0xF2B632;
    public static final int BLUE = 0x5DA9E9;
    public static final int MUTED = 0xA7B1B8;

    /** MiniMessage mit unseren Farben. */
    public static final MiniMessage MM = MiniMessage.builder()
            .editTags(t -> t.resolvers(
                    TagResolver.resolver("em", Tag.styling(TextColor.color(EMERALD))),
                    TagResolver.resolver("au", Tag.styling(TextColor.color(GOLD))),
                    TagResolver.resolver("bl", Tag.styling(TextColor.color(BLUE))),
                    TagResolver.resolver("mu", Tag.styling(TextColor.color(MUTED)))))
            .build();

    private static final Map<String, String[]> TEXTS = new LinkedHashMap<>();

    private Texts() {
    }

    static void t(String key, String de, String en) {
        if (TEXTS.put(key, new String[] {de, en}) != null) {
            throw new IllegalStateException("Text doppelt: " + key);
        }
    }

    static {
        TextsGeneral.register();
        TextsMarket.register();
        TextsWorld.register();
        TextsShop.register();
    }

    /** Alle Schlüssel (für den Test, dass jeder benutzte Text existiert). */
    public static Map<String, String[]> all() {
        return Collections.unmodifiableMap(TEXTS);
    }

    /** Rohtext in der Sprache (Englisch fällt auf Deutsch zurück, unbekannt → Schlüssel selbst). */
    public static String raw(String lang, String key) {
        String[] v = TEXTS.get(key);
        if (v == null) {
            return key;
        }
        return "en".equals(lang) && v[1] != null ? v[1] : v[0];
    }

    public static Component render(String lang, String key, TagResolver... r) {
        return MM.deserialize(raw(lang, key), r);
    }
}
