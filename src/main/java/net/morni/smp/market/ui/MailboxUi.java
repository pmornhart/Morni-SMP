package net.morni.smp.market.ui;

import java.util.List;
import net.morni.smp.Smp;
import net.morni.smp.market.Mailbox;
import net.morni.smp.text.Msg;
import net.morni.smp.ui.Items;
import net.morni.smp.ui.Menu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Das Abholfach als Menü: anklicken = ins Inventar, unten „Alles abholen“. */
public final class MailboxUi {

    private static final int PER_PAGE = 45;

    private final Smp s;

    public MailboxUi(Smp s) {
        this.s = s;
    }

    public void open(Player p, int page) {
        String lang = s.msg.lang(p);
        List<Mailbox.Mail> all = s.mailbox.list(p.getUniqueId(), 1_000);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.min(Math.max(0, page), pages - 1);
        Menu m = new Menu(6, s.msg.text(lang, "mail.title", Msg.t("count", all.size())));
        List<Mailbox.Mail> shown = all.subList(pg * PER_PAGE, Math.min(all.size(), (pg + 1) * PER_PAGE));
        for (int i = 0; i < shown.size(); i++) {
            Mailbox.Mail mail = shown.get(i);
            m.set(i, Items.withLore(ItemCodec.stack(mail.item()), List.of(
                    s.msg.text(lang, "mail.line.amount", Msg.t("amount", mail.item().amount())),
                    s.msg.text(lang, "mail.reason." + mail.reason()),
                    s.msg.text(lang, "mail.line.take"))), pl -> {
                        if (s.deliveries.take(pl, mail.id())) {
                            pl.playSound(pl.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
                        } else {
                            s.msg.send(pl, "mail.full");
                        }
                        open(pl, pg);
                    });
        }
        if (all.isEmpty()) {
            m.show(22, Items.button(Material.PAPER, s.msg.text(lang, "mail.empty")));
        }
        if (pg > 0) {
            m.set(45, Items.button(Material.ARROW, s.msg.text(lang, "menu.prev")), pl -> open(pl, pg - 1));
        }
        if (pg + 1 < pages) {
            m.set(53, Items.button(Material.ARROW, s.msg.text(lang, "menu.next")), pl -> open(pl, pg + 1));
        }
        m.set(49, Items.button(Material.HOPPER, s.msg.text(lang, "mail.take-all"),
                List.of(s.msg.text(lang, "mail.take-all.lore"))), pl -> {
                    int[] r = s.deliveries.takeAll(pl);
                    s.msg.send(pl, r[1] > 0 ? "mail.taken-some" : "mail.taken-all", Msg.t("count", r[0]),
                            Msg.t("left", r[1]));
                    open(pl, 0);
                });
        m.set(51, Items.button(Material.GOLD_INGOT, s.msg.text(lang, "ah.button")),
                pl -> s.plugin.auctionUi().command(pl, new String[0]));
        m.set(52, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }
}
