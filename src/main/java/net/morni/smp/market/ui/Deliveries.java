package net.morni.smp.market.ui;

import java.util.List;
import java.util.Map;
import net.morni.smp.market.ItemData;
import net.morni.smp.market.Mailbox;
import net.morni.smp.text.Msg;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Gegenstände aus dem Abholfach ins Inventar: Zeile erst aus der Datenbank nehmen, dann geben; was nicht passt,
 * kommt sofort wieder ins Abholfach. So entsteht nie etwas doppelt.
 */
public final class Deliveries {

    private final Mailbox mailbox;
    private final Msg msg;

    public Deliveries(Mailbox mailbox, Msg msg) {
        this.mailbox = mailbox;
        this.msg = msg;
    }

    public Mailbox mailbox() {
        return mailbox;
    }

    /** Einen Eintrag abholen. Liefert true, wenn er ganz ins Inventar gepasst hat. */
    public boolean take(Player p, long id) {
        Mailbox.Mail m = mailbox.take(id, p.getUniqueId());
        if (m == null) {
            return false;
        }
        int left = give(p, m.item());
        if (left > 0) {
            mailbox.add(p.getUniqueId(), m.item().withAmount(left), m.reason());
            return false;
        }
        return true;
    }

    /** Alles abholen, solange Platz ist. Liefert {abgeholt, noch im Fach}. */
    public int[] takeAll(Player p) {
        int taken = 0;
        List<Mailbox.Mail> list = mailbox.list(p.getUniqueId(), 200);
        for (Mailbox.Mail m : list) {
            if (!take(p, m.id())) {
                break;
            }
            taken++;
        }
        int left = mailbox.count(p.getUniqueId());
        if (taken > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
        }
        return new int[] {taken, left};
    }

    /** Gegenstände ins Inventar legen; liefert die Anzahl, die nicht gepasst hat. */
    public static int give(Player p, ItemData d) {
        int left = 0;
        for (ItemStack s : ItemCodec.stacks(d)) {
            Map<Integer, ItemStack> rest = p.getInventory().addItem(s);
            for (ItemStack r : rest.values()) {
                left += r.getAmount();
            }
        }
        return left;
    }

    /** Gegenstand geben, Rest ins Abholfach (mit Hinweis). */
    public void giveOrMail(Player p, ItemStack stack, String reason) {
        Map<Integer, ItemStack> rest = p.getInventory().addItem(stack);
        if (!rest.isEmpty()) {
            for (ItemStack r : rest.values()) {
                mailbox.add(p.getUniqueId(), ItemCodec.of(r), reason);
            }
            msg.send(p, "mail.overflow");
        }
    }
}
