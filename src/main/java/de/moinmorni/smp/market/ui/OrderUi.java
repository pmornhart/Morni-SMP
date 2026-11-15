package de.moinmorni.smp.market.ui;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.Category;
import de.moinmorni.smp.market.Fees;
import de.moinmorni.smp.market.ItemData;
import de.moinmorni.smp.market.Orders;
import de.moinmorni.smp.market.Orders.Order;
import de.moinmorni.smp.market.SearchWords;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import de.moinmorni.smp.ui.Picker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Aufträge: offene Kaufgesuche ansehen und beliefern (alles, einen Stapel oder eine gewählte Menge), eigene Aufträge
 * anlegen (Gegenstand aus der Hand oder per Name suchen, Menge, Preis pro Stück) und zurückziehen.
 */
public final class OrderUi {

    public record View(Category category, String search, boolean newest, int page) {
        View page(int p) {
            return new View(category, search, newest, Math.max(0, p));
        }
    }

    private static final int PER_PAGE = 36;

    private final Smp s;

    public OrderUi(Smp s) {
        this.s = s;
    }

    public void command(Player p, String[] args) {
        if (args.length == 0) {
            browse(p, new View(null, "", false, 0));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "neu", "new", "erstellen" -> {
                if (args.length >= 3) {
                    long amount = parseAmount(args[1]);
                    long price = Money.parse(args[2]);
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        s.msg.send(p, "orders.new.empty-hand");
                        return;
                    }
                    if (amount <= 0 || amount > Orders.MAX_WANTED || price <= 0) {
                        s.msg.send(p, "orders.new.invalid", Msg.t("max", Orders.MAX_WANTED));
                        return;
                    }
                    confirmCreate(p, hand.asOne(), (int) amount, price);
                } else {
                    create(p);
                }
            }
            case "meine", "mine", "eigene" -> mine(p);
            case "suche", "search" -> browse(p, new View(null,
                    String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), false, 0));
            default -> browse(p, new View(null, String.join(" ", args), false, 0));
        }
    }

    private static long parseAmount(String s) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- Stöbern + Liefern

    public void browse(Player p, View v) {
        String lang = s.msg.lang(p);
        int total = s.orders.count(v.category(), v.search());
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        View view = v.page() >= pages ? v.page(pages - 1) : v;
        Menu m = new Menu(6, s.msg.text(lang, "orders.title", Msg.t("page", view.page() + 1), Msg.t("pages", pages)));
        // Abteilungen
        ItemStack all = Items.button(Material.CHEST, s.msg.text(lang, "cat.all"));
        AuctionUi.glow(all, view.category() == null);
        m.set(0, all, pl -> browse(pl, new View(null, view.search(), view.newest(), 0)));
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            ItemStack icon = Items.button(AuctionUi.CATEGORY_ICONS[i], s.msg.text(lang, "cat." + c.id()));
            AuctionUi.glow(icon, c == view.category());
            m.set(1 + i, icon, pl -> browse(pl, new View(c, view.search(), view.newest(), 0)));
        }
        m.set(8, Items.button(Material.OAK_SIGN, s.msg.text(lang, "search.button"), List.of(
                view.search().isBlank() ? s.msg.text(lang, "search.lore")
                        : s.msg.text(lang, "search.current", Msg.t("text", view.search())))), (pl, click) -> {
            if (click.isRightClick()) {
                browse(pl, new View(view.category(), "", view.newest(), 0));
                return;
            }
            pl.closeInventory();
            s.msg.send(pl, "search.ask");
            s.prompt.ask(pl, text -> browse(pl, new View(view.category(), text, view.newest(), 0)), () -> browse(pl, view));
        });
        List<Order> list = s.orders.browse(view.category(), view.search(), view.newest(), view.page() * PER_PAGE,
                PER_PAGE);
        for (int i = 0; i < list.size(); i++) {
            Order o = list.get(i);
            m.set(9 + i, orderIcon(p, o, lang), pl -> {
                if (o.owner().equals(pl.getUniqueId())) {
                    confirmCancel(pl, o);
                } else {
                    deliverMenu(pl, o, view);
                }
            });
        }
        if (list.isEmpty()) {
            m.show(22, Items.button(Material.PAPER, s.msg.text(lang, "orders.empty"),
                    List.of(s.msg.text(lang, "orders.empty.lore"))));
        }
        if (view.page() > 0) {
            m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.prev")), pl -> browse(pl, view.page(view.page() - 1)));
        }
        if (view.page() + 1 < pages) {
            m.set(53, Items.button(Material.ARROW, s.msg.text(lang, "menu.next")), pl -> browse(pl, view.page(view.page() + 1)));
        }
        m.set(46, Items.button(Material.HOPPER, s.msg.text(lang, view.newest() ? "orders.sort.newest" : "orders.sort.best"),
                List.of(s.msg.text(lang, "ah.sort.lore"))),
                pl -> browse(pl, new View(view.category(), view.search(), !view.newest(), 0)));
        m.set(47, Items.button(Material.WRITABLE_BOOK, s.msg.text(lang, "orders.mine"),
                List.of(s.msg.text(lang, "orders.mine.lore", Msg.t("count", s.orders.countActive(p.getUniqueId())),
                        Msg.t("max", s.orders.maxActive())))), this::mine);
        m.set(48, Items.button(Material.EMERALD, s.msg.text(lang, "orders.new"),
                List.of(s.msg.text(lang, "orders.new.lore"))), this::create);
        m.show(49, Items.button(Material.BOOK, s.msg.text(lang, "orders.info"),
                List.of(s.msg.text(lang, "orders.info.lore", Msg.t("fee", AuctionUi.percent(s.orders.feeBasisPoints(),
                        lang)), Msg.money("balance", s.bank.balance(p.getUniqueId()), lang)))));
        int mail = s.mailbox.count(p.getUniqueId());
        m.set(50, Items.button(mail > 0 ? Material.CHEST : Material.BARREL, s.msg.text(lang, "mail.button"),
                List.of(s.msg.text(lang, "mail.button.lore", Msg.t("count", mail)))),
                pl -> s.plugin.mailboxUi().open(pl, 0));
        m.set(51, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "ah.button"),
                List.of(s.msg.text(lang, "ah.button.lore"))),
                pl -> s.plugin.auctionUi().browse(pl, new AuctionUi.View(null, "", de.moinmorni.smp.market.AuctionHouse.Sort.CHEAPEST, 0)));
        m.set(52, Items.button(Material.CLOCK, s.msg.text(lang, "menu.refresh")), pl -> browse(pl, view));
        m.open(p);
    }

    private ItemStack orderIcon(Player p, Order o, String lang) {
        ItemStack item = ItemCodec.one(o.item()).asQuantity(Math.max(1, Math.min(o.remaining(),
                ItemCodec.one(o.item()).getMaxStackSize())));
        List<Component> lines = new ArrayList<>();
        lines.add(s.msg.text(lang, "orders.line.wanted", Msg.t("wanted", o.wanted()), Msg.t("left", o.remaining())));
        lines.add(s.msg.text(lang, "orders.line.pays", Msg.money("unit", o.unitPrice(), lang)));
        lines.add(s.msg.text(lang, "orders.line.total", Msg.money("total", o.unitPrice() * o.remaining(), lang)));
        OptionalLong median = s.prices.median(o.item().key());
        if (median.isPresent()) {
            lines.add(s.msg.text(lang, "ah.line.market", Msg.money("unit", median.getAsLong(), lang)));
        }
        lines.add(s.msg.text(lang, "orders.line.from", Msg.t("name", o.ownerName())));
        lines.add(s.msg.text(lang, "ah.line.left", Msg.t("time", Msg.duration(o.expires() - System.currentTimeMillis(),
                lang))));
        if (o.owner().equals(p.getUniqueId())) {
            lines.add(s.msg.text(lang, "orders.line.own"));
        } else {
            int have = ItemCodec.count(p.getInventory(), ItemCodec.one(o.item()));
            lines.add(s.msg.text(lang, "orders.line.have", Msg.t("count", have)));
            lines.add(s.msg.text(lang, "orders.line.deliver"));
        }
        return Items.withLore(item, lines);
    }

    private void deliverMenu(Player p, Order o, View back) {
        String lang = s.msg.lang(p);
        ItemStack template = ItemCodec.one(o.item());
        int have = ItemCodec.count(p.getInventory(), template);
        Menu m = new Menu(3, s.msg.text(lang, "orders.deliver.title"));
        m.show(4, orderIcon(p, o, lang));
        int all = Math.min(have, o.remaining());
        int stack = Math.min(all, template.getMaxStackSize());
        if (have <= 0) {
            m.show(13, Items.button(Material.BARRIER, s.msg.text(lang, "orders.deliver.none"),
                    List.of(s.msg.text(lang, "orders.deliver.none.lore"))));
        } else {
            m.set(11, Items.button(Material.CHEST, s.msg.text(lang, "orders.deliver.all", Msg.t("count", all)),
                    List.of(s.msg.text(lang, "orders.deliver.gets", Msg.money("money", net(o.unitPrice() * all), lang)))),
                    pl -> deliver(pl, o, all, back));
            m.set(13, Items.button(Material.BUNDLE, s.msg.text(lang, "orders.deliver.stack", Msg.t("count", stack)),
                    List.of(s.msg.text(lang, "orders.deliver.gets", Msg.money("money", net(o.unitPrice() * stack), lang)))),
                    pl -> deliver(pl, o, stack, back));
            m.set(15, Items.button(Material.OAK_SIGN, s.msg.text(lang, "orders.deliver.choose")),
                    pl -> s.picker.open(pl, s.msg.text(lang, "orders.deliver.title"), template, Picker.Kind.AMOUNT,
                            stack, 1, all, n -> List.of(s.msg.text(lang, "orders.deliver.gets",
                                    Msg.money("money", net(o.unitPrice() * n), lang))),
                            n -> deliver(pl, o, (int) n, back), () -> deliverMenu(pl, o, back)));
        }
        m.set(18, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")), pl -> browse(pl, back));
        m.open(p);
    }

    private long net(long gross) {
        return gross - Fees.of(gross, s.orders.feeBasisPoints());
    }

    /**
     * Liefern: erst die Ware aus dem Inventar nehmen, dann den Auftrag buchen; was der Auftrag nicht mehr braucht
     * (jemand war schneller), kommt sofort zurück.
     */
    private void deliver(Player p, Order o, int amount, View back) {
        ItemStack template = ItemCodec.one(o.item());
        int removed = ItemCodec.remove(p.getInventory(), template, amount);
        if (removed <= 0) {
            s.msg.send(p, "orders.deliver.none");
            return;
        }
        Orders.DeliverResult r;
        try {
            r = s.orders.deliver(o.id(), p.getUniqueId(), p.getName(), removed);
        } catch (RuntimeException e) {
            s.deliveries.giveOrMail(p, template.asQuantity(removed), "auftrag_zurueck");
            throw e;
        }
        int back2 = removed - r.accepted();
        if (back2 > 0) {
            int left = back2;
            while (left > 0) {
                int n = Math.min(left, template.getMaxStackSize());
                s.deliveries.giveOrMail(p, template.asQuantity(n), "auftrag_zurueck");
                left -= n;
            }
        }
        String lang = s.msg.lang(p);
        switch (r.status()) {
            case OK -> {
                s.msg.send(p, "orders.delivered", Msg.t("count", r.accepted()), Msg.c("item", ItemCodec.name(template)),
                        Msg.money("money", r.paid(), lang), Msg.money("fee", r.fee(), lang),
                        Msg.t("owner", o.ownerName()));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
                s.reporter.orderFilled(p.getUniqueId());
                s.reporter.purchase(o.owner());
                s.noticeSender.flush(o.owner());
                p.closeInventory();
            }
            case GONE -> {
                s.msg.send(p, "orders.gone");
                browse(p, back);
            }
            case OWN -> s.msg.send(p, "orders.own");
            case NOTHING -> s.msg.send(p, "orders.deliver.none");
        }
    }

    // ---------------------------------------------------------------- Eigene Aufträge

    public void mine(Player p) {
        String lang = s.msg.lang(p);
        List<Order> list = s.orders.activeOf(p.getUniqueId());
        Menu m = new Menu(4, s.msg.text(lang, "orders.mine.title", Msg.t("count", list.size()),
                Msg.t("max", s.orders.maxActive())));
        for (int i = 0; i < list.size() && i < 27; i++) {
            Order o = list.get(i);
            m.set(i, orderIcon(p, o, lang), pl -> confirmCancel(pl, o));
        }
        m.set(27, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")),
                pl -> browse(pl, new View(null, "", false, 0)));
        m.set(31, Items.button(Material.EMERALD, s.msg.text(lang, "orders.new"),
                List.of(s.msg.text(lang, "orders.new.lore"))), this::create);
        m.set(35, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    private void confirmCancel(Player p, Order o) {
        String lang = s.msg.lang(p);
        s.confirm.open(p, s.msg.text(lang, "orders.cancel.title"), ItemCodec.one(o.item()),
                List.of(s.msg.text(lang, "orders.cancel.lore", Msg.money("money", o.escrow(), lang))),
                s.msg.text(lang, "orders.cancel.yes"), () -> {
                    long back = s.orders.cancel(o.id(), p.getUniqueId(), false);
                    if (back >= 0) {
                        s.msg.send(p, "orders.cancelled", Msg.money("money", back, lang));
                    } else {
                        s.msg.send(p, "orders.gone");
                    }
                }, () -> mine(p));
    }

    // ---------------------------------------------------------------- Neuer Auftrag

    /** Neuer Auftrag: Vorlage aus der Hand, sonst Name eintippen und aussuchen. */
    public void create(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            chooseAmount(p, hand.asOne());
            return;
        }
        p.closeInventory();
        s.msg.send(p, "orders.new.ask-name");
        s.prompt.ask(p, text -> chooseItem(p, text), () -> s.msg.send(p, "menu.cancelled"));
    }

    private void chooseItem(Player p, String query) {
        List<Set<String>> terms = SearchWords.terms(query);
        List<Material> found = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem() || m.isAir()) {
                continue;
            }
            String key = m.getKey().getKey();
            if (SearchWords.matches(key + " " + key.replace('_', ' '), terms)) {
                found.add(m);
            }
        }
        found.sort(Comparator.comparingInt((Material m) -> m.getKey().getKey().length())
                .thenComparing(m -> m.getKey().getKey()));
        String lang = s.msg.lang(p);
        if (found.isEmpty()) {
            s.msg.send(p, "orders.new.not-found", Msg.t("text", query));
            return;
        }
        if (found.size() == 1) {
            chooseAmount(p, new ItemStack(found.get(0)));
            return;
        }
        Menu m = new Menu(6, s.msg.text(lang, "orders.new.pick"));
        for (int i = 0; i < found.size() && i < 54; i++) {
            Material mat = found.get(i);
            m.set(i, new ItemStack(mat), pl -> chooseAmount(pl, new ItemStack(mat)));
        }
        m.open(p);
    }

    private void chooseAmount(Player p, ItemStack template) {
        String lang = s.msg.lang(p);
        s.picker.open(p, s.msg.text(lang, "orders.new.amount"), template, Picker.Kind.AMOUNT,
                Math.min(64, template.getMaxStackSize() * 1L), 1, Orders.MAX_WANTED, null,
                amount -> choosePrice(p, template, (int) amount), null);
    }

    private void choosePrice(Player p, ItemStack template, int amount) {
        String lang = s.msg.lang(p);
        ItemData data = ItemCodec.of(template);
        OptionalLong median = s.prices.median(data.key());
        long start = median.isPresent() ? median.getAsLong() : 10;
        s.picker.open(p, s.msg.text(lang, "orders.new.price"), template, Picker.Kind.MONEY, start, 1,
                Money.MAX / Orders.MAX_WANTED, unit -> {
                    List<Component> lines = new ArrayList<>();
                    lines.add(s.msg.text(lang, "orders.new.summary", Msg.t("amount", amount),
                            Msg.money("total", unit * amount, lang)));
                    median.ifPresent(md -> lines.add(s.msg.text(lang, "ah.line.market", Msg.money("unit", md, lang))));
                    lines.add(s.msg.text(lang, "line.balance", Msg.money("balance", s.bank.balance(p.getUniqueId()),
                            lang)));
                    return lines;
                }, unit -> confirmCreate(p, template, amount, unit), () -> chooseAmount(p, template));
    }

    private void confirmCreate(Player p, ItemStack template, int amount, long unit) {
        String lang = s.msg.lang(p);
        s.confirm.open(p, s.msg.text(lang, "orders.new.confirm"), template,
                List.of(s.msg.text(lang, "orders.new.summary", Msg.t("amount", amount),
                        Msg.money("total", unit * amount, lang)),
                        s.msg.text(lang, "orders.line.pays", Msg.money("unit", unit, lang)),
                        s.msg.text(lang, "orders.new.escrow")),
                s.msg.text(lang, "orders.new.yes", Msg.money("total", unit * amount, lang)), () -> {
                    Orders.CreateResult r = s.orders.create(p.getUniqueId(), p.getName(), ItemCodec.of(template),
                            amount, unit);
                    switch (r.status()) {
                        case OK -> {
                            s.msg.send(p, "orders.created", Msg.t("amount", amount),
                                    Msg.c("item", ItemCodec.name(template)), Msg.money("unit", unit, lang),
                                    Msg.money("total", unit * amount, lang));
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.3f);
                        }
                        case TOO_MANY -> s.msg.send(p, "orders.too-many", Msg.t("max", s.orders.maxActive()));
                        case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", Msg.money("missing",
                                unit * amount - s.bank.balance(p.getUniqueId()), lang));
                        case INVALID -> s.msg.send(p, "orders.new.invalid", Msg.t("max", Orders.MAX_WANTED));
                    }
                }, null);
    }
}
