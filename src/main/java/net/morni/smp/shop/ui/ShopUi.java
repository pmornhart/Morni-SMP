package net.morni.smp.shop.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import net.morni.smp.Smp;
import net.morni.smp.market.Buyback;
import net.morni.smp.market.ui.NoticeSender;
import net.morni.smp.shop.Catalog;
import net.morni.smp.shop.Pricing;
import net.morni.smp.shop.ServerShop;
import net.morni.smp.shop.Ware;
import net.morni.smp.text.Msg;
import net.morni.smp.ui.Items;
import net.morni.smp.ui.Menu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Das Händlerhaus als Truhen-Menüs (geht mit einfachen Klicks, also auch auf Bedrock):
 *
 * <pre>
 *   Übersicht   – zehn Abteilungen, Suche, Ankauf zum Hineinlegen, ganzes Inventar verkaufen, Spieler-Markt, Geld
 *   Abteilung   – 36 Waren pro Seite, jede mit Kauf- und Ankaufspreis; Klick = Ware, Umschalt-Klick = Stapel kaufen
 *   Ware        – kaufen (1 … 64, Stapel, „so viel wie passt“, eigene Menge) und verkaufen (… „alle“)
 * </pre>
 *
 * Gekauft wird erst gebucht, dann ausgegeben; verkauft wird erst abgenommen, dann gebucht (geht die Buchung schief,
 * kommt alles zurück). Ab {@code haendler.bestaetigen-ab} fragt die Händlerin nach.
 */
public final class ShopUi {

    private static final int PER_PAGE = 36;
    private static final int[] CATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int[] AMOUNTS_64 = {1, 8, 16, 32, 64};
    private static final int[] AMOUNTS_16 = {1, 4, 8, 16, 32};
    private static final int[] AMOUNTS_1 = {1, 2, 3, 4, 5};
    private static final int[] STACKS = {2, 4, 8, 16};

    private final Smp s;

