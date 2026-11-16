package net.morni.smp.market.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.morni.smp.Smp;
import net.morni.smp.market.Buyback;
import net.morni.smp.text.Msg;
import net.morni.smp.ui.Items;
import net.morni.smp.ui.Menu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Ankauf des Händlerhauses: Gegenstände in die oberen fünf Reihen legen, unten steht sofort, was es dafür gibt (mit dem
 * sinkenden Tagespreis). „Verkaufen“ nimmt nur, was die Stadt kauft; alles andere bleibt liegen und kommt beim
 * Schließen zurück ins Inventar.
 */
public final class BuybackUi {

    private static final int FREE = 45;

    private final Smp s;

    public BuybackUi(Smp s) {
        this.s = s;
    }

    public void open(Player p) {
        String lang = s.msg.lang(p);
        Menu m = new Menu(6, s.msg.text(lang, "buyback.title"));
        int[] free = new int[FREE];
        for (int i = 0; i < FREE; i++) {
            free[i] = i;
        }
        m.free(free);
        for (int i = 45; i < 54; i++) {
            m.show(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        m.set(45, Items.button(Material.EMERALD, s.msg.text(lang, "buyback.prices"),
                List.of(s.msg.text(lang, "buyback.prices.lore"))), pl -> s.plugin.shopUi().main(pl));
        Runnable redraw = () -> m.set(49, totalButton(p, m, lang), pl -> sell(pl, m));
        m.onChange(redraw);
        redraw.run();
        m.set(53, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.onClose(pl -> giveBack(pl, m));
        m.open(p);
    }

    /** Was die Stadt kaufen würde: Sorte → Stück (nur unveränderte Gegenstände). */
    private Map<String, Integer> sellable(Menu m) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < FREE; i++) {
            ItemStack it = m.getInventory().getItem(i);
            if (plain(it) && s.buyback.offers().containsKey(it.getType().getKey().toString())) {
                out.merge(it.getType().getKey().toString(), it.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    private static boolean plain(ItemStack it) {
        return it != null && !it.getType().isAir() && it.isSimilar(new ItemStack(it.getType()));
    }

    private ItemStack totalButton(Player p, Menu m, String lang) {
        Map<String, Integer> items = sellable(m);
        Buyback.Quote q = s.buyback.quote(p.getUniqueId(), items, s.today());
        List<Component> lore = new ArrayList<>();
        for (Buyback.Line l : q.lines()) {
            lore.add(s.msg.text(lang, "buyback.line", Msg.t("amount", l.amount()),
                    Msg.c("item", NoticeSender.itemName(l.key())), Msg.money("money", l.payout(), lang)));
        }
        if (q.lines().isEmpty()) {
            lore.add(s.msg.text(lang, "buyback.put-in"));
        } else {
            lore.add(s.msg.text(lang, "buyback.click"));
        }
        return Items.button(q.total() > 0 ? Material.EMERALD_BLOCK : Material.EMERALD,
                s.msg.text(lang, "buyback.total", Msg.money("money", q.total(), lang)), lore);
    }

    private void sell(Player p, Menu m) {
        Map<String, Integer> items = sellable(m);
        if (items.isEmpty()) {
            s.msg.send(p, "buyback.nothing");
            return;
        }
        // erst aus dem Menü nehmen, dann buchen (geht die Buchung schief, kommt alles zurück)
        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < FREE; i++) {
            ItemStack it = m.getInventory().getItem(i);
            if (plain(it) && items.containsKey(it.getType().getKey().toString())) {
                taken.add(it.clone());
                m.getInventory().setItem(i, null);
            }
        }
        Buyback.Quote q;
        try {
            q = s.buyback.sell(p.getUniqueId(), items, s.today());
        } catch (RuntimeException e) {
            for (ItemStack it : taken) {
                m.getInventory().addItem(it);
            }
            throw e;
        }
        String lang = s.msg.lang(p);
        s.msg.send(p, "buyback.sold", Msg.money("money", q.total(), lang),
                Msg.t("count", items.values().stream().mapToInt(Integer::intValue).sum()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.9f);
        m.set(49, totalButton(p, m, lang), pl -> sell(pl, m));
    }

    private void giveBack(Player p, Menu m) {
        boolean any = false;
        for (int i = 0; i < FREE; i++) {
            ItemStack it = m.getInventory().getItem(i);
            if (it != null && !it.getType().isAir()) {
                m.getInventory().setItem(i, null);
                s.deliveries.giveOrMail(p, it, "ankauf_zurueck");
                any = true;
            }
        }
        if (any) {
            s.msg.send(p, "buyback.returned");
        }
    }
}
