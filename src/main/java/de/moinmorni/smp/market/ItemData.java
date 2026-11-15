package de.moinmorni.smp.market;

import java.util.Arrays;
import java.util.Objects;

/**
 * Ein Gegenstand, wie ihn der Markt speichert: ein einzelnes Stück im Paper-Format ({@code blob}, immer Anzahl 1),
 * dazu die Anzahl. {@code key} ist die Preis-Sorte (z. B. „minecraft:diamond“ oder „minecraft:enchanted_book|sharpness=5“),
 * {@code name} ein lesbarer Name zum Suchen (eigener Name des Gegenstands oder die Sorte), {@code category} die Abteilung.
 *
 * <p>Der Markt selbst schaut nie in den Inhalt – so lässt er sich ohne Server testen.
 */
public record ItemData(byte[] blob, String key, String name, String category, int amount) {

    public ItemData {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(key, "key");
        name = name == null ? "" : name;
        category = category == null ? Category.OTHER.id() : category;
        if (amount <= 0) {
            throw new IllegalArgumentException("Anzahl muss > 0 sein");
        }
    }

    /** Gleiche Sache, andere Anzahl. */
    public ItemData withAmount(int n) {
        return new ItemData(blob, key, name, category, n);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemData d && Arrays.equals(blob, d.blob) && key.equals(d.key) && name.equals(d.name)
                && category.equals(d.category) && amount == d.amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(blob), key, name, category, amount);
    }

    @Override
    public String toString() {
        return amount + "× " + key;
    }
}
