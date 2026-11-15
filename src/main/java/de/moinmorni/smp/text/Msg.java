package de.moinmorni.smp.text;

import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.net.Network;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Texte an Spieler – immer in ihrer Sprache (aus dem Netzwerk, sonst Deutsch). */
public final class Msg {

    private final Network net;

    public Msg(Network net) {
        this.net = net;
    }

    public Network network() {
        return net;
    }

    /** „de“ oder „en“. Die Konsole liest Deutsch. */
    public String lang(CommandSender s) {
        return s instanceof Player p ? net.language(p.getUniqueId()) : "de";
    }

    public Component text(String lang, String key, TagResolver... r) {
        return Texts.render(lang, key, r);
    }

    public Component text(CommandSender s, String key, TagResolver... r) {
        return Texts.render(lang(s), key, r);
    }

    /** Roher Text (ohne Formatierung), z. B. für Schilder. */
    public String raw(String lang, String key) {
        return Texts.raw(lang, key);
    }

    /** Mit Vorsilbe „MorniSMP »“. */
    public void send(CommandSender s, String key, TagResolver... r) {
        String lang = lang(s);
        s.sendMessage(Texts.render(lang, "prefix").append(Texts.render(lang, key, r)));
    }

    /** Ohne Vorsilbe (Listen, Karten). */
    public void line(CommandSender s, String key, TagResolver... r) {
        s.sendMessage(text(s, key, r));
    }

    public void bar(Player p, String key, TagResolver... r) {
        p.sendActionBar(text(p, key, r));
    }

    public void title(Player p, String titleKey, String subKey, TagResolver... r) {
        p.showTitle(Title.title(text(p, titleKey, r), subKey == null ? Component.empty() : text(p, subKey, r),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(400))));
    }

    // ---------- Platzhalter ----------

    /** Text von außen (Spielernamen …) – wird nie als Formatierung gelesen. */
    public static TagResolver t(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver c(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public static TagResolver money(String name, long cents, String lang) {
        return Placeholder.unparsed(name, Money.format(cents, lang));
    }

    public TagResolver money(String name, long cents, CommandSender s) {
        return money(name, cents, lang(s));
    }

    /** Dauer lesbar: „2 Std. 5 Min.“ / „2 h 5 min“. */
    public static String duration(long ms, String lang) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86_400;
        long h = s % 86_400 / 3_600;
        long m = s % 3_600 / 60;
        boolean en = "en".equals(lang);
        if (d > 0) {
            return d + (en ? " d " : " Tg. ") + h + (en ? " h" : " Std.");
        }
        if (h > 0) {
            return h + (en ? " h " : " Std. ") + m + (en ? " min" : " Min.");
        }
        if (m > 0) {
            return m + (en ? " min" : " Min.");
        }
        return s + (en ? " s" : " Sek.");
    }
}