    public ShopUi(Smp s) {
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

    /** {@code /shop [abteilung | suche <text> | <text>]}. */
    public void command(Player p, String[] args) {
        if (!open(p)) {
            return;
        }
        if (args.length == 0) {
            main(p);
            return;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        Catalog cat = s.shop.catalog();
        if (first.equals("suche") || first.equals("search") || first.equals("suchen")) {
            if (args.length == 1) {
                askSearch(p);
            } else {
                search(p, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), 0);
            }
            return;
        }
        if (first.equals("verkaufen") || first.equals("sell")) {
            sellInventory(p);
            return;
        }
        for (Catalog.Category c : cat.categories()) {
            if (c.id().equals(first) || c.de().toLowerCase(Locale.ROOT).startsWith(first)
                    || c.en().toLowerCase(Locale.ROOT).startsWith(first)) {
                category(p, c.id(), 0);
                return;
            }
        }
        search(p, String.join(" ", args), 0);
    }

    /** Namen der Abteilungen (für Tab-Vorschläge). */
    public List<String> tab() {
        List<String> out = new ArrayList<>(List.of("suche", "verkaufen"));
        for (Catalog.Category c : s.shop.catalog().categories()) {
            out.add(c.id());
        }
        return out;
    }

    // ---------------------------------------------------------------- Übersicht

    public void main(Player p) {
        if (!open(p)) {
            return;
        }
        String lang = s.msg.lang(p);
        Catalog cat = s.shop.catalog();
        Menu m = new Menu(6, s.msg.text(lang, "merchant.title"));
        frame(m, Material.GREEN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE);
        background(m, Material.BLACK_STAINED_GLASS_PANE);
        m.show(4, Items.button(Material.EMERALD, s.msg.text(lang, "merchant.head"), List.of(
                s.msg.text(lang, "merchant.head.l1"), s.msg.text(lang, "merchant.head.l2"),
                s.msg.text(lang, "merchant.head.l3", Msg.t("count", cat.size()),
                        Msg.t("cats", cat.categories().size())))));
        int month = month();
        List<Catalog.Category> cats = cat.categories();
        int[] slots = layout(cats.size());
        for (int i = 0; i < cats.size() && i < slots.length; i++) {
            Catalog.Category c = cats.get(i);
            int count = cat.wares(c.id(), month).size();
            m.set(slots[i], Items.button(material(c.icon(), Material.CHEST),
                    s.msg.text(lang, "merchant.cat", Msg.t("cat", c.name(lang))),
                    List.of(s.msg.text(lang, "merchant.cat.count", Msg.t("count", count)),
                            Component.empty(), s.msg.text(lang, "merchant.cat.click"))), pl -> {
                click(pl);
                category(pl, c.id(), 0);
            });
        }
        m.set(38, Items.button(Material.OAK_HANGING_SIGN, s.msg.text(lang, "merchant.search"),
                List.of(s.msg.text(lang, "merchant.search.lore"))), pl -> {
            click(pl);
            askSearch(pl);
        });
        m.set(40, Items.button(Material.HOPPER, s.msg.text(lang, "merchant.sellin"),
                List.of(s.msg.text(lang, "merchant.sellin.lore"))), pl -> {
            click(pl);
            s.plugin.buybackUi().open(pl);
        });
        Map<String, Integer> sellable = sellableInventory(p);
        long worth = sellable.isEmpty() ? 0 : s.buyback.quote(p.getUniqueId(), sellable, s.today()).total();
        m.set(42, Items.button(Material.CHEST_MINECART, s.msg.text(lang, "merchant.sellall"),
                List.of(s.msg.text(lang, "merchant.sellall.lore"), s.msg.text(lang, "merchant.sellall.worth",
                        Msg.money("money", worth, lang), Msg.t("count", sum(sellable))))), pl -> {
            click(pl);
            sellInventory(pl);
        });
        m.set(45, Items.button(Material.BOOK, s.msg.text(lang, "merchant.info"), List.of(
                s.msg.text(lang, "merchant.info.l1"), s.msg.text(lang, "merchant.info.l2"),
                s.msg.text(lang, "merchant.info.l3"), s.msg.text(lang, "merchant.info.l4"))), (Player pl) -> { });
        m.set(47, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "merchant.market"),
                List.of(s.msg.text(lang, "merchant.market.lore"))), pl -> {
            click(pl);
            s.plugin.auctionUi().command(pl, new String[0]);
        });
        balance(m, 49, p, lang);
        close(m, 53, lang);
        m.open(p);
    }

    /** Plätze für die Abteilungen: zwei Reihen à fünf (bei mehr als zehn drei Reihen). */
    private static int[] layout(int n) {
        if (n <= 5) {
            return new int[] {20, 21, 22, 23, 24};
        }
        return CATEGORY_SLOTS;
    }

    // ---------------------------------------------------------------- Abteilung / Suche

    public void category(Player p, String id, int page) {
        Catalog.Category c = s.shop.catalog().category(id);
        if (c == null) {
            main(p);
            return;
        }
        String lang = s.msg.lang(p);
        List<Ware> wares = s.shop.catalog().wares(id, month());
        list(p, wares, page, material(c.icon(), Material.CHEST), s.msg.text(lang, "merchant.cat", Msg.t("cat",
                c.name(lang))), pg -> s.msg.text(lang, "merchant.title.category", Msg.t("cat", c.name(lang)),
                Msg.t("page", pg + 1), Msg.t("pages", pages(wares.size()))), pg -> category(p, id, pg));
    }

    public void search(Player p, String query, int page) {
        if (!open(p)) {
            return;
        }
        List<Ware> found = s.shop.catalog().search(query, month());
        if (found.isEmpty()) {
            s.msg.send(p, "merchant.search.none");
            no(p);
            return;
        }
        String lang = s.msg.lang(p);
        String shown = query.length() > 20 ? query.substring(0, 20) + "…" : query;
        list(p, found, page, Material.OAK_HANGING_SIGN, s.msg.text(lang, "merchant.search"),
                pg -> s.msg.text(lang, "merchant.title.search", Msg.t("query", shown), Msg.t("page", pg + 1),
                        Msg.t("pages", pages(found.size()))), pg -> search(p, query, pg));
    }

    private void askSearch(Player p) {
        p.closeInventory();
        s.msg.send(p, "merchant.search.ask");
        s.prompt.ask(p, text -> search(p, text, 0), () -> s.msg.send(p, "menu.cancelled"));
    }

    private interface Page {
        void open(int page);
    }

    private interface Title {
        Component of(int page);
    }

    private void list(Player p, List<Ware> wares, int page, Material icon, Component header, Title title,
                      Page reopen) {
        String lang = s.msg.lang(p);
        int pages = pages(wares.size());
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, title.of(pg));
        frame(m, Material.GRAY_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE);
        m.show(4, Items.button(icon, header));
        Map<Material, Integer> have = inventoryCounts(p);
        long balance = s.bank.balance(p.getUniqueId());
        Map<String, Long> soldToday = s.buyback.soldToday(p.getUniqueId(), s.today());
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            int slot = 9 + i;
            if (idx >= wares.size()) {
                m.show(slot, null);
                continue;
            }
            Ware w = wares.get(idx);
            Material mat = material(w.key(), null);
            if (mat == null) {
                continue;
            }
            m.set(slot, wareIcon(p, w, mat, have.getOrDefault(mat, 0), soldToday, balance, lang), (pl, click) -> {
                if (click == ClickType.SHIFT_LEFT && w.buyable()) {
                    buy(pl, w, Math.min(mat.getMaxStackSize(), 64), () -> reopen.open(pg));
                } else {
                    click(pl);
                    ware(pl, w, () -> reopen.open(pg));
                }
            });
        }
        if (wares.isEmpty()) {
            m.show(22, Items.button(Material.STRUCTURE_VOID, s.msg.text(lang, "merchant.empty")));
        }
        m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")), pl -> {
            click(pl);
            main(pl);
        });
        m.set(46, Items.button(Material.OAK_HANGING_SIGN, s.msg.text(lang, "merchant.search"),
                List.of(s.msg.text(lang, "merchant.search.lore"))), pl -> {
            click(pl);
            askSearch(pl);
        });
        if (pg > 0) {
            m.set(48, Items.button(Material.SPECTRAL_ARROW, s.msg.text(lang, "menu.prev")), pl -> {
                click(pl);
                reopen.open(pg - 1);
            });
        }
        balance(m, 49, p, lang);
        if (pg + 1 < pages) {
            m.set(50, Items.button(Material.SPECTRAL_ARROW, s.msg.text(lang, "menu.next")), pl -> {
                click(pl);
                reopen.open(pg + 1);
            });
        }
        close(m, 53, lang);
        m.open(p);
    }

    private ItemStack wareIcon(Player p, Ware w, Material mat, int have, Map<String, Long> soldToday, long balance,
                               String lang) {
        List<Component> lore = new ArrayList<>();
        priceLines(lore, p, w, mat, soldToday, lang);
        lore.add(s.msg.text(lang, "merchant.ware.have", Msg.t("count", have)));
        lore.add(Component.empty());
        lore.add(s.msg.text(lang, "merchant.ware.click"));
        if (w.buyable()) {
            lore.add(s.msg.text(lang, "merchant.ware.shift", Msg.t("amount", Math.min(mat.getMaxStackSize(), 64))));
        }
        return Items.withLore(new ItemStack(mat), lore);
    }

    /** Kauf- und Ankaufspreis, Tagesmenge, Tagesgrenze, Saison. */
    private void priceLines(List<Component> lore, Player p, Ware w, Material mat, Map<String, Long> soldToday,
                            String lang) {
        if (w.buyable()) {
            lore.add(s.msg.text(lang, "merchant.ware.buy", Msg.money("price", w.buy(), lang)));
            int stack = Math.min(mat.getMaxStackSize(), 64);
            if (stack > 1) {
                lore.add(s.msg.text(lang, "merchant.ware.buy.stack", Msg.t("amount", stack),
                        Msg.money("price", w.buy() * stack, lang)));
            }
            if (w.buyDaily() > 0) {
                int left = s.shop.leftToday(p.getUniqueId(), w, s.today());
                lore.add(left > 0 ? s.msg.text(lang, "merchant.ware.limit", Msg.t("left", left))
                        : s.msg.text(lang, "merchant.ware.sold-out"));
            }
        } else {
            lore.add(s.msg.text(lang, "merchant.ware.no-buy"));
        }
        if (w.sellable()) {
            lore.add(s.msg.text(lang, "merchant.ware.sell", Msg.money("price", w.sell(), lang)));
            long full = w.daily() - soldToday.getOrDefault(w.key(), 0L);
            lore.add(full > 0 ? s.msg.text(lang, "merchant.ware.sell.full", Msg.t("full", full))
                    : s.msg.text(lang, "merchant.ware.sell.less"));
        } else {
            lore.add(s.msg.text(lang, "merchant.ware.no-sell"));
        }
        if (!w.months().isEmpty()) {
            lore.add(s.msg.text(lang, "merchant.ware.season"));
        }
    }

    // ---------------------------------------------------------------- Ware

    public void ware(Player p, Ware w, Runnable back) {
        String lang = s.msg.lang(p);
        Material mat = material(w.key(), null);
        if (mat == null) {
            return;
        }
        Menu m = new Menu(6, Component.translatable(mat.translationKey()));
        frame(m, Material.GRAY_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 9; i < 45; i++) {
            Material band = i < 27 ? Material.LIME_STAINED_GLASS_PANE : i < 36 ? Material.ORANGE_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            m.show(i, Items.filler(band));
        }
        List<Component> head = new ArrayList<>();
        priceLines(head, p, w, mat, s.buyback.soldToday(p.getUniqueId(), s.today()), lang);
        int have = inventoryCounts(p).getOrDefault(mat, 0);
        head.add(s.msg.text(lang, "merchant.ware.have", Msg.t("count", have)));
        m.show(4, Items.withLore(new ItemStack(mat), head));
        long balance = s.bank.balance(p.getUniqueId());
        int stack = Math.min(mat.getMaxStackSize(), 64);
        int room = room(p, mat);
        Runnable redraw = () -> ware(p, w, back);

        // Kaufen
        if (w.buyable() && w.inSeason(month())) {
            int left = s.shop.leftToday(p.getUniqueId(), w, s.today());
            m.show(9, Items.button(Material.LIME_DYE, s.msg.text(lang, "merchant.buy.header"),
                    List.of(s.msg.text(lang, "merchant.buy.header.lore", Msg.money("price", w.buy(), lang)))));
            int[] amounts = stack >= 64 ? AMOUNTS_64 : stack > 1 ? AMOUNTS_16 : AMOUNTS_1;
            for (int i = 0; i < amounts.length; i++) {
                int n = amounts[i];
                if (w.buyDaily() > 0 && n > w.buyDaily()) {
                    continue; // mehr gibt es pro Tag nie
                }
                ItemStack icon = new ItemStack(mat, Math.min(n, mat.getMaxStackSize()));
                m.set(11 + i, buyButton(icon, s.msg.text(lang, "merchant.buy.button", Msg.t("amount", n)), w, n,
                        balance, room, left, lang), pl -> buy(pl, w, n, redraw));
            }
            m.set(16, Items.button(Material.NAME_TAG, s.msg.text(lang, "merchant.buy.custom")),
                    pl -> askAmount(pl, n -> buy(pl, w, n, redraw)));
            if (stack > 1) {
                for (int i = 0; i < STACKS.length; i++) {
                    int n = STACKS[i] * stack;
                    if (w.buyDaily() > 0 && n > w.buyDaily()) {
                        continue;
                    }
                    int k = STACKS[i];
                    m.set(20 + i, buyButton(new ItemStack(Material.BARREL, k), s.msg.text(lang, "merchant.buy.stacks",
                            Msg.t("stacks", k), Msg.t("amount", n)), w, n, balance, room, left, lang),
                            pl -> buy(pl, w, n, redraw));
                }
            }
            int fill = Math.min(Math.min(room, Pricing.affordable(balance, w.buy())), left);
            fill = Math.min(fill, Pricing.MAX_AMOUNT);
            int fillAmount = fill;
            m.set(24, Items.button(Material.CHEST, s.msg.text(lang, "merchant.buy.fill", Msg.t("amount", fill)),
                    fill > 0 ? List.of(s.msg.text(lang, "merchant.cost", Msg.money("price", w.buy() * fill, lang)))
                            : List.of(s.msg.text(lang, "merchant.buy.fill.none"))), pl -> {
                if (fillAmount > 0) {
                    buy(pl, w, fillAmount, redraw);
                } else {
                    no(pl);
                }
            });
        } else {
            m.show(9, Items.button(Material.GRAY_STAINED_GLASS_PANE, s.msg.text(lang,
                    w.buyable() ? "merchant.off-season" : "merchant.ware.no-buy")));
        }

        // Verkaufen
        if (w.sellable()) {
            m.show(27, Items.button(Material.ORANGE_DYE, s.msg.text(lang, "merchant.sell.header"),
                    List.of(s.msg.text(lang, "merchant.ware.sell", Msg.money("price", w.sell(), lang)))));
            int[] amounts = stack >= 64 ? AMOUNTS_64 : stack > 1 ? AMOUNTS_16 : AMOUNTS_1;
            for (int i = 0; i < amounts.length; i++) {
                int n = amounts[i];
                m.set(29 + i, sellButton(new ItemStack(mat, Math.min(n, mat.getMaxStackSize())),
                        s.msg.text(lang, "merchant.sell.button", Msg.t("amount", n)), p, w, n, have, lang),
                        pl -> sell(pl, w, n, redraw));
            }
            m.set(34, sellButton(new ItemStack(Material.HOPPER), s.msg.text(lang, "merchant.sell.all",
                    Msg.t("amount", have)), p, w, Math.max(1, have), have, lang), pl -> {
                int now = inventoryCounts(pl).getOrDefault(mat, 0);
                if (now <= 0) {
                    s.msg.send(pl, "merchant.not-enough", Msg.t("count", 0));
                    no(pl);
                    return;
                }
                sell(pl, w, Math.min(now, Pricing.MAX_AMOUNT), redraw);
            });
            m.set(35, Items.button(Material.NAME_TAG, s.msg.text(lang, "merchant.sell.custom")),
                    pl -> askAmount(pl, n -> sell(pl, w, n, redraw)));
        } else {
            m.show(27, Items.button(Material.GRAY_STAINED_GLASS_PANE, s.msg.text(lang, "merchant.sell.no"),
                    List.of(s.msg.text(lang, "merchant.sell.no.lore"))));
        }

        // Spieler-Markt
        OptionalLong market = s.prices.median(w.key());
        m.set(40, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "merchant.ah"), List.of(market.isPresent()
                ? s.msg.text(lang, "merchant.ah.price", Msg.money("price", market.getAsLong(), lang))
                : s.msg.text(lang, "merchant.ah.no-price"))), pl -> {
            click(pl);
            s.plugin.auctionUi().command(pl, new String[] {"suche", w.id()});
        });
        m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")), pl -> {
            click(pl);
            back.run();
        });
        balance(m, 49, p, lang);
        close(m, 53, lang);
        m.open(p);
    }

    private ItemStack buyButton(ItemStack icon, Component name, Ware w, int n, long balance, int room, int left,
                                String lang) {
        long cost = w.buy() * n;
        List<Component> lore = new ArrayList<>();
        lore.add(s.msg.text(lang, "merchant.cost", Msg.money("price", cost, lang)));
        if (cost > balance) {
            lore.add(s.msg.text(lang, "merchant.missing", Msg.money("missing", cost - balance, lang)));
        }
        if (n > room) {
            lore.add(s.msg.text(lang, "merchant.room", Msg.t("room", room)));
        }
        if (n > left) {
            lore.add(left > 0 ? s.msg.text(lang, "merchant.ware.limit", Msg.t("left", left))
                    : s.msg.text(lang, "merchant.ware.sold-out"));
        }
        return Items.button(icon.getType(), name, lore).asQuantity(icon.getAmount());
    }

    private ItemStack sellButton(ItemStack icon, Component name, Player p, Ware w, int n, int have, String lang) {
        List<Component> lore = new ArrayList<>();
        ServerShop.Deal q = s.shop.quoteSell(p.getUniqueId(), w.key(), Math.max(1, Math.min(n, Pricing.MAX_AMOUNT)),
                s.today());
        lore.add(s.msg.text(lang, "merchant.payout", Msg.money("price", q.total(), lang)));
        if (have < n) {
            lore.add(s.msg.text(lang, "merchant.have-only", Msg.t("count", have)));
        }
        return Items.button(icon.getType(), name, lore).asQuantity(icon.getAmount());
    }

    private void askAmount(Player p, java.util.function.IntConsumer then) {
        p.closeInventory();
        s.msg.send(p, "merchant.amount.ask", Msg.t("max", Pricing.MAX_AMOUNT));
        s.prompt.ask(p, text -> {
            int n;
            try {
                n = Integer.parseInt(text.strip().replace(".", "").replace("×", "").replace("x", ""));
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (!Pricing.validAmount(n)) {
                s.msg.send(p, "merchant.amount.bad", Msg.t("max", Pricing.MAX_AMOUNT));
                return;
            }
            then.accept(n);
        }, () -> s.msg.send(p, "menu.cancelled"));
    }

    // ---------------------------------------------------------------- Kaufen

    /** Kaufen (mit Nachfrage ab der Grenze). {@code after}: danach (Menü neu zeichnen). */
    public void buy(Player p, Ware w, int amount, Runnable after) {
        Material mat = material(w.key(), null);
        if (mat == null || !w.buyable()) {
            s.msg.send(p, "merchant.no-buy");
            no(p);
            return;
        }
        String lang = s.msg.lang(p);
        long cost = Pricing.cost(w.buy(), amount);
        if (cost < 0 || !Pricing.validAmount(amount)) {
            s.msg.send(p, "merchant.amount.bad", Msg.t("max", Pricing.MAX_AMOUNT));
            no(p);
            return;
        }
        if (!precheckBuy(p, w, mat, amount, cost)) {
            return;
        }
        if (cost >= s.settings.shopConfirmAbove && s.settings.shopConfirmAbove > 0) {
            s.confirm.open(p, s.msg.text(lang, "merchant.confirm.buy"), new ItemStack(mat, Math.min(amount,
                    mat.getMaxStackSize())), List.of(s.msg.text(lang, "merchant.confirm.line", Msg.t("amount", amount),
                    Msg.c("item", NoticeSender.itemName(w.key())), Msg.money("price", cost, lang))),
                    s.msg.text(lang, "merchant.confirm.buy.yes", Msg.money("price", cost, lang)),
                    () -> doBuy(p, w, mat, amount, after), after);
        } else {
            doBuy(p, w, mat, amount, after);
        }
    }

    /** Vorher prüfen, was der Spieler sofort verstehen soll: Geld, Platz, Tagesgrenze. */
    private boolean precheckBuy(Player p, Ware w, Material mat, int amount, long cost) {
        String lang = s.msg.lang(p);
        long balance = s.bank.balance(p.getUniqueId());
        if (cost > balance) {
            s.msg.send(p, "merchant.too-poor", Msg.money("missing", cost - balance, lang));
            no(p);
            return false;
        }
        int room = room(p, mat);
        if (amount > room) {
            s.msg.send(p, "merchant.no-room", Msg.t("room", room));
            no(p);
            return false;
        }
        int left = s.shop.leftToday(p.getUniqueId(), w, s.today());
        if (amount > left) {
            s.msg.send(p, "merchant.limit", Msg.t("left", left));
            no(p);
            return false;
        }
        return true;
    }

    private void doBuy(Player p, Ware w, Material mat, int amount, Runnable after) {
        if (!p.isOnline()) {
            return;
        }
        long cost = w.buy() * amount;
        // nach einer Nachfrage kann sich alles geändert haben – noch einmal prüfen
        if (!precheckBuy(p, w, mat, amount, cost)) {
            return;
        }
        ServerShop.Deal d = s.shop.buy(p.getUniqueId(), w.key(), amount, s.today(), month());
        String lang = s.msg.lang(p);
        switch (d.status()) {
            case OK -> {
                give(p, mat, amount);
                s.msg.send(p, "merchant.bought", Msg.t("amount", amount), Msg.c("item",
                        NoticeSender.itemName(w.key())), Msg.money("price", d.total(), lang),
                        Msg.money("balance", s.bank.balance(p.getUniqueId()), lang));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.5f, 1.0f);
            }
            case NOT_ENOUGH -> {
                long balance = s.bank.balance(p.getUniqueId());
                s.msg.send(p, "merchant.too-poor", Msg.money("missing", Math.max(0, cost - balance), lang));
                no(p);
            }
            case LIMIT -> {
                s.msg.send(p, "merchant.limit", Msg.t("left", d.left()));
                no(p);
            }
            case OFF_SEASON -> {
                s.msg.send(p, "merchant.off-season");
                no(p);
            }
            case BAD_AMOUNT -> {
                s.msg.send(p, "merchant.amount.bad", Msg.t("max", Pricing.MAX_AMOUNT));
                no(p);
            }
            default -> {
                s.msg.send(p, "merchant.no-buy");
                no(p);
            }
        }
        if (after != null && d.ok()) {
            after.run();
        }
    }

    private void give(Player p, Material mat, int amount) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, mat.getMaxStackSize());
            s.deliveries.giveOrMail(p, new ItemStack(mat, n), "haendler");
            left -= n;
        }
    }

    // ---------------------------------------------------------------- Verkaufen

    /** Verkaufen (mit Nachfrage ab der Grenze). */
    public void sell(Player p, Ware w, int amount, Runnable after) {
        Material mat = material(w.key(), null);
        if (mat == null || !w.sellable()) {
            s.msg.send(p, "merchant.no-sell");
            no(p);
            return;
        }
        if (!Pricing.validAmount(amount)) {
            s.msg.send(p, "merchant.amount.bad", Msg.t("max", Pricing.MAX_AMOUNT));
            no(p);
            return;
        }
        int have = inventoryCounts(p).getOrDefault(mat, 0);
        if (have < amount) {
            s.msg.send(p, "merchant.not-enough", Msg.t("count", have));
            no(p);
            return;
        }
        String lang = s.msg.lang(p);
        long payout = s.shop.quoteSell(p.getUniqueId(), w.key(), amount, s.today()).total();
        if (payout >= s.settings.shopConfirmAbove && s.settings.shopConfirmAbove > 0) {
            s.confirm.open(p, s.msg.text(lang, "merchant.confirm.sell"), new ItemStack(mat, Math.min(amount,
                    mat.getMaxStackSize())), List.of(s.msg.text(lang, "merchant.confirm.line", Msg.t("amount", amount),
                    Msg.c("item", NoticeSender.itemName(w.key())), Msg.money("price", payout, lang))),
                    s.msg.text(lang, "merchant.confirm.sell.yes", Msg.money("price", payout, lang)),
                    () -> doSell(p, w, mat, amount, after), after);
        } else {
            doSell(p, w, mat, amount, after);
        }
    }

    private void doSell(Player p, Ware w, Material mat, int amount, Runnable after) {
        if (!p.isOnline()) {
            return;
        }
        if (inventoryCounts(p).getOrDefault(mat, 0) < amount) {
            s.msg.send(p, "merchant.not-enough", Msg.t("count", inventoryCounts(p).getOrDefault(mat, 0)));
            no(p);
            return;
        }
        take(p, mat, amount);
        ServerShop.Deal d;
        try {
            d = s.shop.sell(p.getUniqueId(), w.key(), amount, s.today());
        } catch (RuntimeException e) {
            give(p, mat, amount);
            throw e;
        }
        if (!d.ok()) {
            give(p, mat, amount);
            s.msg.send(p, "merchant.no-sell");
            no(p);
            return;
        }
        String lang = s.msg.lang(p);
        s.msg.send(p, "merchant.sold", Msg.t("amount", amount), Msg.c("item", NoticeSender.itemName(w.key())),
                Msg.money("price", d.total(), lang));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.9f);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.6f);
        if (after != null) {
            after.run();
        }
    }

    /** Alles im Inventar verkaufen, was die Händlerin ankauft – mit Übersicht und Nachfrage. */
    public void sellInventory(Player p) {
        if (!open(p)) {
            return;
        }
        Map<String, Integer> items = sellableInventory(p);
        if (items.isEmpty()) {
            s.msg.send(p, "merchant.nothing-to-sell");
            no(p);
            return;
        }
        String lang = s.msg.lang(p);
        Buyback.Quote q = s.buyback.quote(p.getUniqueId(), items, s.today());
        List<Component> lines = new ArrayList<>();
        int shown = 0;
        for (Buyback.Line l : q.lines()) {
            if (shown++ >= 10) {
                break;
            }
            lines.add(s.msg.text(lang, "merchant.confirm.line", Msg.t("amount", l.amount()),
                    Msg.c("item", NoticeSender.itemName(l.key())), Msg.money("price", l.payout(), lang)));
        }
        if (q.lines().size() > 10) {
            lines.add(s.msg.text(lang, "merchant.confirm.more", Msg.t("count", q.lines().size() - 10)));
        }
        lines.add(Component.empty());
        lines.add(s.msg.text(lang, "merchant.confirm.total", Msg.money("price", q.total(), lang)));
        s.confirm.open(p, s.msg.text(lang, "merchant.confirm.sell"), new ItemStack(Material.CHEST_MINECART), lines,
                s.msg.text(lang, "merchant.confirm.sell.yes", Msg.money("price", q.total(), lang)),
                () -> doSellInventory(p, items), () -> main(p));
    }

    private void doSellInventory(Player p, Map<String, Integer> planned) {
        if (!p.isOnline()) {
            return;
        }
        // nur, was noch da ist (und höchstens das Gezeigte)
        Map<String, Integer> now = sellableInventory(p);
        Map<String, Integer> items = new LinkedHashMap<>();
        planned.forEach((k, v) -> {
            int n = Math.min(v, now.getOrDefault(k, 0));
            if (n > 0) {
                items.put(k, n);
            }
        });
        if (items.isEmpty()) {
            s.msg.send(p, "merchant.nothing-to-sell");
            no(p);
            return;
        }
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            take(p, material(e.getKey(), Material.AIR), e.getValue());
        }
        Buyback.Quote q;
        try {
            q = s.buyback.sell(p.getUniqueId(), items, s.today());
        } catch (RuntimeException ex) {
            items.forEach((k, v) -> give(p, material(k, Material.AIR), v));
            throw ex;
        }
        String lang = s.msg.lang(p);
        s.msg.send(p, "merchant.sold-all", Msg.t("count", sum(items)), Msg.t("kinds", items.size()),
                Msg.money("price", q.total(), lang));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    // ---------------------------------------------------------------- Inventar

    /** Unveränderte Gegenstände (ohne Namen, Verzauberung, Schaden) im Hauptinventar, nach Sorte. */
    static Map<Material, Integer> inventoryCounts(Player p) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (plain(it)) {
                out.merge(it.getType(), it.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    private Map<String, Integer> sellableInventory(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Catalog cat = s.shop.catalog();
        for (Map.Entry<Material, Integer> e : inventoryCounts(p).entrySet()) {
            String key = e.getKey().getKey().toString();
            Ware w = cat.ware(key);
            if (w != null && w.sellable()) {
                out.put(key, Math.min(e.getValue(), Pricing.MAX_AMOUNT));
            }
        }
        return out;
    }

    static boolean plain(ItemStack it) {
        return it != null && !it.getType().isAir() && it.isSimilar(new ItemStack(it.getType()));
    }

    /** Wie viele Stück dieser Sorte passen noch ins Hauptinventar? */
    static int room(Player p, Material mat) {
        int max = mat.getMaxStackSize();
        int room = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null || it.getType().isAir()) {
                room += max;
            } else if (plain(it) && it.getType() == mat) {
                room += Math.max(0, max - it.getAmount());
            }
        }
        return room;
    }

    /** Genau {@code amount} unveränderte Stück abnehmen (vorher geprüft). */
    static void take(Player p, Material mat, int amount) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = contents.length - 1; i >= 0 && left > 0; i--) {
            ItemStack it = contents[i];
            if (plain(it) && it.getType() == mat) {
                int n = Math.min(left, it.getAmount());
                left -= n;
                if (n == it.getAmount()) {
                    contents[i] = null;
                } else {
                    it.setAmount(it.getAmount() - n);
                }
            }
        }
        inv.setStorageContents(contents);
    }

    // ---------------------------------------------------------------- Helfer

    private void frame(Menu m, Material top, Material bottom) {
        ItemStack t = Items.filler(top);
        ItemStack b = Items.filler(bottom);
        for (int i = 0; i < 9; i++) {
            m.show(i, t);
            m.show(m.size() - 9 + i, b);
        }
    }

    /** Leere Plätze zwischen Rahmen füllen (ruhiger Hintergrund, Knöpfe stechen heraus). */
    private static void background(Menu m, Material pane) {
        ItemStack f = Items.filler(pane);
        for (int i = 9; i < m.size() - 9; i++) {
            ItemStack there = m.getInventory().getItem(i);
            if (there == null || there.getType().isAir()) {
                m.show(i, f);
            }
        }
    }

    private void balance(Menu m, int slot, Player p, String lang) {
        m.show(slot, Items.button(Material.SUNFLOWER, s.msg.text(lang, "merchant.balance",
                Msg.money("money", s.bank.balance(p.getUniqueId()), lang)),
                List.of(s.msg.text(lang, "merchant.balance.lore"))));
    }

    private void close(Menu m, int slot, String lang) {
        m.set(slot, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
    }

    private static int pages(int n) {
        return Math.max(1, (n + PER_PAGE - 1) / PER_PAGE);
    }

    private static int sum(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    static Material material(String key, Material fallback) {
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem() && !m.isAir() ? m : fallback;
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
    }

    private static void no(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }
}
