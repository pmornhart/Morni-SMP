package de.moinmorni.smp.market.ui;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.AuctionHouse.Listing;
import de.moinmorni.smp.market.AuctionHouse.Sort;
import de.moinmorni.smp.market.Category;
import de.moinmorni.smp.market.Fees;
import de.moinmorni.smp.market.ItemData;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import de.moinmorni.smp.ui.Picker;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Menüs des Auktionshauses: stöbern (Abteilungen, Suche, Sortierung, Seiten), kaufen mit Nachfrage, Gegenstand aus
 * der Hand verkaufen (Preis mit Plus/Minus oder eintippen, Marktpreis als Hilfe), eigene Angebote zurücknehmen.
 */
public final class AuctionUi {

    /** Was gerade angezeigt wird. */
    public record View(Category category, String search, Sort sort, int page) {
        View with(Category c) {
            return new View(c, search, sort, 0);
        }

        View page(int p) {
            return new View(category, search, sort, Math.max(0, p));
        }

        View sorted(Sort s) {
            return new View(category, search, s, 0);
        }

        View searching(String s) {
            return new View(category, s, sort, 0);
        }
    }

    static final int PER_PAGE = 36;
    static final Material[] CATEGORY_ICONS = {Material.BRICKS, Material.IRON_SWORD, Material.IRON_CHESTPLATE,
            Material.BREAD, Material.REDSTONE, Material.ENCHANTED_BOOK, Material.BUNDLE};

    private final Smp s;

    public AuctionUi(Smp s) {
        this.s = s;
    }

