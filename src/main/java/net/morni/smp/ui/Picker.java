package net.morni.smp.ui;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import net.kyori.adventure.text.Component;
import net.morni.smp.economy.Money;
import net.morni.smp.text.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Zahl wählen (Preis oder Menge) mit Plus-/Minus-Knöpfen – oder eintippen. Für Geld rechnet der Picker in
 * Hundertsteln, zeigt aber Taler.
 */
public final class Picker {

    /** Was gewählt wird. */
    public enum Kind {
        MONEY, AMOUNT
    }

    private final Msg msg;
    private final ChatPrompt prompt;

    public Picker(Msg msg, ChatPrompt prompt) {
        this.msg = msg;
        this.prompt = prompt;
    }

    /**
     * @param title    Überschrift
     * @param shown    der Gegenstand (oder ein Knopf), um den es geht
     * @param describe Zeilen unter der Zahl (z. B. Marktpreis), bekommt den aktuellen Wert
     * @param done     Bestätigt mit diesem Wert
     */
    public void open(Player p, Component title, ItemStack shown, Kind kind, long start, long min, long max,
                     LongFunction<List<Component>> describe, LongConsumer done, Runnable back) {
        String lang = msg.lang(p);
        Menu m = new Menu(4, title);
        long[] value = {Math.max(min, Math.min(max, start))};
        long[] steps = kind == Kind.MONEY ? new long[] {100, 1_000, 10_000} : new long[] {1, 8, 64};
        Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            for (int i = 0; i < 3; i++) {
                long step = steps[i];
                String label = kind == Kind.MONEY ? Money.format(step, lang) : Long.toString(step);
                m.set(2 - i, Items.button(Material.RED_STAINED_GLASS_PANE,
                        msg.text(lang, "picker.minus", Msg.t("step", label))), (pl, click) -> {
                    value[0] = Math.max(min, value[0] - step);
                    pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
                    draw[0].run();
                });
                m.set(6 + i, Items.button(Material.LIME_STAINED_GLASS_PANE,
                        msg.text(lang, "picker.plus", Msg.t("step", label))), (pl, click) -> {
                    value[0] = Math.min(max, value[0] + step);
                    pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    draw[0].run();
                });
            }
            String shownValue = kind == Kind.MONEY ? Money.format(value[0], lang) : value[0] + "×";
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(msg.text(lang, "picker.value", Msg.t("value", shownValue)));
            lines.addAll(describe == null ? List.of() : describe.apply(value[0]));
            m.show(4, Items.withLore(shown, lines));
            m.set(13, Items.button(Material.OAK_SIGN, msg.text(lang, "picker.type"),
                    List.of(msg.text(lang, "picker.type.lore"))), pl -> {
                pl.closeInventory();
                msg.send(pl, kind == Kind.MONEY ? "picker.ask.money" : "picker.ask.amount");
                prompt.ask(pl, text -> {
                    long v = kind == Kind.MONEY ? Money.parse(text) : parseAmount(text);
                    if (v < min || v > max) {
                        msg.send(pl, "picker.invalid");
                    } else {
                        value[0] = v;
                    }
                    draw[0].run();
                    m.open(pl);
                }, () -> m.open(pl));
            });
            m.set(31, Items.button(Material.EMERALD_BLOCK, msg.text(lang, "picker.ok",
                    Msg.t("value", shownValue))), pl -> done.accept(value[0]));
            if (back != null) {
                m.set(27, Items.button(Material.ARROW, msg.text(lang, "menu.back")), pl -> back.run());
            }
            m.set(35, Items.button(Material.BARRIER, msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        };
        draw[0].run();
        m.open(p);
    }

    static long parseAmount(String s) {
        try {
            long v = Long.parseLong(s.strip().replace(".", "").replace(",", ""));
            return v <= 0 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
