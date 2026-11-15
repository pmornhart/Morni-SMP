package de.moinmorni.smp.market.ui;

import de.moinmorni.smp.market.Category;
import de.moinmorni.smp.market.ItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/**
 * Brücke zwischen echten Gegenständen ({@link ItemStack}) und dem, was der Markt speichert ({@link ItemData}). Die
 * Preis-Sorte unterscheidet Verzauberungen und Tränke („minecraft:enchanted_book|sharpness=5“), nicht aber Namen
 * oder Abnutzung.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static ItemData of(ItemStack stack) {
        ItemStack one = stack.asOne();
        String key = key(stack);
        String name = "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        String search = (key.substring(key.indexOf(':') + 1).replace('_', ' ') + " " + name).strip();
        Category cat = Category.of(key, stack.getType().isBlock(), stack.getType().isEdible());
        return new ItemData(one.serializeAsBytes(), key, search, cat.id(), stack.getAmount());
    }

    /** Preis-Sorte eines Gegenstands. */
    public static String key(ItemStack stack) {
        StringBuilder sb = new StringBuilder(stack.getType().getKey().toString());
        ItemMeta meta = stack.getItemMeta();
        Map<String, Integer> ench = new TreeMap<>();
        if (meta instanceof EnchantmentStorageMeta book) {
            book.getStoredEnchants().forEach((e, lvl) -> ench.put(name(e), lvl));
        }
        if (meta != null) {
            meta.getEnchants().forEach((e, lvl) -> ench.put(name(e), lvl));
        }
        if (!ench.isEmpty()) {
            sb.append('|');
            List<String> parts = new ArrayList<>();
            ench.forEach((k, v) -> parts.add(k + "=" + v));
            sb.append(String.join(",", parts));
        }
        if (meta instanceof PotionMeta pm) {
            PotionType type = pm.getBasePotionType();
            if (type != null) {
                sb.append("|potion=").append(type.getKey().getKey());
            }
        }
        return sb.toString();
    }

    private static String name(Enchantment e) {
        return e.getKey().getKey();
    }

    /** Gegenstand zum Anzeigen (höchstens ein Stapel – zum Ausgeben {@link #stacks} nehmen). */
    public static ItemStack stack(ItemData d) {
        ItemStack base = ItemStack.deserializeBytes(d.blob());
        return base.asQuantity(Math.max(1, Math.min(d.amount(), base.getMaxStackSize())));
    }

    /** Eine Vorlage (Anzahl 1). */
    public static ItemStack one(ItemData d) {
        return ItemStack.deserializeBytes(d.blob()).asOne();
    }

    /** In Stapel aufgeteilt. */
    public static List<ItemStack> stacks(ItemData d) {
        ItemStack base = ItemStack.deserializeBytes(d.blob()).asOne();
        int max = Math.max(1, base.getMaxStackSize());
        List<ItemStack> out = new ArrayList<>();
        int left = d.amount();
        while (left > 0) {
            int n = Math.min(max, left);
            out.add(base.asQuantity(n));
            left -= n;
        }
        return out;
    }

    /** Name für Chat und Menüs (eigener Name oder der übersetzbare Name des Spiels). */
    public static Component name(ItemStack s) {
        return s.effectiveName();
    }

    /** Zählt passende Gegenstände (gleiche Sorte, gleiche Eigenschaften) im Hauptinventar. */
    public static int count(Inventory inv, ItemStack template) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s != null && s.isSimilar(template)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    /** Nimmt bis zu {@code amount} passende Gegenstände heraus; liefert, wie viele es waren. */
    public static int remove(Inventory inv, ItemStack template, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s != null && s.isSimilar(template)) {
                int take = Math.min(left, s.getAmount());
                left -= take;
                if (take == s.getAmount()) {
                    contents[i] = null;
                } else {
                    s.setAmount(s.getAmount() - take);
                }
            }
        }
        inv.setStorageContents(contents);
        return amount - left;
    }

    /** Wie viele Stück dieser Sorte passen noch hinein? */
    public static int room(Inventory inv, ItemStack template) {
        int max = template.getMaxStackSize();
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) {
                n += max;
            } else if (s.isSimilar(template)) {
                n += Math.max(0, max - s.getAmount());
            }
        }
        return n;
    }
}
