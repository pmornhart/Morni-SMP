package net.morni.smp.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Ein Truhen-Menü: jeder Platz kann eine Aktion haben. Alles wird per einfachem Klick bedient – das klappt auch für
 * Bedrock-Spieler (Geyser zeigt Truhen als Truhen). Nichts lässt sich herausnehmen oder hineinlegen (außer in
 * Menüs mit freien Plätzen, siehe {@link #free}).
 */
public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
    /** Plätze, in die man selbst etwas legen darf (Ankauf, Verkaufen). */
    private final java.util.Set<Integer> free = new java.util.HashSet<>();
    private Consumer<Player> onClose;
    private Runnable onChange;

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public int size() {
        return inventory.getSize();
    }

    public void set(int slot, ItemStack item, Consumer<Player> action) {
        set(slot, item, action == null ? null : (p, click) -> action.accept(p));
    }

    public void set(int slot, ItemStack item, BiConsumer<Player, ClickType> action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    /** Nur zeigen, nichts tun. */
    public void show(int slot, ItemStack item) {
        set(slot, item, (BiConsumer<Player, ClickType>) null);
    }

    public void clear() {
        inventory.clear();
        actions.clear();
    }

    /** Freie Plätze: dort darf der Spieler eigene Gegenstände ablegen. */
    public void free(int... slots) {
        for (int s : slots) {
            free.add(s);
        }
    }

    public boolean isFree(int slot) {
        return free.contains(slot);
    }

    public java.util.Set<Integer> freeSlots() {
        return free;
    }

    public void onClose(Consumer<Player> c) {
        this.onClose = c;
    }

    /** Wird nach jeder Änderung an freien Plätzen aufgerufen (einen Tick später). */
    public void onChange(Runnable r) {
        this.onChange = r;
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    /** Klick: oben auf einen Knopf → Aktion. Freie Plätze und das eigene Inventar gehen nur in Menüs mit freien Plätzen. */
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        boolean top = e.getClickedInventory() == inventory;
        if (free.isEmpty()) {
            e.setCancelled(true);
        } else {
            boolean allowed;
            if (top) {
                allowed = free.contains(e.getSlot()) && e.getClick() != ClickType.DOUBLE_CLICK;
            } else {
                // eigenes Inventar: normal klicken geht, Umschalt-Klick legt in freie Plätze (das macht Bukkit selbst
                // nur in irgendeinen Platz – darum selbst verteilen)
                allowed = !e.isShiftClick() && e.getClick() != ClickType.DOUBLE_CLICK;
                if (e.isShiftClick()) {
                    shiftIntoFree(e);
                }
            }
            if (!allowed) {
                e.setCancelled(true);
            }
            if (allowed || e.isShiftClick()) {
                changed();
            }
        }
        if (top) {
            BiConsumer<Player, ClickType> action = actions.get(e.getSlot());
            if (action != null) {
                e.setCancelled(true);
                action.accept(p, e.getClick());
            }
        }
    }

    private void shiftIntoFree(InventoryClickEvent e) {
        ItemStack moving = e.getCurrentItem();
        if (moving == null || moving.getType().isAir()) {
            return;
        }
        int left = moving.getAmount();
        for (int slot : new java.util.TreeSet<>(free)) {
            ItemStack there = inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                inventory.setItem(slot, moving.asQuantity(left));
                left = 0;
            } else if (there.isSimilar(moving) && there.getAmount() < there.getMaxStackSize()) {
                int add = Math.min(left, there.getMaxStackSize() - there.getAmount());
                there.setAmount(there.getAmount() + add);
                left -= add;
            }
            if (left == 0) {
                break;
            }
        }
        if (left == 0) {
            e.setCurrentItem(null);
        } else {
            moving.setAmount(left);
            e.setCurrentItem(moving);
        }
    }

    public void drag(InventoryDragEvent e) {
        for (int raw : e.getRawSlots()) {
            if (raw < inventory.getSize() && !free.contains(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        if (!free.isEmpty()) {
            changed();
        }
    }

    private void changed() {
        if (onChange != null) {
            Bukkit.getScheduler().runTask(Menus.plugin(), onChange);
        }
    }

    /** Menü wurde geschlossen. */
    public void closed(Player p) {
        if (onClose != null) {
            onClose.accept(p);
        }
    }
}