    public void command(Player p, String[] args) {
        if (args.length == 0) {
            browse(p, new View(null, "", Sort.CHEAPEST, 0));
            return;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "verkaufen", "sell" -> {
                if (args.length >= 2) {
                    long price = Money.parse(String.join("", java.util.Arrays.copyOfRange(args, 1, args.length)));
                    if (price <= 0) {
                        s.msg.send(p, "money.invalid");
                        return;
                    }
                    sellNow(p, p.getInventory().getItemInMainHand().clone(), price);
                } else {
                    sell(p);
                }
            }
            case "suche", "search", "suchen" -> browse(p, new View(null,
                    String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), Sort.CHEAPEST, 0));
            case "meine", "mine", "eigene" -> mine(p, 0);
            case "abholen", "mailbox" -> s.plugin.mailboxUi().open(p, 0);
            default -> browse(p, new View(null, String.join(" ", args), Sort.CHEAPEST, 0));
        }
    }

    // ---------------------------------------------------------------- Stöbern

    public void browse(Player p, View v) {
        String lang = s.msg.lang(p);
        int total = s.ah.count(v.category(), v.search());
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        View view = v.page() >= pages ? v.page(pages - 1) : v;
        Menu m = new Menu(6, s.msg.text(lang, "ah.title", Msg.t("page", view.page() + 1), Msg.t("pages", pages)));
        categoryRow(p, m, view, lang);
        List<Listing> list = s.ah.browse(view.category(), view.search(), view.sort(), view.page() * PER_PAGE, PER_PAGE);
        for (int i = 0; i < list.size(); i++) {
            Listing l = list.get(i);
            m.set(9 + i, listingIcon(p, l, lang), pl -> {
                if (l.seller().equals(pl.getUniqueId())) {
                    confirmCancel(pl, l, () -> browse(pl, view));
                } else {
                    confirmBuy(pl, l, view);
                }
            });
        }
        if (list.isEmpty()) {
            m.show(22, Items.button(Material.PAPER, s.msg.text(lang, "ah.empty"),
                    List.of(s.msg.text(lang, "ah.empty.lore"))));
        }
        if (view.page() > 0) {
            m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.prev")), pl -> browse(pl, view.page(view.page() - 1)));
        }
        if (view.page() + 1 < pages) {
            m.set(53, Items.button(Material.ARROW, s.msg.text(lang, "menu.next")), pl -> browse(pl, view.page(view.page() + 1)));
        }
        Sort next = Sort.values()[(view.sort().ordinal() + 1) % Sort.values().length];
        m.set(46, Items.button(Material.HOPPER, s.msg.text(lang, "ah.sort." + view.sort().name().toLowerCase()),
                List.of(s.msg.text(lang, "ah.sort.lore"))), pl -> browse(pl, view.sorted(next)));
        m.set(47, Items.button(Material.WRITABLE_BOOK, s.msg.text(lang, "ah.mine"),
                List.of(s.msg.text(lang, "ah.mine.lore", Msg.t("count", s.ah.countActive(p.getUniqueId())),
                        Msg.t("max", s.ah.maxActive())))), pl -> mine(pl, 0));
        m.set(48, Items.button(Material.EMERALD, s.msg.text(lang, "ah.sell"),
                List.of(s.msg.text(lang, "ah.sell.lore"))), this::sell);
        m.show(49, Items.button(Material.BOOK, s.msg.text(lang, "ah.info"),
                List.of(s.msg.text(lang, "ah.info.lore", Msg.t("fee", percent(s.ah.feeBasisPoints(), lang)),
                        Msg.t("hours", s.ah.durationMs() / 3_600_000), Msg.money("balance",
                                s.bank.balance(p.getUniqueId()), lang)))));
        int mail = s.mailbox.count(p.getUniqueId());
        m.set(50, Items.button(mail > 0 ? Material.CHEST : Material.BARREL, s.msg.text(lang, "mail.button"),
                List.of(s.msg.text(lang, "mail.button.lore", Msg.t("count", mail)))),
                pl -> s.plugin.mailboxUi().open(pl, 0));
        m.set(51, Items.button(Material.MAP, s.msg.text(lang, "orders.button"),
                List.of(s.msg.text(lang, "orders.button.lore"))), pl -> s.plugin.orderUi().browse(pl,
                new OrderUi.View(null, "", false, 0)));
        m.set(52, Items.button(Material.CLOCK, s.msg.text(lang, "menu.refresh")), pl -> browse(pl, view));
        m.open(p);
    }

    /** Oberste Reihe: Abteilungen und Suche (gemeinsam mit den Aufträgen). */
    void categoryRow(Player p, Menu m, View view, String lang) {
        ItemStack all = Items.button(Material.CHEST, s.msg.text(lang, "cat.all"));
        glow(all, view.category() == null);
        m.set(0, all, pl -> browse(pl, view.with(null)));
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            ItemStack icon = Items.button(CATEGORY_ICONS[i], s.msg.text(lang, "cat." + c.id()));
            glow(icon, c == view.category());
            m.set(1 + i, icon, pl -> browse(pl, view.with(c)));
        }
        List<Component> lore = new ArrayList<>();
        if (view.search() != null && !view.search().isBlank()) {
            lore.add(s.msg.text(lang, "search.current", Msg.t("text", view.search())));
            lore.add(s.msg.text(lang, "search.clear"));
        } else {
            lore.add(s.msg.text(lang, "search.lore"));
        }
        m.set(8, Items.button(Material.OAK_SIGN, s.msg.text(lang, "search.button"), lore), (pl, click) -> {
            if (click.isRightClick() && view.search() != null && !view.search().isBlank()) {
                browse(pl, view.searching(""));
                return;
            }
            pl.closeInventory();
            s.msg.send(pl, "search.ask");
            s.prompt.ask(pl, text -> browse(pl, view.searching(text)), () -> browse(pl, view));
        });
    }

    static void glow(ItemStack item, boolean on) {
        if (on) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
    }

    static String percent(int bp, String lang) {
        String v = bp % 100 == 0 ? Integer.toString(bp / 100) : String.format(java.util.Locale.ROOT, "%.2f", bp / 100.0);
        return ("en".equals(lang) ? v : v.replace('.', ',')) + " %";
    }

    private ItemStack listingIcon(Player p, Listing l, String lang) {
        ItemStack item = ItemCodec.stack(l.item());
        List<Component> lines = new ArrayList<>();
        lines.add(s.msg.text(lang, "ah.line.price", Msg.money("price", l.price(), lang)));
        if (l.item().amount() > 1) {
            lines.add(s.msg.text(lang, "ah.line.unit", Msg.money("unit", l.unitPrice(), lang)));
        }
        OptionalLong median = s.prices.median(l.item().key());
        if (median.isPresent()) {
            lines.add(s.msg.text(lang, "ah.line.market", Msg.money("unit", median.getAsLong(), lang)));
        }
        lines.add(s.msg.text(lang, "ah.line.seller", Msg.t("name", l.sellerName())));
        lines.add(s.msg.text(lang, "ah.line.left", Msg.t("time",
                Msg.duration(l.expires() - System.currentTimeMillis(), lang))));
        lines.add(s.msg.text(lang, l.seller().equals(p.getUniqueId()) ? "ah.line.own" : "ah.line.buy"));
        return Items.withLore(item, lines);
    }

    // ---------------------------------------------------------------- Kaufen

    void confirmBuy(Player p, Listing l, View back) {
        String lang = s.msg.lang(p);
        long balance = s.bank.balance(p.getUniqueId());
        List<Component> lines = new ArrayList<>();
        lines.add(s.msg.text(lang, "ah.line.price", Msg.money("price", l.price(), lang)));
        lines.add(s.msg.text(lang, "ah.line.seller", Msg.t("name", l.sellerName())));
        lines.add(s.msg.text(lang, "line.balance", Msg.money("balance", balance, lang)));
        s.confirm.open(p, s.msg.text(lang, "ah.confirm.title"), ItemCodec.stack(l.item()), lines,
                s.msg.text(lang, "ah.confirm.yes", Msg.money("price", l.price(), lang)),
                () -> buy(p, l.id(), back), () -> browse(p, back));
    }

    private void buy(Player p, long id, View back) {
        AuctionHouse.BuyResult r = s.ah.buy(id, p.getUniqueId(), p.getName());
        String lang = s.msg.lang(p);
        switch (r.status()) {
            case OK -> {
                Listing l = r.listing();
                boolean inHand = s.deliveries.takeAll(p)[1] == 0;
                s.msg.send(p, "ah.bought", Msg.t("amount", l.item().amount()),
                        Msg.c("item", ItemCodec.name(ItemCodec.one(l.item()))), Msg.money("price", l.price(), lang),
                        Msg.t("seller", l.sellerName()));
                if (!inHand) {
                    s.msg.send(p, "mail.waiting");
                }
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                s.reporter.purchase(p.getUniqueId());
                s.reporter.sale(l.seller());
                s.noticeSender.flush(l.seller());
            }
            case GONE -> {
                s.msg.send(p, "ah.gone");
                browse(p, back);
            }
            case OWN -> s.msg.send(p, "ah.own");
            case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", Msg.money("missing",
                    (r.listing() == null ? 0 : r.listing().price()) - s.bank.balance(p.getUniqueId()), lang));
        }
    }

    // ---------------------------------------------------------------- Verkaufen

    /** Preis wählen für den Gegenstand in der Hand. */
    public void sell(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            s.msg.send(p, "ah.sell.empty-hand");
            p.closeInventory();
            return;
        }
        ItemStack snapshot = hand.clone();
        ItemData data = ItemCodec.of(snapshot);
        OptionalLong median = s.prices.median(data.key());
        long start = median.isPresent() ? Math.max(1, median.getAsLong() * snapshot.getAmount()) : 100L * snapshot.getAmount();
        String lang = s.msg.lang(p);
        s.picker.open(p, s.msg.text(lang, "ah.sell.title"), snapshot, Picker.Kind.MONEY, start, 1, Money.MAX,
                price -> {
                    List<Component> lines = new ArrayList<>();
                    if (median.isPresent()) {
                        lines.add(s.msg.text(lang, "ah.line.market", Msg.money("unit", median.getAsLong(), lang)));
                        lines.add(s.msg.text(lang, "ah.sell.market-total", Msg.money("price",
                                median.getAsLong() * snapshot.getAmount(), lang)));
                    } else {
                        lines.add(s.msg.text(lang, "ah.sell.no-market"));
                    }
                    long fee = Fees.of(price, s.ah.feeBasisPoints());
                    lines.add(s.msg.text(lang, "ah.sell.you-get", Msg.money("net", price - fee, lang),
                            Msg.money("fee", fee, lang)));
                    return lines;
                }, price -> {
                    p.closeInventory();
                    sellNow(p, snapshot, price);
                }, null);
    }

    /** Gegenstand aus der Hand nehmen und anbieten (zurück in die Hand, wenn es nicht klappt). */
    void sellNow(Player p, ItemStack snapshot, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            s.msg.send(p, "ah.sell.empty-hand");
            return;
        }
        if (!hand.equals(snapshot)) {
            s.msg.send(p, "ah.sell.changed");
            return;
        }
        if (s.ah.countActive(p.getUniqueId()) >= s.ah.maxActive()) {
            s.msg.send(p, "ah.too-many", Msg.t("max", s.ah.maxActive()));
            return;
        }
        ItemData data = ItemCodec.of(hand);
        p.getInventory().setItemInMainHand(null);
        AuctionHouse.ListResult r;
        try {
            r = s.ah.list(p.getUniqueId(), p.getName(), data, price);
        } catch (RuntimeException e) {
            p.getInventory().setItemInMainHand(snapshot);
            throw e;
        }
        if (r.status() != AuctionHouse.ListStatus.OK) {
            if (p.getInventory().getItemInMainHand().getType().isAir()) {
                p.getInventory().setItemInMainHand(snapshot);
            } else {
                s.deliveries.giveOrMail(p, snapshot, "ah_zurueck");
            }
            s.msg.send(p, r.status() == AuctionHouse.ListStatus.TOO_MANY ? "ah.too-many" : "money.invalid",
                    Msg.t("max", s.ah.maxActive()));
            return;
        }
        String lang = s.msg.lang(p);
        long fee = Fees.of(price, s.ah.feeBasisPoints());
        s.msg.send(p, "ah.listed", Msg.t("amount", data.amount()), Msg.c("item", ItemCodec.name(snapshot)),
                Msg.money("price", price, lang), Msg.money("fee", fee, lang),
                Msg.t("hours", s.ah.durationMs() / 3_600_000));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.3f);
    }

    // ---------------------------------------------------------------- Eigene Angebote

    public void mine(Player p, int page) {
        String lang = s.msg.lang(p);
        List<Listing> list = s.ah.activeOf(p.getUniqueId());
        Menu m = new Menu(4, s.msg.text(lang, "ah.mine.title", Msg.t("count", list.size()),
                Msg.t("max", s.ah.maxActive())));
        for (int i = 0; i < list.size() && i < 27; i++) {
            Listing l = list.get(i);
            m.set(i, listingIcon(p, l, lang), pl -> confirmCancel(pl, l, () -> mine(pl, 0)));
        }
        m.set(27, Items.button(Material.ARROW, s.msg.text(lang, "menu.back")),
                pl -> browse(pl, new View(null, "", Sort.CHEAPEST, 0)));
        m.set(31, Items.button(Material.EMERALD, s.msg.text(lang, "ah.sell"), List.of(s.msg.text(lang, "ah.sell.lore"))),
                this::sell);
        m.set(35, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    private void confirmCancel(Player p, Listing l, Runnable back) {
        String lang = s.msg.lang(p);
        s.confirm.open(p, s.msg.text(lang, "ah.cancel.title"), ItemCodec.stack(l.item()),
                List.of(s.msg.text(lang, "ah.cancel.lore")), s.msg.text(lang, "ah.cancel.yes"), () -> {
                    if (s.ah.cancel(l.id(), p.getUniqueId(), false)) {
                        s.deliveries.takeAll(p);
                        s.msg.send(p, "ah.cancelled");
                    } else {
                        s.msg.send(p, "ah.gone");
                    }
                    back.run();
                }, back);
    }
}
