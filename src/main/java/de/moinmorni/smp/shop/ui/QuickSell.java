package de.moinmorni.smp.shop.ui;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.Fees;
import de.moinmorni.smp.market.Orders;
import de.moinmorni.smp.market.ui.ItemCodec;
import de.moinmorni.smp.market.ui.NoticeSender;
import de.moinmorni.smp.shop.Course;
import de.moinmorni.smp.shop.Courses;
import de.moinmorni.smp.shop.ServerShop;
import de.moinmorni.smp.shop.Sparkline;
import de.moinmorni.smp.shop.Ware;
import de.moinmorni.smp.text.Msg;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /wert} (was ist das in meiner Hand wert?) und {@code /verkaufen hand|alles} (sofort verkaufen).
 *
 * <p>Sofortverkauf aus der Hand: erst an offene Kaufaufträge von Spielern, die nach Gebühr mehr zahlen als Vroni
 * (beste zuerst; die Ware kommt ins Abholfach des Auftraggebers), was übrig bleibt, an Vroni zum Kurs. Gibt es keinen
 * Käufer, bleibt alles in der Hand.
 */
public final class QuickSell {

    private static final long HOUR = 3_600_000L;
    /** So viele Aufträge werden beim Sofortverkauf höchstens angeschaut. */
    private static final int ORDERS = 20;

    private final Smp s;

    public QuickSell(Smp s) {
        this.s = s;
    }

    // ---------------------------------------------------------------- /wert

    /** {@code /wert [alles]}. */
    public void worth(Player p, String[] args) {
        if (args.length > 0 && isAll(args[0])) {
            worthAll(p);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            s.msg.send(p, "worth.none");
            return;
        }
        String lang = s.msg.lang(p);
        String key = ItemCodec.key(hand);
        int amount = hand.getAmount();
        s.msg.send(p, "worth.head", Msg.t("amount", amount), Msg.c("item", ItemCodec.name(hand)));
        boolean plain = ShopUi.plain(hand);
        Ware w = plain && s.settings.shopEnabled ? s.shop.catalog().ware(hand.getType().getKey().toString()) : null;
        if (w != null) {
            ServerShop.Price pr = s.shop.price(w);
            // der Kurs wirkt nur nach oben auf den Kaufpreis und nur nach unten auf den Ankauf
            int trend = Sparkline.trend(pr.factor());
            Component rate = rate(lang, pr.factor());
            if (w.buyable()) {
                s.msg.line(p, "worth.buy", Msg.money("price", pr.buy(), lang),
                        Msg.c("rate", trend > 0 ? rate : Component.empty()));
            }
            if (w.sellable()) {
                long total = s.shop.quoteSell(p.getUniqueId(), w.key(), amount, s.today()).total();
                s.msg.line(p, "worth.sell", Msg.money("price", pr.sell(), lang), Msg.t("amount", amount),
                        Msg.money("total", total, lang), Msg.c("rate", trend < 0 ? rate : Component.empty()));
            } else {
                s.msg.line(p, "worth.no-sell");
            }
            List<Courses.Point> day = s.shop.courses().history(w.key(), System.currentTimeMillis(), HOUR, 25);
            s.msg.line(p, "worth.spark", Msg.c("bars", ExchangeUi.bars(day)));
        } else {
            s.msg.line(p, plain ? "worth.no-ware" : "worth.special");
        }
        boolean market = false;
        OptionalLong median = s.prices.median(key);
        if (median.isPresent()) {
            s.msg.line(p, "worth.median", Msg.money("price", median.getAsLong(), lang));
            market = true;
        }
        List<AuctionHouse.Listing> cheap = s.ah.cheapest(key, 1);
        if (!cheap.isEmpty()) {
            s.msg.line(p, "worth.cheapest", Msg.money("price", cheap.get(0).unitPrice(), lang));
            market = true;
        }
        List<Orders.Order> best = s.orders.best(key, 1);
        if (!best.isEmpty()) {
            s.msg.line(p, "worth.order", Msg.money("price", best.get(0).unitPrice(), lang),
                    Msg.t("amount", best.get(0).remaining()));
            market = true;
        }
        if (!market) {
            s.msg.line(p, "worth.no-market");
        }
        if ((w != null && w.sellable()) || !best.isEmpty()) {
            s.msg.line(p, "worth.actions");
        }
    }

    private Component rate(String lang, double factor) {
        int t = Sparkline.trend(factor);
        if (t == 0) {
            return Component.empty();
        }
        return s.msg.text(lang, t > 0 ? "worth.rate.up" : "worth.rate.down", Msg.t("pct", Course.percent(factor)));
    }

    private void worthAll(Player p) {
        Map<String, Integer> items = s.plugin.shopUi().sellableInventory(p);
        if (items.isEmpty() || !s.settings.shopEnabled) {
            s.msg.send(p, "worth.all.none");
            return;
        }
        long total = s.buyback.quote(p.getUniqueId(), items, s.today()).total();
        int count = items.values().stream().mapToInt(Integer::intValue).sum();
        s.msg.send(p, "worth.all", Msg.money("total", total, s.msg.lang(p)), Msg.t("count", count),
                Msg.t("kinds", items.size()));
    }

