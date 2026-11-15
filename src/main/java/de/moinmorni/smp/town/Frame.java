package de.moinmorni.smp.town;

/**
 * Ein gedrehtes Koordinatensystem für ein Gebäude: {@code u} läuft die Vorderseite entlang (von außen gesehen nach
 * rechts), {@code v} ins Gebäude hinein; {@code v = 0} ist die Vorderwand. So wird jedes Haus einmal beschrieben und
 * in jede Richtung gedreht.
 */
public final class Frame {

    /** Himmelsrichtungen (Minecraft-Namen). */
    public enum Dir {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dz;

        Dir(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Dir opposite() {
            return values()[(ordinal() + 2) % 4];
        }

        public Dir right() {
            return values()[(ordinal() + 1) % 4];
        }

        public Dir left() {
            return values()[(ordinal() + 3) % 4];
        }

        /** Drehung eines stehenden Schilds/Banners (0 = Süden, 4 = Westen, 8 = Norden, 12 = Osten), Schrift nach außen. */
        public int rotation() {
            return switch (this) {
                case SOUTH -> 0;
                case WEST -> 4;
                case NORTH -> 8;
                case EAST -> 12;
            };
        }

        /** Achse für Stämme, die in diese Richtung laufen. */
        public String axis() {
            return dx != 0 ? "x" : "z";
        }
    }

    final Plan plan;
    final int ox;
    final int oz;
    /** Vorderseite zeigt nach außen in diese Richtung. */
    public final Dir front;
    /** Richtung von u (von außen gesehen nach rechts). */
    public final Dir right;

    public Frame(Plan plan, int ox, int oz, Dir front) {
        this.plan = plan;
        this.ox = ox;
        this.oz = oz;
        this.front = front;
        // von außen auf die Vorderseite geschaut, liegt „rechts“ eine Vierteldrehung gegen den Uhrzeigersinn von vorne
        this.right = front.left();
    }

    /** Welt-x zu (u, v). */
    public int x(int u, int v) {
        return ox + u * right.dx - v * front.dx;
    }

    public int z(int u, int v) {
        return oz + u * right.dz - v * front.dz;
    }

    public void set(int u, int y, int v, String data) {
        plan.set(x(u, v), y, z(u, v), data);
    }

    public String get(int u, int y, int v) {
        return plan.get(x(u, v), y, z(u, v));
    }

    public void fill(int u1, int y1, int v1, int u2, int y2, int v2, String data) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
            for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    set(u, y, v, data);
                }
            }
        }
    }

    /** Richtung „nach vorne/hinten/rechts/links“ im Gebäude → Welt-Richtung. */
    public String f() {
        return front.id();
    }

    public String b() {
        return front.opposite().id();
    }

    public String r() {
        return right.id();
    }

    public String l() {
        return right.opposite().id();
    }

    /** Achse entlang der Vorderseite (u) bzw. in die Tiefe (v). */
    public String axisU() {
        return right.axis();
    }

    public String axisV() {
        return front.axis();
    }

    public void sign(int u, int y, int v, String wood, java.util.List<String> front, java.util.List<String> back) {
        plan.sign(x(u, v), y, z(u, v), wood + "_wall_sign[facing=" + f() + "]", front, back);
    }
}
