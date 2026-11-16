package de.moinmorni.smp.shop.ui;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.Orders;
import de.moinmorni.smp.market.ui.NoticeSender;
import de.moinmorni.smp.shop.Catalog;
import de.moinmorni.smp.shop.Course;
import de.moinmorni.smp.shop.Courses;
import de.moinmorni.smp.shop.ServerShop;
import de.moinmorni.smp.shop.Sparkline;
import de.moinmorni.smp.shop.Ware;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.text.Texts;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.IntConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Die Börse als Truhen-Menüs (auch für Bedrock):
 *
 * <pre>
 *   Übersicht   – Steiger, Fallende, Erze & Edelsteine, alle Waren, Suche, Erklärung
 *   Liste       – 36 Waren pro Seite mit Kurs, Preisen und Verlauf (Abteilung oder Suche)
 *   Ware        – Kursverlauf als Säulen (24 Std. oder 7 Tage), Preise, Spielermarkt, Wege zum Handel
 * </pre>
 */
public final class ExchangeUi {

    private static final int PER_PAGE = 36;
    private static final int ROW = 7;
    private static final int[] CATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    /** Erze in der Übersicht (falls im Sortiment). */
    private static final List<String> ORES = List.of("coal", "copper_ingot", "iron_ingot", "gold_ingot", "redstone",
            "lapis_lazuli", "diamond", "netherite_ingot");
    private static final long HOUR = 3_600_000L;
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;

    private final Smp s;

    public ExchangeUi(Smp s) {
        this.s = s;
    }

    private boolean open(Player p) {
        if (!s.settings.shopEnabled) {
            s.msg.send(p, "merchant.off");
            return false;
        }
        return true;
    }

    private int month() {
        return s.now().getMonthValue();
    }

    /** {@code /boerse [ware]}. */
    public void command(Player p, String[] args) {
        if (!open(p)) {
            return;
        }
        if (args.length == 0) {
            main(p);
            return;
        }
        String query = String.join(" ", args);
        List<Ware> found = s.shop.catalog().search(query, month());
        if (found.isEmpty()) {
            s.msg.send(p, "exchange.search.none");
            no(p);
            return;
        }
        if (found.size() == 1 || found.get(0).id().equals(query.toLowerCase(Locale.ROOT).replace(' ', '_'))) {
            detail(p, found.get(0), false, () -> main(p));
            return;
        }
        String shown = query.length() > 20 ? query.substring(0, 20) + "…" : query;
        list(p, found, 0, shown, pg -> list(p, found, pg, shown, null));
    }

    // ---------------------------------------------------------------- Übersicht

    public void main(Player p) {
        if (!open(p)) {
            return;
        }
        String lang = s.msg.lang(p);
        Catalog cat = s.shop.catalog();
        Menu m = new Menu(6, s.msg.text(lang, "exchange.title"));
        frame(m);
        m.show(4, Items.button(Material.CLOCK, s.msg.text(lang, "exchange.head"), List.of(
                s.msg.text(lang, "exchange.head.l1"), s.msg.text(lang, "exchange.head.l2"),
                s.msg.text(lang, "exchange.head.l3"), s.msg.text(lang, "exchange.head.l4"))));
        List<Ware> up = new ArrayList<>();
        List<Ware> down = new ArrayList<>();
        for (Courses.Mover mv : s.shop.courses().movers()) {
            Ware w = cat.ware(mv.key());
            if (w == null || ShopUi.material(w.key(), null) == null || !w.inSeason(month())) {
                continue;
            }
            if (Sparkline.trend(mv.factor()) > 0 && up.size() < ROW) {
                up.add(w);
            } else if (Sparkline.trend(mv.factor()) < 0 && down.size() < ROW) {
                down.add(w);
            }
        }
        row(m, p, 9, Items.button(Material.LIME_DYE, s.msg.text(lang, "exchange.up"),
                List.of(s.msg.text(lang, "exchange.up.lore"))), up, lang);
        row(m, p, 18, Items.button(Material.RED_DYE, s.msg.text(lang, "exchange.down"),
                List.of(s.msg.text(lang, "exchange.down.lore"))), down, lang);
        List<Ware> ores = new ArrayList<>();
        for (String id : ORES) {
            Ware w = cat.ware("minecraft:" + id);
            if (w != null) {
                ores.add(w);
            }
        }
        row(m, p, 27, Items.button(Material.DIAMOND, s.msg.text(lang, "exchange.ores"),
                List.of(s.msg.text(lang, "exchange.ores.lore"))), ores, lang);
        m.set(38, Items.button(Material.CHEST, s.msg.text(lang, "exchange.cats"),
                List.of(s.msg.text(lang, "exchange.cats.lore"))), pl -> {
            click(pl);
            categories(pl);
        });
        m.set(40, Items.button(Material.OAK_HANGING_SIGN, s.msg.text(lang, "exchange.search")), pl -> {
            click(pl);
            askSearch(pl);
        });
        m.show(42, Items.button(Material.BOOK, s.msg.text(lang, "exchange.info"), List.of(
                s.msg.text(lang, "exchange.info.l1"), s.msg.text(lang, "exchange.info.l2"),
                s.msg.text(lang, "exchange.info.l3"), s.msg.text(lang, "exchange.info.l4"),
                s.msg.text(lang, "exchange.info.l5"))));
        bottom(m, p, lang, null);
        m.open(p);
    }

