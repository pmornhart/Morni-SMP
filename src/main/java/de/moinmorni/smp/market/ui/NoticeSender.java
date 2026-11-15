package de.moinmorni.smp.market.ui;

import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.Notices;
import de.moinmorni.smp.text.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Gibt gespeicherte Nachrichten („Dein Diamant wurde verkauft“) an Spieler weiter – sofort oder beim Betreten. */
public final class NoticeSender {

    private static final Set<String> MONEY = Set.of("price", "fee", "refund", "paid");

    private final Notices notices;
    private final Msg msg;

    public NoticeSender(Notices notices, Msg msg) {
        this.notices = notices;
        this.msg = msg;
    }

    /** Ist der Spieler online, bekommt er alles Offene jetzt. */
    public void flush(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            flush(p);
        }
    }

    public void flush(Player p) {
        List<Notices.Notice> list = notices.takeAll(p.getUniqueId());
        if (list.isEmpty()) {
            return;
        }
        String lang = msg.lang(p);
        for (Notices.Notice n : list) {
            msg.send(p, "notice." + n.key(), resolvers(n.args(), lang));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
    }

    static TagResolver[] resolvers(Map<String, String> args, String lang) {
        List<TagResolver> r = new ArrayList<>();
        args.forEach((k, v) -> {
            if (k.equals("item")) {
                r.add(Msg.c("item", itemName(v)));
            } else if (MONEY.contains(k)) {
                long cents;
                try {
                    cents = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    cents = 0;
                }
                r.add(Msg.money(k, cents, lang));
            } else {
                r.add(Msg.t(k, v));
            }
        });
        return r.toArray(new TagResolver[0]);
    }

    /** Name zu einer Preis-Sorte („minecraft:diamond|…“) – der Client übersetzt ihn selbst. */
    public static Component itemName(String key) {
        String base = key.contains("|") ? key.substring(0, key.indexOf('|')) : key;
        Material m = Material.matchMaterial(base);
        if (m == null) {
            return Component.text(base);
        }
        return Component.translatable(m.translationKey());
    }

    /** Für Texte: Preis „pro Stück“ formatiert. */
    public static String unit(long cents, String lang) {
        return Money.format(cents, lang);
    }
}
