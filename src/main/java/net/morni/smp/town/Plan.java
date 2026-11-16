package net.morni.smp.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bauplan aus Daten: Block-Zustände (wie im Spiel geschrieben, z. B. {@code spruce_stairs[facing=north]}) an
 * Koordinaten relativ zur Stadtmitte; {@code y = 0} ist die Bodenschicht des Plateaus. Dazu Schilder und Banner, die
 * nach den Blöcken gesetzt werden. Der Plan kennt keinen Server – so lässt er sich testen.
 */
public final class Plan {

    /** Schild: Zeilen als MiniMessage. {@code wall}: an der Wand ({@code facing}), sonst stehend ({@code rotation}). */
    public record Sign(int x, int y, int z, String block, List<String> front, List<String> back) {
    }

    /** Banner: Grundfarbe im Block, Muster als (Farbe, Muster-Schlüssel). */
    public record Banner(int x, int y, int z, String block, List<String[]> patterns) {
    }

    private final Map<Long, String> blocks = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();

    public static long pack(int x, int y, int z) {
        return ((long) (x + 2048) << 32) | ((long) (y + 2048) << 16) | (z + 2048);
    }

    public static int x(long p) {
        return (int) (p >>> 32) - 2048;
    }

    public static int y(long p) {
        return (int) ((p >>> 16) & 0xFFFF) - 2048;
    }

    public static int z(long p) {
        return (int) (p & 0xFFFF) - 2048;
    }

    public void set(int x, int y, int z, String data) {
        blocks.put(pack(x, y, z), data);
    }

    public String get(int x, int y, int z) {
        return blocks.get(pack(x, y, z));
    }

    /** Name ohne Zustand („spruce_stairs“) oder null. */
    public String name(int x, int y, int z) {
        String d = get(x, y, z);
        if (d == null) {
            return null;
        }
        int b = d.indexOf('[');
        return b < 0 ? d : d.substring(0, b);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String data) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    public void remove(int x, int y, int z) {
        blocks.remove(pack(x, y, z));
    }

    public void sign(int x, int y, int z, String block, List<String> front, List<String> back) {
        set(x, y, z, block);
        signs.add(new Sign(x, y, z, block, front, back));
    }

    public void banner(int x, int y, int z, String block, List<String[]> patterns) {
        set(x, y, z, block);
        banners.add(new Banner(x, y, z, block, patterns));
    }

    public Map<Long, String> blocks() {
        return blocks;
    }

    public List<Sign> signs() {
        return signs;
    }

    public List<Banner> banners() {
        return banners;
    }

    /** Einfache, immer gleiche Zufallszahl 0…n-1 für eine Stelle (Abwechslung im Pflaster, ohne Zufall beim Bauen). */
    public static int noise(int x, int z, int salt, int n) {
        long h = x * 73_856_093L ^ z * 19_349_663L ^ salt * 83_492_791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) Math.floorMod(h, n);
    }
}