    /** Eine Reihe: Schild vorne, dahinter bis zu sieben Waren (leer: „gerade ruhig“). */
    private void row(Menu m, Player p, int start, ItemStack label, List<Ware> wares, String lang) {
        m.show(start, label);
        if (wares.isEmpty()) {
            m.show(start + 1, Items.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, s.msg.text(lang, "exchange.calm"),
                    List.of(s.msg.text(lang, "exchange.calm.lore"))));
            return;
        }
        for (int i = 0; i < wares.size() && i < 8; i++) {
            Ware w = wares.get(i);
            m.set(start + 1 + i, icon(p, w, lang), pl -> {
                click(pl);
                detail(pl, w, false, () -> main(pl));
            });
        }
    }

    // ---------------------------------------------------------------- Abteilungen, Liste

    public void categories(Player p) {
        String lang = s.msg.lang(p);
        Catalog cat = s.shop.catalog();
        Menu m = new Menu(6, s.msg.text(lang, "exchange.title.cats"));
        frame(m);
        List<Catalog.Category> cats = cat.categories();
        for (int i = 0; i < cats.size() && i < CATEGORY_SLOTS.length; i++) {
            Catalog.Category c = cats.get(i);
            List<Ware> wares = cat.wares(c.id(), month());
            m.set(CATEGORY_SLOTS[i], Items.button(ShopUi.material(c.icon(), Material.CHEST),
                    s.msg.text(lang, "merchant.cat", Msg.t("cat", c.name(lang))),
                    List.of(s.msg.text(lang, "merchant.cat.count", Msg.t("count", wares.size())))), pl -> {
                click(pl);
                list(pl, wares, 0, c.name(s.msg.lang(pl)), null);
            });
        }
        bottom(m, p, lang, () -> main(p));
        m.open(p);
    }

    private void list(Player p, List<Ware> wares, int page, String what, IntConsumer reopen) {
        IntConsumer again = reopen != null ? reopen : pg -> list(p, wares, pg, what, null);
        String lang = s.msg.lang(p);
        int pages = Math.max(1, (wares.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, s.msg.text(lang, "exchange.title.list", Msg.t("what", what), Msg.t("page", pg + 1),
                Msg.t("pages", pages)));
        frame(m);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= wares.size()) {
                m.show(9 + i, null);
                continue;
            }
            Ware w = wares.get(idx);
            if (ShopUi.material(w.key(), null) == null) {
                continue;
            }
            m.set(9 + i, icon(p, w, lang), pl -> {
                click(pl);
                detail(pl, w, false, () -> again.accept(pg));
            });
        }
        if (pg > 0) {
            m.set(48, Items.button(Material.SPECTRAL_ARROW, s.msg.text(lang, "menu.prev")), pl -> {
                click(pl);
                again.accept(pg - 1);
            });
        }
        if (pg + 1 < pages) {
            m.set(50, Items.button(Material.SPECTRAL_ARROW, s.msg.text(lang, "menu.next")), pl -> {
                click(pl);
                again.accept(pg + 1);
            });
        }
        bottom(m, p, lang, () -> main(p));
        m.open(p);
    }

    /** Eine Ware als Symbol: Kurs, Preise jetzt, Verlauf der letzten 24 Stunden. */
    private ItemStack icon(Player p, Ware w, String lang) {
        Material mat = ShopUi.material(w.key(), Material.BARRIER);
        List<Component> lore = new ArrayList<>();
        ServerShop.Price pr = s.shop.price(w);
        lore.add(courseLine(s, lang, pr));
        priceLines(lore, w, pr, lang);
        List<Courses.Point> day = s.shop.courses().history(w.key(), System.currentTimeMillis(), HOUR, 25);
        lore.add(s.msg.text(lang, "exchange.spark", Msg.c("bars", bars(day))));
        lore.add(s.msg.text(lang, "exchange.change", Msg.c("change", change(day.get(0).factor(), pr.factor()))));
        lore.add(Component.empty());
        lore.add(s.msg.text(lang, "exchange.click"));
        return Items.withLore(new ItemStack(mat), lore);
    }

    private void priceLines(List<Component> lore, Ware w, ServerShop.Price pr, String lang) {
        lore.add(w.buyable() ? s.msg.text(lang, "exchange.buy", Msg.money("price", pr.buy(), lang))
                : s.msg.text(lang, "exchange.no-buy"));
        lore.add(w.sellable() ? s.msg.text(lang, "exchange.sell", Msg.money("price", pr.sell(), lang))
                : s.msg.text(lang, "exchange.no-sell"));
    }

    // ---------------------------------------------------------------- Ware: Verlauf als Säulen

    /**
     * Eine Ware an der Börse: oben Preise und Spielermarkt, darunter der Kursverlauf als neun Säulen (grün über dem
     * Grundpreis, rot darunter), unten die Wege zum Handel. {@code week}: 7 Tage statt 24 Stunden.
     */
    public void detail(Player p, Ware w, boolean week, Runnable back) {
        if (!open(p)) {
            return;
        }
        String lang = s.msg.lang(p);
        Material mat = ShopUi.material(w.key(), Material.BARRIER);
        Menu m = new Menu(6, s.msg.text(lang, "exchange.chart.title", Msg.c("item", NoticeSender.itemName(w.key()))));
        ItemStack top = Items.filler(Material.YELLOW_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            m.show(i, top);
            m.show(45 + i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        ServerShop.Price pr = s.shop.price(w);
        long now = System.currentTimeMillis();
        List<Component> head = new ArrayList<>();
        head.add(courseLine(s, lang, pr));
        priceLines(head, w, pr, lang);
        Courses.Volume vol = s.shop.courses().volume(w.key(), s.now().toLocalDate().atStartOfDay(s.settings.zone)
                .toInstant().toEpochMilli());
        head.add(s.msg.text(lang, "exchange.volume", Msg.t("bought", vol.bought()), Msg.t("sold", vol.sold())));
        head.add(Component.empty());
        marketLines(head, w.key(), lang);
        m.show(4, Items.withLore(new ItemStack(mat), head));

        long step = week ? 21 * HOUR : 3 * HOUR;
        List<Courses.Point> pts = s.shop.courses().history(w.key(), now, step, COLUMNS);
        List<Double> values = new ArrayList<>();
        pts.forEach(pt -> values.add(pt.factor()));
        int[] h = Sparkline.heights(values, ROWS);
        for (int col = 0; col < COLUMNS; col++) {
            Courses.Point pt = pts.get(col);
            int trend = Sparkline.trend(pt.factor());
            Material fill = trend > 0 ? Material.LIME_STAINED_GLASS_PANE : trend < 0 ? Material.RED_STAINED_GLASS_PANE
                    : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            Component when = col == COLUMNS - 1 ? s.msg.text(lang, "exchange.chart.now")
                    : s.msg.text(lang, "exchange.chart.ago", Msg.t("time", Msg.duration(now - pt.time(), lang)));
            String rateKey = trend > 0 ? "exchange.chart.rate.up" : trend < 0 ? "exchange.chart.rate.down"
                    : "exchange.chart.rate.flat";
            Component rate = s.msg.text(lang, rateKey, Msg.t("pct", Course.percent(pt.factor())));
            Component name = s.msg.text(lang, "exchange.chart.cell", Msg.c("when", when), Msg.c("rate", rate));
            List<Component> lore = new ArrayList<>();
            if (w.buyable()) {
                lore.add(s.msg.text(lang, "exchange.buy", Msg.money("price", Course.buyPrice(w.buy(), pt.factor()),
                        lang)));
            }
            if (w.sellable()) {
                lore.add(s.msg.text(lang, "exchange.sell", Msg.money("price", Course.sellPrice(w.sell(), pt.factor()),
                        lang)));
            }
            for (int r = 0; r < ROWS; r++) {
                boolean filled = r >= ROWS - h[col];
                m.show(9 + r * 9 + col, Items.button(filled ? fill : Material.BLACK_STAINED_GLASS_PANE, name, lore));
            }
        }
        m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")), pl -> {
            click(pl);
            back.run();
        });
        m.set(46, Items.button(Material.CLOCK, s.msg.text(lang, week ? "exchange.chart.day" : "exchange.chart.week"),
                List.of(s.msg.text(lang, week ? "exchange.chart.showing.week" : "exchange.chart.showing.day"))), pl -> {
            click(pl);
            detail(pl, w, !week, back);
        });
        m.set(48, Items.button(Material.EMERALD, s.msg.text(lang, "exchange.trade"),
                List.of(s.msg.text(lang, "exchange.trade.lore"))), pl -> {
            click(pl);
            s.plugin.shopUi().ware(pl, w, () -> detail(pl, w, week, back));
        });
        m.set(49, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "exchange.ah.search")), pl -> {
            click(pl);
            s.plugin.auctionUi().command(pl, new String[] {"suche", w.id()});
        });
        m.set(50, Items.button(Material.WRITABLE_BOOK, s.msg.text(lang, "exchange.orders.search"),
                List.of(s.msg.text(lang, "exchange.orders.search.lore"))), pl -> {
            click(pl);
            s.plugin.orderUi().command(pl, new String[] {"suche", w.id()});
        });
        m.set(53, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    /** Spielermarkt: Median, günstigstes Angebot im Auktionshaus, bester Kaufauftrag. */
    private void marketLines(List<Component> lore, String key, String lang) {
        boolean any = false;
        OptionalLong median = s.prices.median(key);
        if (median.isPresent()) {
            lore.add(s.msg.text(lang, "exchange.market.median", Msg.money("price", median.getAsLong(), lang)));
            any = true;
        }
        List<AuctionHouse.Listing> cheap = s.ah.cheapest(key, 1);
        if (!cheap.isEmpty()) {
            AuctionHouse.Listing l = cheap.get(0);
            lore.add(s.msg.text(lang, "exchange.market.cheapest", Msg.money("price", l.unitPrice(), lang),
                    Msg.t("amount", l.item().amount())));
            any = true;
        }
        List<Orders.Order> best = s.orders.best(key, 1);
        if (!best.isEmpty()) {
            Orders.Order o = best.get(0);
            lore.add(s.msg.text(lang, "exchange.market.order", Msg.money("price", o.unitPrice(), lang),
                    Msg.t("amount", o.remaining())));
            any = true;
        }
        if (!any) {
            lore.add(s.msg.text(lang, "exchange.market.none"));
        }
    }

    // ---------------------------------------------------------------- Helfer

    /** Kurs als Zeile („Kurs ▲ 112 %“ / „Kurs ▼ 70 %“ / „Kurs 100 %“). */
    public static Component courseLine(Smp s, String lang, ServerShop.Price pr) {
        int t = Sparkline.trend(pr.factor());
        String key = t > 0 ? "exchange.course.up" : t < 0 ? "exchange.course.down" : "exchange.course.flat";
        return s.msg.text(lang, key, Msg.t("pct", pr.percent()));
    }

    /** Balken als Text-Bild. */
    public static Component bars(List<Courses.Point> pts) {
        List<Double> values = new ArrayList<>();
        pts.forEach(pt -> values.add(pt.factor()));
        return Texts.MM.deserialize(Sparkline.bars(values));
    }

    /** Änderung als farbiger Text („+12 %“ grün, „−30 %“ rot, „±0 %“ grau). */
    public static Component change(double from, double to) {
        int c = Sparkline.change(from, to);
        String txt = c > 0 ? "<green>+" + c + " %</green>" : c < 0 ? "<red>−" + Math.abs(c) + " %</red>"
                : "<gray>±0 %</gray>";
        return Texts.MM.deserialize(txt);
    }

    private void askSearch(Player p) {
        p.closeInventory();
        s.msg.send(p, "exchange.search.ask");
        s.prompt.ask(p, text -> command(p, text.strip().split("\\s+")), () -> s.msg.send(p, "menu.cancelled"));
    }

    private void frame(Menu m) {
        ItemStack t = Items.filler(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack b = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            m.show(i, t);
            m.show(45 + i, b);
        }
    }

    /** Untere Reihe: zurück (optional), Händlerhaus, Auktionshaus, Aufträge, Geld, schließen. */
    private void bottom(Menu m, Player p, String lang, Runnable back) {
        if (back != null) {
            m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")), pl -> {
                click(pl);
                back.run();
            });
        }
        m.set(46, Items.button(Material.EMERALD, s.msg.text(lang, "exchange.to-shop")), pl -> {
            click(pl);
            s.plugin.shopUi().main(pl);
        });
        m.set(47, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "exchange.to-ah")), pl -> {
            click(pl);
            s.plugin.auctionUi().command(pl, new String[0]);
        });
        m.set(48, Items.button(Material.WRITABLE_BOOK, s.msg.text(lang, "exchange.to-orders")), pl -> {
            click(pl);
            s.plugin.orderUi().command(pl, new String[0]);
        });
        m.show(49, Items.button(Material.SUNFLOWER, s.msg.text(lang, "merchant.balance",
                Msg.money("money", s.bank.balance(p.getUniqueId()), lang))));
        m.set(53, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }

    private static void no(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }
}
