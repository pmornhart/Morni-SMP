package net.morni.smp.market.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.morni.smp.Smp;
import net.morni.smp.market.Stalls;
import net.morni.smp.market.Stalls.Stall;
import net.morni.smp.text.Msg;
import net.morni.smp.town.Layout.Box;
import net.morni.smp.town.Layout.StallSpot;
import net.morni.smp.ui.Items;
import net.morni.smp.ui.Menu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Marktstände: mieten, verlängern, kündigen (Befehl {@code /stand} oder Klick aufs Hängeschild), Warnung vor dem
 * Ablauf und Räumen danach – Kisten samt Inhalt und Schilder kommen ins Abholfach des Mieters.
 */
public final class StallUi {

    private final Smp s;

    public StallUi(Smp s) {
        this.s = s;
    }

    public void command(Player p, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "mieten", "rent" -> rent(p);
            case "verlaengern", "verlängern", "extend" -> extend(p);
            case "kuendigen", "kündigen", "aufgeben", "release" -> release(p);
            default -> info(p);
        }
    }

    private StallSpot here(Player p) {
        return s.town.stallFront(p.getLocation());
    }

    private void rent(Player p) {
        StallSpot spot = here(p);
        if (spot == null) {
            s.msg.send(p, "stall.stand-there");
            return;
        }
        rent(p, spot.id());
    }

    public void rent(Player p, int id) {
        Stalls.RentResult r = s.stalls.rent(id, p.getUniqueId(), p.getName());
        String lang = s.msg.lang(p);
        switch (r.status()) {
            case OK -> {
                s.msg.send(p, "stall.rented", Msg.t("id", id), Msg.money("rent", s.stalls.rent(), lang),
                        Msg.t("until", until(r.stall(), lang)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
            }
            case EXTENDED -> s.msg.send(p, "stall.extended", Msg.t("id", id), Msg.t("until", until(r.stall(), lang)));
            case TAKEN -> s.msg.send(p, "stall.taken", Msg.t("id", id),
                    Msg.t("name", r.stall() == null ? "?" : r.stall().tenantName()));
            case HAS_OTHER -> s.msg.send(p, "stall.has-other", Msg.t("id", r.stall().id()));
            case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", Msg.money("missing",
                    s.stalls.rent() - s.bank.balance(p.getUniqueId()), lang));
            case TOO_FAR_AHEAD -> s.msg.send(p, "stall.too-far");
            case UNKNOWN, NOT_TENANT -> s.msg.send(p, "stall.stand-there");
        }
        s.boards.stallSigns();
    }

    private void extend(Player p) {
        Optional<Stall> own = s.stalls.of(p.getUniqueId());
        if (own.isEmpty()) {
            s.msg.send(p, "stall.none");
            return;
        }
        rent(p, own.get().id());
    }

    private void release(Player p) {
        Optional<Stall> own = s.stalls.of(p.getUniqueId());
        if (own.isEmpty()) {
            s.msg.send(p, "stall.none");
            return;
        }
        String lang = s.msg.lang(p);
        s.confirm.open(p, s.msg.text(lang, "stall.release.title"), new ItemStack(Material.SPRUCE_HANGING_SIGN),
                List.of(s.msg.text(lang, "stall.release.lore")), s.msg.text(lang, "stall.release.yes"), () -> {
                    Optional<Stall> still = s.stalls.of(p.getUniqueId());
                    if (still.isEmpty()) {
                        return;
                    }
                    int n = evacuate(still.get());
                    s.stalls.free(still.get().id());
                    s.boards.stallSigns();
                    s.msg.send(p, "stall.released", Msg.t("count", n));
                }, null);
    }

    private void info(Player p) {
        StallSpot spot = here(p);
        Optional<Stall> st = spot != null ? s.stalls.get(spot.id()) : s.stalls.of(p.getUniqueId());
        if (st.isEmpty()) {
            s.msg.send(p, "stall.help", Msg.money("rent", s.stalls.rent(), s.msg.lang(p)));
            return;
        }
        menu(p, st.get().id());
    }

    /** Menü zu einem Stand (auch per Klick aufs Hängeschild). */
    public void menu(Player p, int id) {
        Optional<Stall> st = s.stalls.get(id);
        if (st.isEmpty()) {
            return;
        }
        Stall stall = st.get();
        String lang = s.msg.lang(p);
        Menu m = new Menu(3, s.msg.text(lang, "stall.title", Msg.t("id", id)));
        List<Component> lore = new ArrayList<>();
        if (stall.free()) {
            lore.add(s.msg.text(lang, "stall.free", Msg.money("rent", s.stalls.rent(), lang)));
        } else {
            lore.add(s.msg.text(lang, "stall.tenant", Msg.t("name", stall.tenantName())));
            lore.add(s.msg.text(lang, "stall.until", Msg.t("until", until(stall, lang))));
        }
        lore.add(s.msg.text(lang, "stall.explain"));
        m.show(4, Items.button(Material.SPRUCE_HANGING_SIGN, s.msg.text(lang, "stall.title", Msg.t("id", id)), lore));
        boolean mine = p.getUniqueId().equals(stall.tenant());
        if (stall.free()) {
            m.set(13, Items.button(Material.EMERALD_BLOCK, s.msg.text(lang, "stall.rent.button",
                    Msg.money("rent", s.stalls.rent(), lang))), pl -> {
                        pl.closeInventory();
                        rent(pl, id);
                    });
        } else if (mine) {
            m.set(11, Items.button(Material.EMERALD, s.msg.text(lang, "stall.extend.button",
                    Msg.money("rent", s.stalls.rent(), lang))), pl -> {
                        pl.closeInventory();
                        rent(pl, id);
                    });
            m.set(15, Items.button(Material.BARRIER, s.msg.text(lang, "stall.release.button")), pl -> release(pl));
        }
        m.set(22, Items.button(Material.OAK_DOOR, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    private String until(Stall st, String lang) {
        return st == null ? "?" : Msg.duration(st.paidUntil() - System.currentTimeMillis(), lang);
    }

    /** Jede Minute: warnen, abgelaufene räumen. */
    public void tick() {
        if (!s.town.ready()) {
            return;
        }
        for (Stall st : s.stalls.warnDue()) {
            s.notices.add(st.tenant(), "stall.warn", "id", Integer.toString(st.id()));
            s.noticeSender.flush(st.tenant());
        }
        for (Stall st : s.stalls.expired()) {
            int n = evacuate(st);
            s.stalls.free(st.id());
            s.notices.add(st.tenant(), "stall.expired", "id", Integer.toString(st.id()), "count", Integer.toString(n));
            s.noticeSender.flush(st.tenant());
            s.boards.stallSigns();
        }
    }

    /**
     * Stand räumen: Inhalt aller Kisten/Fässer und die Kisten selbst sowie Schilder ins Abholfach des Mieters,
     * Läden schließen. Liefert die Zahl der Einträge im Abholfach.
     */
    public int evacuate(Stall st) {
        StallSpot spot = s.town.stall(st.id());
        if (spot == null || st.tenant() == null || !s.town.known()) {
            return 0;
        }
        UUID tenant = st.tenant();
        s.plugin.shopListener().removeStallShops(st.id());
        int n = 0;
        Box z = spot.zone();
        for (int x = z.x1(); x <= z.x2(); x++) {
            for (int y = z.y1(); y <= z.y2(); y++) {
                for (int zz = z.z1(); zz <= z.z2(); zz++) {
                    Block b = s.town.block(x, y, zz);
                    Material type = b.getType();
                    if (!movable(type)) {
                        continue;
                    }
                    BlockState state = b.getState();
                    Inventory inv = null;
                    if (state instanceof Chest c) {
                        inv = c.getBlockInventory();
                    } else if (state instanceof Container c) {
                        inv = c.getInventory();
                    }
                    if (inv != null) {
                        for (ItemStack it : inv.getContents()) {
                            if (it != null && !it.getType().isAir()) {
                                s.mailbox.add(tenant, ItemCodec.of(it), "stand_geraeumt");
                                n++;
                            }
                        }
                        inv.clear();
                    }
                    Material item = type.isItem() ? type : signItem(type);
                    if (item != null) {
                        s.mailbox.add(tenant, ItemCodec.of(new ItemStack(item)), "stand_geraeumt");
                        n++;
                    }
                    b.setType(Material.AIR, false);
                }
            }
        }
        return n;
    }

    /** Was ein Mieter am Stand aufstellen darf (und was beim Räumen mitgenommen wird). */
    public static boolean movable(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.BARREL || Tag.ALL_SIGNS.isTagged(m);
    }

    /** Wand- und Hängeschilder haben kein eigenes Item – das passende Schild. */
    private static Material signItem(Material wall) {
        String n = wall.name().replace("_WALL_HANGING_SIGN", "_HANGING_SIGN").replace("_WALL_SIGN", "_SIGN");
        Material m = Material.matchMaterial(n);
        return m != null && m.isItem() ? m : null;
    }

    /** Für den Stadtschutz: Darf der Spieler an diesem Stand gerade bauen? */
    public boolean mayBuild(Player p, StallSpot spot) {
        Optional<Stall> st = s.stalls.get(spot.id());
        return st.isPresent() && p.getUniqueId().equals(st.get().tenant())
                && st.get().paidUntil() > System.currentTimeMillis();
    }
}
