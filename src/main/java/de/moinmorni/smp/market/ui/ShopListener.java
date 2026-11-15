package de.moinmorni.smp.market.ui;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.ItemData;
import de.moinmorni.smp.market.Shops;
import de.moinmorni.smp.market.Shops.Shop;
import de.moinmorni.smp.market.Stalls;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.text.Texts;
import de.moinmorni.smp.town.Layout.StallSpot;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import de.moinmorni.smp.ui.Picker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Ladenkisten: Kiste oder Fass, davor/darauf ein Schild mit „[Laden]“ (oder „[Shop]“) – nur auf dem eigenen
 * Grundstück oder am gemieteten Marktstand. Die Ware im Inventar wird zur Vorlage; danach Verkauf/Ankauf, Menge und
 * Preis wählen. Kunden klicken aufs Schild. Die Kiste öffnet nur der Besitzer, Trichter ziehen nichts heraus.
 */
public final class ShopListener implements Listener {

    private final Smp s;
    /** Schilder, die auf ihre Einrichtung warten: Ort → Besitzer. */
    private final Map<String, UUID> pending = new HashMap<>();

    public ShopListener(Smp s) {
        this.s = s;
    }

    private static String key(Block b) {
        return Shops.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    // ---------------------------------------------------------------- Schild aufstellen

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        Component first = e.line(0);
        String text = first == null ? "" : PlainTextComponentSerializer.plainText().serialize(first).strip();
        if (!text.equalsIgnoreCase("[laden]") && !text.equalsIgnoreCase("[shop]")) {
            return;
        }
        Player p = e.getPlayer();
        Block container = containerFor(e.getBlock());
        if (container == null) {
            s.msg.send(p, "shop.no-container");
            e.setCancelled(true);
            return;
        }
        int stall = allowedHere(p, container);
        if (stall < 0) {
            s.msg.send(p, "shop.not-here");
            e.setCancelled(true);
            return;
        }
        if (shopFor(container).isPresent()) {
            s.msg.send(p, "shop.exists");
            e.setCancelled(true);
            return;
        }
        String lang = s.msg.lang(p);
        e.line(0, Texts.MM.deserialize("<dark_green>[Laden]</dark_green>"));
        e.line(1, Component.text(s.msg.raw(lang, "shop.sign.setup")));
        e.line(2, Component.empty());
        e.line(3, Component.text(p.getName()));
        Block sign = e.getBlock();
        pending.put(key(sign), p.getUniqueId());
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            s.msg.send(p, "shop.hold-item");
            return;
        }
        ItemStack template = hand.clone();
        s.plugin.getServer().getScheduler().runTask(s.plugin, () -> setup(p, sign, container, template, stall, null));
    }

    /** Kiste/Fass, an dem das Schild hängt (Wandschild) bzw. auf dem es steht. */
    private static Block containerFor(Block sign) {
        Block target;
        if (sign.getBlockData() instanceof WallSign ws) {
            target = sign.getRelative(ws.getFacing().getOppositeFace());
        } else {
            target = sign.getRelative(BlockFace.DOWN);
        }
        Material t = target.getType();
        return t == Material.CHEST || t == Material.BARREL || t == Material.TRAPPED_CHEST ? target : null;
    }

    /** Darf der Spieler hier einen Laden eröffnen? -1 = nein, 0 = eigenes Grundstück, n = eigener Stand n. */
    private int allowedHere(Player p, Block container) {
        StallSpot spot = s.town.stallZone(container);
        if (spot != null) {
            Optional<Stalls.Stall> st = s.stalls.get(spot.id());
            boolean mine = st.isPresent() && p.getUniqueId().equals(st.get().tenant())
                    && st.get().paidUntil() > System.currentTimeMillis();
            return mine || s.bypass(p) ? spot.id() : -1;
        }
        Claims.Claim c = s.claims.at(container.getWorld().getName(), container.getX() >> 4, container.getZ() >> 4);
        if (c != null && c.owner().equals(p.getUniqueId())) {
            return 0;
        }
        return s.bypass(p) ? 0 : -1;
    }

    // ---------------------------------------------------------------- Einrichten

    /** Einrichtungs-Menü: Ware, Verkauf/Ankauf, Menge pro Klick, Preis. */
    private void setup(Player p, Block sign, Block container, ItemStack template, int stall, Shop existing) {
        String lang = s.msg.lang(p);
        Shops.Mode[] mode = {existing == null ? Shops.Mode.SELL : existing.mode()};
        int[] amount = {existing == null ? Math.min(template.getAmount(), template.getMaxStackSize())
                : existing.item().amount()};
        ItemStack one = template.asOne();
        long[] price = {existing == null ? s.prices.median(ItemCodec.key(one)).orElse(100) * amount[0]
                : existing.price()};
        Menu m = new Menu(3, s.msg.text(lang, "shop.setup.title"));
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            m.show(4, Items.withLore(one.asQuantity(Math.max(1, Math.min(amount[0], one.getMaxStackSize()))),
                    List.of(s.msg.text(lang, "shop.setup.item"))));
            m.set(10, Items.button(mode[0] == Shops.Mode.SELL ? Material.EMERALD : Material.GOLD_INGOT,
                    s.msg.text(lang, mode[0] == Shops.Mode.SELL ? "shop.mode.sell" : "shop.mode.buy"),
                    List.of(s.msg.text(lang, mode[0] == Shops.Mode.SELL ? "shop.mode.sell.lore" : "shop.mode.buy.lore"),
                            s.msg.text(lang, "shop.mode.toggle"))), pl -> {
                        mode[0] = mode[0] == Shops.Mode.SELL ? Shops.Mode.BUY : Shops.Mode.SELL;
                        draw[0].run();
                    });
            m.set(12, Items.button(Material.CHEST, s.msg.text(lang, "shop.setup.amount", Msg.t("amount", amount[0]))),
                    pl -> s.picker.open(pl, s.msg.text(lang, "shop.setup.amount", Msg.t("amount", amount[0])), one,
                            Picker.Kind.AMOUNT, amount[0], 1, one.getMaxStackSize() * 4L, null, v -> {
                                amount[0] = (int) v;
                                draw[0].run();
                                m.open(pl);
                            }, () -> m.open(pl)));
            m.set(14, Items.button(Material.GOLD_NUGGET, s.msg.text(lang, "shop.setup.price",
                    Msg.money("price", price[0], lang))), pl -> s.picker.open(pl,
                    s.msg.text(lang, "shop.setup.price", Msg.money("price", price[0], lang)), one, Picker.Kind.MONEY,
                    price[0], 1, Money.MAX, v -> {
                        List<Component> l = new ArrayList<>();
                        s.prices.median(ItemCodec.key(one)).ifPresent(md -> l.add(s.msg.text(lang, "ah.line.market",
                                Msg.money("unit", md, lang))));
                        return l;
                    }, v -> {
                        price[0] = v;
                        draw[0].run();
                        m.open(pl);
                    }, () -> m.open(pl)));
            m.set(16, Items.button(Material.EMERALD_BLOCK, s.msg.text(lang, "shop.setup.open"),
                    List.of(s.msg.text(lang, mode[0] == Shops.Mode.SELL ? "shop.sign.sells" : "shop.sign.buys",
                            Msg.t("amount", amount[0]), Msg.money("price", price[0], lang)))), pl -> {
                        pl.closeInventory();
                        open(pl, sign, container, one.asQuantity(1), amount[0], mode[0], price[0], stall, existing);
                    });
            if (existing != null) {
                m.set(22, Items.button(Material.BARRIER, s.msg.text(lang, "shop.remove")), pl -> {
                    pl.closeInventory();
                    s.shops.delete(existing.id());
                    clearSign(sign);
                    s.msg.send(pl, "shop.removed");
                });
            }
        };
        draw[0].run();
        m.open(p);
    }

    private void open(Player p, Block sign, Block container, ItemStack one, int amount, Shops.Mode mode, long price,
                      int stall, Shop existing) {
        if (!(sign.getState() instanceof Sign) || containerFor(sign) == null) {
            s.msg.send(p, "shop.gone");
            return;
        }
        if (existing == null && !p.getUniqueId().equals(pending.get(key(sign)))) {
            s.msg.send(p, "shop.gone");
            return;
        }
        pending.remove(key(sign));
        ItemData data = ItemCodec.of(one).withAmount(amount);
        if (existing != null) {
            s.shops.delete(existing.id());
        }
        Shop shop = s.shops.create(container.getWorld().getName(), container.getX(), container.getY(), container.getZ(),
                sign.getX(), sign.getY(), sign.getZ(), p.getUniqueId(), p.getName(), data, mode, price, stall);
        writeSign(sign, shop, one);
        s.msg.send(p, "shop.opened", Msg.c("item", ItemCodec.name(one)));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.4f);
    }

    private void writeSign(Block block, Shop shop, ItemStack one) {
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        String price = Money.format(shop.price(), "de");
        for (Side side : Side.values()) {
            var sd = sign.getSide(side);
            sd.line(0, Texts.MM.deserialize(shop.mode() == Shops.Mode.SELL ? "<dark_green><b>[Laden]</b>"
                    : "<dark_blue><b>[Ankauf]</b>"));
            sd.line(1, ItemCodec.name(one));
            sd.line(2, Component.text(shop.item().amount() + " × " + price));
            sd.line(3, Component.text(shop.ownerName()));
        }
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private static void clearSign(Block block) {
        if (block.getState() instanceof Sign sign) {
            for (Side side : Side.values()) {
                for (int i = 0; i < 4; i++) {
                    sign.getSide(side).line(i, Component.empty());
                }
            }
            sign.setWaxed(false);
            sign.update(true, false);
        }
    }

    // ---------------------------------------------------------------- Kunden

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        if (Tag.ALL_SIGNS.isTagged(b.getType())) {
            Optional<Shop> shop = s.shops.bySign(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (shop.isPresent()) {
                e.setCancelled(true);
                if (shop.get().owner().equals(p.getUniqueId())) {
                    setup(p, b, containerFor(b), ItemCodec.one(shop.get().item()), shop.get().stall(), shop.get());
                } else {
                    customer(p, shop.get());
                }
                return;
            }
            UUID waiting = pending.get(key(b));
            if (waiting != null && waiting.equals(p.getUniqueId())) {
                e.setCancelled(true);
                ItemStack hand = p.getInventory().getItemInMainHand();
                Block container = containerFor(b);
                if (hand.getType().isAir() || container == null) {
                    s.msg.send(p, "shop.hold-item");
                    return;
                }
                int stall = allowedHere(p, container);
                if (stall < 0) {
                    s.msg.send(p, "shop.not-here");
                    return;
                }
                setup(p, b, container, hand.clone(), stall, null);
            }
            return;
        }
        Optional<Shop> shop = shopFor(b);
        if (shop.isPresent() && !shop.get().owner().equals(p.getUniqueId()) && !s.bypass(p)) {
            e.setCancelled(true);
            customer(p, shop.get());
        }
    }

    private void customer(Player p, Shop shop) {
        String lang = s.msg.lang(p);
        ItemStack one = ItemCodec.one(shop.item());
        Inventory stock = containerInventory(shop);
        Menu m = new Menu(3, s.msg.text(lang, shop.mode() == Shops.Mode.SELL ? "shop.buy.title" : "shop.sell.title",
                Msg.t("owner", shop.ownerName())));
        int amount = shop.item().amount();
        int clicks;
        if (stock == null) {
            clicks = 0;
        } else if (shop.mode() == Shops.Mode.SELL) {
            clicks = ItemCodec.count(stock, one) / amount;
        } else {
            clicks = ItemCodec.room(stock, one) / amount;
        }
        List<Component> info = new ArrayList<>();
        info.add(s.msg.text(lang, shop.mode() == Shops.Mode.SELL ? "shop.sign.sells" : "shop.sign.buys",
                Msg.t("amount", amount), Msg.money("price", shop.price(), lang)));
        info.add(s.msg.text(lang, shop.mode() == Shops.Mode.SELL ? "shop.stock" : "shop.space", Msg.t("count", clicks)));
        s.prices.median(shop.item().key()).ifPresent(md -> info.add(s.msg.text(lang, "ah.line.market",
                Msg.money("unit", md, lang))));
        info.add(s.msg.text(lang, "line.balance", Msg.money("balance", s.bank.balance(p.getUniqueId()), lang)));
        m.show(4, Items.withLore(one.asQuantity(Math.max(1, Math.min(amount, one.getMaxStackSize()))), info));
        m.set(13, Items.button(shop.mode() == Shops.Mode.SELL ? Material.EMERALD_BLOCK : Material.GOLD_BLOCK,
                s.msg.text(lang, shop.mode() == Shops.Mode.SELL ? "shop.buy.button" : "shop.sell.button",
                        Msg.t("amount", amount), Msg.money("price", shop.price(), lang))), pl -> {
                    trade(pl, shop);
                    customer(pl, s.shops.get(shop.id()).orElse(shop));
                });
        m.set(22, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    /** Ein Klick: Ware und Geld bewegen. Erst die Ware sichern, dann buchen, bei Fehlschlag zurücklegen. */
    private void trade(Player p, Shop shop) {
        Optional<Shop> fresh = s.shops.get(shop.id());
        if (fresh.isEmpty()) {
            s.msg.send(p, "shop.gone");
            return;
        }
        Inventory stock = containerInventory(shop);
        if (stock == null) {
            s.msg.send(p, "shop.gone");
            return;
        }
        ItemStack one = ItemCodec.one(shop.item());
        int amount = shop.item().amount();
        String lang = s.msg.lang(p);
        if (shop.mode() == Shops.Mode.SELL) {
            if (ItemCodec.count(stock, one) < amount) {
                s.msg.send(p, "shop.sold-out");
                return;
            }
            if (s.bank.balance(p.getUniqueId()) < shop.price()) {
                s.msg.send(p, "money.not-enough", Msg.money("missing", shop.price() - s.bank.balance(p.getUniqueId()),
                        lang));
                return;
            }
            int removed = ItemCodec.remove(stock, one, amount);
            Shops.TradeResult r = s.shops.trade(shop.id(), p.getUniqueId(), p.getName());
            if (r.status() != Shops.TradeStatus.OK) {
                putBack(stock, one, removed, shop.owner());
                s.msg.send(p, r.status() == Shops.TradeStatus.NOT_ENOUGH ? "money.not-enough" : "shop.gone",
                        Msg.money("missing", shop.price() - s.bank.balance(p.getUniqueId()), lang));
                return;
            }
            int left = removed;
            while (left > 0) {
                int n = Math.min(left, one.getMaxStackSize());
                s.deliveries.giveOrMail(p, one.asQuantity(n), "laden");
                left -= n;
            }
            s.msg.send(p, "shop.bought", Msg.t("amount", amount), Msg.c("item", ItemCodec.name(one)),
                    Msg.money("price", shop.price(), lang), Msg.t("owner", shop.ownerName()));
            s.reporter.purchase(p.getUniqueId());
            s.reporter.sale(shop.owner());
        } else {
            if (ItemCodec.count(p.getInventory(), one) < amount) {
                s.msg.send(p, "shop.sell.missing", Msg.t("amount", amount), Msg.c("item", ItemCodec.name(one)));
                return;
            }
            if (ItemCodec.room(stock, one) < amount) {
                s.msg.send(p, "shop.full");
                return;
            }
            int removed = ItemCodec.remove(p.getInventory(), one, amount);
            Shops.TradeResult r = s.shops.trade(shop.id(), p.getUniqueId(), p.getName());
            if (r.status() != Shops.TradeStatus.OK) {
                int left = removed;
                while (left > 0) {
                    int n = Math.min(left, one.getMaxStackSize());
                    s.deliveries.giveOrMail(p, one.asQuantity(n), "laden");
                    left -= n;
                }
                s.msg.send(p, r.status() == Shops.TradeStatus.NOT_ENOUGH ? "shop.owner-broke" : "shop.gone");
                return;
            }
            putBack(stock, one, removed, shop.owner());
            s.msg.send(p, "shop.sold", Msg.t("amount", amount), Msg.c("item", ItemCodec.name(one)),
                    Msg.money("price", shop.price(), lang), Msg.t("owner", shop.ownerName()));
            s.reporter.sale(p.getUniqueId());
            s.reporter.purchase(shop.owner());
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.1f);
        s.noticeSender.flush(shop.owner());
    }

    private void putBack(Inventory stock, ItemStack one, int amount, UUID owner) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, one.getMaxStackSize());
            for (ItemStack rest : stock.addItem(one.asQuantity(n)).values()) {
                s.mailbox.add(owner, ItemCodec.of(rest), "laden");
            }
            left -= n;
        }
    }

    private Inventory containerInventory(Shop shop) {
        var w = s.plugin.getServer().getWorld(shop.world());
        if (w == null) {
            return null;
        }
        Block b = w.getBlockAt(shop.x(), shop.y(), shop.z());
        if (b.getState() instanceof Chest c) {
            return c.getInventory();
        }
        if (b.getState() instanceof Barrel br) {
            return br.getInventory();
        }
        return null;
    }

    // ---------------------------------------------------------------- Schutz

    /** Laden zu einem Block (auch zur zweiten Hälfte einer Doppelkiste). */
    public Optional<Shop> shopFor(Block b) {
        String w = b.getWorld().getName();
        if (s.shops.isContainer(w, b.getX(), b.getY(), b.getZ())) {
            return s.shops.byContainer(w, b.getX(), b.getY(), b.getZ());
        }
        if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
            if (b.getState() instanceof Chest c && c.getInventory().getHolder() instanceof DoubleChest dc) {
                for (InventoryHolder half : new InventoryHolder[] {dc.getLeftSide(), dc.getRightSide()}) {
                    if (half instanceof Chest hc) {
                        Location l = hc.getLocation();
                        if (s.shops.isContainer(w, l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                            return s.shops.byContainer(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isShopBlock(Block b) {
        String w = b.getWorld().getName();
        return s.shops.isContainer(w, b.getX(), b.getY(), b.getZ()) || s.shops.isSign(w, b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String w = b.getWorld().getName();
        Optional<Shop> shop = s.shops.isSign(w, b.getX(), b.getY(), b.getZ())
                ? s.shops.bySign(w, b.getX(), b.getY(), b.getZ()) : shopFor(b);
        if (shop.isEmpty()) {
            pending.remove(key(b));
            return;
        }
        Player p = e.getPlayer();
        if (!shop.get().owner().equals(p.getUniqueId()) && !s.bypass(p)) {
            e.setCancelled(true);
            s.msg.bar(p, "shop.protected");
            return;
        }
        s.shops.delete(shop.get().id());
        s.msg.send(p, "shop.removed");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Location l = e.getSource().getLocation();
        if (l == null) {
            return;
        }
        Block b = l.getBlock();
        if (shopFor(b).isPresent()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    private boolean protectedBlock(Block b) {
        return isShopBlock(b) || shopFor(b).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (protectedBlock(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (protectedBlock(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Alle Läden eines Marktstands entfernen (beim Räumen). */
    public void removeStallShops(int stall) {
        for (Shop sh : s.shops.inStall(stall)) {
            s.shops.delete(sh.id());
        }
    }

    /** Läden in einem Chunk entfernen (Grundstück aufgegeben). Liefert die Zahl. */
    public int removeInChunk(String world, int cx, int cz) {
        int n = 0;
        for (Iterator<Shop> it = s.shops.all().iterator(); it.hasNext(); ) {
            Shop sh = it.next();
            if (sh.world().equals(world) && sh.x() >> 4 == cx && sh.z() >> 4 == cz && sh.stall() == 0) {
                s.shops.delete(sh.id());
                n++;
            }
        }
        return n;
    }
}
