package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.market.ui.NoticeSender;
import de.moinmorni.smp.shop.Course;
import de.moinmorni.smp.shop.Courses;
import de.moinmorni.smp.shop.ServerShop;
import de.moinmorni.smp.shop.Ware;
import de.moinmorni.smp.shop.ui.ExchangeUi;
import de.moinmorni.smp.text.Msg;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

/**
 * {@code /smpadmin boerse [zuruecksetzen [ware] | setzen <ware> <prozent>]}: Kurse der Börse ansehen, auf den
 * Grundpreis zurücksetzen oder (zum Testen und Vorführen) von Hand setzen.
 */
final class ExchangeAdmin {

    private static final int SHOWN = 12;

    private final Smp s;

    ExchangeAdmin(Smp s) {
        this.s = s;
    }

    void run(CommandSender sender, String[] args) {
        String sub = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> show(sender);
            case "zuruecksetzen", "zurücksetzen", "reset" -> {
                String key = args.length >= 3 ? key(args[2]) : null;
                if (args.length >= 3 && s.shop.catalog().ware(key) == null) {
                    s.msg.send(sender, "admin.exchange.unknown");
                    return;
                }
                s.msg.send(sender, "admin.exchange.reset", Msg.t("count", s.shop.courses().reset(key)));
                s.plugin.exchangeBoard().refresh();
            }
            case "setzen", "set" -> {
                if (args.length < 4) {
                    s.msg.send(sender, "admin.exchange.usage");
                    return;
                }
                Ware w = s.shop.catalog().ware(key(args[2]));
                if (w == null) {
                    s.msg.send(sender, "admin.exchange.unknown");
                    return;
                }
                double pct;
                try {
                    pct = Double.parseDouble(args[3].replace("%", "").replace(',', '.'));
                } catch (NumberFormatException e) {
                    s.msg.send(sender, "admin.exchange.usage");
                    return;
                }
                s.shop.courses().set(w.key(), pct / 100.0);
                s.msg.send(sender, "admin.exchange.set", Msg.c("item", NoticeSender.itemName(w.key())),
                        Msg.t("pct", Course.percent(s.shop.courses().factor(w.key()))));
                s.plugin.exchangeBoard().refresh();
            }
            default -> s.msg.send(sender, "admin.exchange.usage");
        }
    }

    List<String> tab(String[] args) {
        if (args.length == 2) {
            return List.of("zuruecksetzen", "setzen");
        }
        if (args.length == 3) {
            return s.shop.catalog().wares().values().stream().map(Ware::id)
                    .filter(id -> id.startsWith(args[2].toLowerCase(Locale.ROOT))).limit(40).toList();
        }
        return List.of();
    }

    private void show(CommandSender sender) {
        Course.Rules r = s.shop.courses().rules();
        if (!r.on()) {
            s.msg.send(sender, "admin.exchange.off");
            return;
        }
        List<Courses.Mover> movers = s.shop.courses().movers();
        s.msg.send(sender, "admin.exchange", Msg.t("count", movers.size()), Msg.t("min", r.minPercent()),
                Msg.t("max", r.maxPercent()), Msg.t("halve", r.halveAfter()), Msg.t("hours", r.recoveryHours()));
        String lang = s.msg.lang(sender);
        int n = 0;
        for (Courses.Mover m : movers) {
            Ware w = s.shop.catalog().ware(m.key());
            if (w == null) {
                continue;
            }
            ServerShop.Price pr = s.shop.price(w);
            s.msg.line(sender, "admin.exchange.line", Msg.c("item", NoticeSender.itemName(m.key())),
                    Msg.c("rate", ExchangeUi.courseLine(s, lang, pr)));
            if (++n >= SHOWN) {
                break;
            }
        }
    }

    private static String key(String id) {
        String k = id.toLowerCase(Locale.ROOT);
        return k.contains(":") ? k : "minecraft:" + k;
    }
}
