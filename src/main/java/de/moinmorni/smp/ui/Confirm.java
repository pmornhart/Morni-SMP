package de.moinmorni.smp.ui;

import de.moinmorni.smp.text.Msg;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** „Wirklich?“ – links Ja (grün), rechts Nein (rot), in der Mitte, worum es geht. */
public final class Confirm {

    private final Msg msg;

    public Confirm(Msg msg) {
        this.msg = msg;
    }

    public void open(Player p, Component title, ItemStack shown, List<Component> details, Component yes,
                     Runnable onYes, Runnable onNo) {
        String lang = msg.lang(p);
        Menu m = new Menu(3, title);
        ItemStack filler = Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            m.show(i, filler);
        }
        m.show(13, details == null || details.isEmpty() ? shown : Items.withLore(shown, details));
        for (int slot : new int[] {10, 11}) {
            m.set(slot, Items.button(Material.LIME_CONCRETE, yes), pl -> {
                pl.closeInventory();
                onYes.run();
            });
        }
        for (int slot : new int[] {15, 16}) {
            m.set(slot, Items.button(Material.RED_CONCRETE, msg.text(lang, "confirm.no")), pl -> {
                if (onNo != null) {
                    onNo.run();
                } else {
                    pl.closeInventory();
                }
            });
        }
        m.open(p);
    }
}