    // ---------------------------------------------------------------- /verkaufen hand|alles

    /** {@code /verkaufen [hand | alles]} – ohne Angabe das Hineinlegen-Menü. */
    public void command(Player p, String[] args) {
        if (!s.settings.shopEnabled && (args.length == 0 || isAll(args[0]))) {
            s.msg.send(p, "merchant.off");
            return;
        }
        if (args.length == 0) {
            s.plugin.buybackUi().open(p);
            return;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (isAll(a)) {
            s.plugin.shopUi().sellInventory(p);
        } else if (a.equals("hand") || a.equals("h")) {
            sellHand(p);
        } else {
            s.plugin.buybackUi().open(p);
        }
    }

    private static boolean isAll(String a) {
        return switch (a.toLowerCase(Locale.ROOT)) {
            case "alles", "all", "inventar", "inventory", "alle" -> true;
            default -> false;
        };
    }

    /** Tab-Vorschläge. */
    public static List<String> tab() {
        return List.of("hand", "alles");
    }

    /** Was in der Hand ist sofort verkaufen: erst an bessere Kaufaufträge, dann an Vroni. */
    public void sellHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            s.msg.send(p, "quicksell.none");
            return;
        }
        String lang = s.msg.lang(p);
        ItemStack template = hand.asOne();
        Component itemName = ItemCodec.name(hand);
        String key = ItemCodec.key(hand);
        int left = hand.getAmount();
        boolean plain = ShopUi.plain(hand);
        Ware w = plain && s.settings.shopEnabled ? s.shop.catalog().ware(hand.getType().getKey().toString()) : null;
        long vroniUnit = w != null && w.sellable() ? s.shop.price(w).sell() : 0;

        // 1) Kaufaufträge, die nach Gebühr mehr zahlen als Vroni
        int delivered = 0;
        long paid = 0;
        long fees = 0;
        for (Orders.Order o : s.orders.best(key, ORDERS)) {
            if (left <= 0) {
                break;
            }
            long net = o.unitPrice() - Fees.of(o.unitPrice(), s.orders.feeBasisPoints());
            if (net <= vroniUnit) {
                break; // beste zuerst – ab hier zahlt Vroni mindestens genauso viel
            }
            if (o.owner().equals(p.getUniqueId()) || !ItemCodec.one(o.item()).isSimilar(template)) {
                continue;
            }
            int removed = ItemCodec.remove(p.getInventory(), template, Math.min(left, o.remaining()));
            if (removed <= 0) {
                break;
            }
            Orders.DeliverResult r;
            try {
                r = s.orders.deliver(o.id(), p.getUniqueId(), p.getName(), removed);
            } catch (RuntimeException e) {
                giveBack(p, template, removed, "auftrag_zurueck");
                throw e;
            }
            giveBack(p, template, removed - r.accepted(), "auftrag_zurueck");
            if (r.status() == Orders.DeliverStatus.OK) {
                delivered += r.accepted();
                paid += r.paid();
                fees += r.fee();
                left -= r.accepted();
                s.reporter.orderFilled(p.getUniqueId());
                s.reporter.purchase(o.owner());
                s.noticeSender.flush(o.owner());
            }
        }
        if (delivered > 0) {
            s.msg.send(p, "quicksell.orders", Msg.t("count", delivered), Msg.c("item", itemName),
                    Msg.money("money", paid, lang), Msg.money("fee", fees, lang));
        }

        // 2) der Rest an Vroni
        int sold = 0;
        if (left > 0 && w != null && w.sellable()) {
            Material mat = hand.getType();
            int have = ShopUi.inventoryCounts(p).getOrDefault(mat, 0);
            int n = Math.min(left, have);
            if (n > 0) {
                ShopUi.take(p, mat, n);
                ServerShop.Deal d;
                try {
                    d = s.shop.sell(p.getUniqueId(), w.key(), n, s.today());
                } catch (RuntimeException e) {
                    giveBack(p, new ItemStack(mat), n, "ankauf_zurueck");
                    throw e;
                }
                if (d.ok()) {
                    sold = n;
                    s.msg.send(p, "quicksell.vroni", Msg.t("count", n), Msg.c("item", NoticeSender.itemName(w.key())),
                            Msg.money("money", d.total(), lang));
                } else {
                    giveBack(p, new ItemStack(mat), n, "ankauf_zurueck");
                }
            }
        }
        if (delivered == 0 && sold == 0) {
            s.msg.send(p, "quicksell.nobody", Msg.c("item", itemName));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        s.msg.line(p, "quicksell.balance", Msg.money("money", s.bank.balance(p.getUniqueId()), lang));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.9f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.6f);
    }

    private void giveBack(Player p, ItemStack template, int amount, String reason) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, template.getMaxStackSize());
            s.deliveries.giveOrMail(p, template.asQuantity(n), reason);
            left -= n;
        }
    }
}
