package net.morni.smp.ui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Knöpfe für Menüs. Namen und Zeilen kommen fertig übersetzt herein. */
public final class Items {

    private Items() {
    }

    /** Knopf mit Namen und Beschreibung (Zeilen ohne Kursiv). */
    public static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack s = new ItemStack(m);
        s.editMeta(meta -> {
            meta.displayName(plain(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> l = new ArrayList<>();
                for (Component c : lore) {
                    l.add(plain(c));
                }
                meta.lore(l);
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return s;
    }

    public static ItemStack button(Material m, Component name) {
        return button(m, name, List.of());
    }

    /** Ein echter Gegenstand (aus dem Markt) mit zusätzlichen Zeilen unten dran. */
    public static ItemStack withLore(ItemStack item, List<Component> extra) {
        ItemStack s = item.clone();
        s.editMeta(meta -> {
            List<Component> l = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!l.isEmpty()) {
                l.add(Component.empty());
            }
            for (Component c : extra) {
                l.add(plain(c));
            }
            meta.lore(l);
        });
        return s;
    }

    /** Leerer Platzhalter (Glasscheibe ohne Namen). */
    public static ItemStack filler(Material pane) {
        ItemStack s = new ItemStack(pane);
        s.editMeta(meta -> {
            meta.displayName(Component.text(" "));
            meta.addItemFlags(ItemFlag.values());
        });
        return s;
    }

    public static ItemStack head(OfflinePlayer owner, Component name, List<Component> lore) {
        ItemStack s = button(Material.PLAYER_HEAD, name, lore);
        s.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(owner));
        return s;
    }

    /** Text ohne das Kursiv, das Minecraft Namen und Zeilen sonst gibt. */
    public static Component plain(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
