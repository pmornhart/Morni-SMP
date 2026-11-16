package net.morni.smp.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Zäune, Glasscheiben und Mauern verbinden sich im Spiel nur, wenn ein Nachbar sich ändert – beim Bauen ohne
 * Physik also nie. Dieser Durchgang setzt die Verbindungen gleich im Plan, aus den Nachbarn im Plan.
 */
final class Connect {

    private Connect() {
    }

    private static final String[] NOT_FULL = {"stairs", "slab", "fence", "pane", "bars", "_wall", "door", "sign",
            "torch", "lantern", "potted", "flower_pot", "carpet", "air", "water", "lava", "banner", "campfire", "chain",
            "button", "lever", "pressure_plate", "rail", "ladder", "vine", "short_grass", "tall_grass", "fern", "tulip",
            "poppy", "dandelion", "orchid", "allium", "daisy", "cornflower", "lily", "rose_bush", "peony", "lilac",
            "sapling", "bed", "chest", "lectern", "cauldron", "anvil", "bell", "candle", "head", "skull", "cake",
            "light", "end_rod", "lightning_rod", "azalea", "bush", "grindstone", "brewing_stand", "flower",
            "hopper", "scaffolding", "cobweb", "snow", "dead_bush"};

    static boolean full(String name) {
        if (name == null) {
            return false;
        }
        if (name.endsWith("_glass") || name.equals("glass")) {
            return true;
        }
        for (String s : NOT_FULL) {
            if (name.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static String family(String name) {
        if (name == null) {
            return null;
        }
        if (name.endsWith("_fence")) {
            return "fence";
        }
        if (name.endsWith("glass_pane") || name.equals("iron_bars")) {
            return "pane";
        }
        if (name.endsWith("_wall") && !name.contains("sign") && !name.contains("torch") && !name.contains("banner")
                && !name.contains("head") && !name.contains("skull") && !name.contains("fan")) {
            return "wall";
        }
        return null;
    }

    static void run(Plan plan) {
        List<Long> todo = new ArrayList<>();
        for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
            String v = e.getValue();
            String n = v.indexOf('[') < 0 ? v : v.substring(0, v.indexOf('['));
            if (family(n) != null) {
                todo.add(e.getKey());
            }
        }
        for (long p : todo) {
            int x = Plan.x(p);
            int y = Plan.y(p);
            int z = Plan.z(p);
            String name = plan.name(x, y, z);
            String fam = family(name);
            boolean n = links(plan, fam, x, y, z - 1);
            boolean e = links(plan, fam, x + 1, y, z);
            boolean s = links(plan, fam, x, y, z + 1);
            boolean w = links(plan, fam, x - 1, y, z);
            String state;
            if (fam.equals("wall")) {
                boolean straight = (n && s && !e && !w) || (e && w && !n && !s);
                boolean up = !straight || plan.name(x, y + 1, z) != null;
                state = "[up=" + up + ",north=" + (n ? "low" : "none") + ",east=" + (e ? "low" : "none") + ",south="
                        + (s ? "low" : "none") + ",west=" + (w ? "low" : "none") + "]";
            } else {
                state = "[north=" + n + ",east=" + e + ",south=" + s + ",west=" + w + "]";
            }
            plan.set(x, y, z, name + state);
        }
    }

    private static boolean links(Plan plan, String fam, int x, int y, int z) {
        String other = plan.name(x, y, z);
        if (other == null) {
            return false;
        }
        String of = family(other);
        if (fam.equals(of)) {
            return true;
        }
        if (fam.equals("fence") && other.endsWith("_fence_gate")) {
            return true;
        }
        return full(other);
    }
}
