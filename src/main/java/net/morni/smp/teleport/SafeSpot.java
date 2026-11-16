package net.morni.smp.teleport;

import java.util.random.RandomGenerator;

/**
 * Zufalls-Teleport: Wohin, und ist es dort sicher? Rein rechnerisch – was ein Block ist, sagt das Plugin.
 */
public final class SafeSpot {

    private SafeSpot() {
    }

    /** Wie der Boden ist. */
    public enum Ground {
        /** fester Block, auf dem man stehen kann */
        SOLID,
        /** Wasser, Lava */
        LIQUID,
        /** Laub (man fällt durch die Baumkrone oder landet oben drauf) */
        LEAVES,
        /** Magma, Kaktus, Feuer, Pulverschnee, Beerenbusch, Netz … */
        DANGEROUS,
        /** Luft oder etwas ohne Halt */
        NONE
    }

    /** Was an der Stelle zu finden war. */
    public record Column(Ground ground, int groundY, boolean feetFree, boolean headFree, boolean skyAbove) {
    }

    /** Ring für Nah/Mittel/Weit. */
    public record Band(int min, int max) {
        public Band {
            if (min < 0 || max <= min) {
                throw new IllegalArgumentException("Ring braucht 0 <= min < max");
            }
        }
    }

    /** Punkt (Blockkoordinaten). */
    public record Point(int x, int z) {
    }

    /**
     * Sicher heißt: fester Boden, zwei freie Blöcke darüber, nicht in einer Höhle ({@code requireSky}), Höhe im
     * erlaubten Bereich, kein Grundstück, nicht in der Stadt, innerhalb der Weltgrenze.
     */
    public static boolean ok(Column c, int minY, int maxY, boolean requireSky, boolean claimed, boolean town,
                             boolean insideBorder) {
        if (c == null || claimed || town || !insideBorder) {
            return false;
        }
        if (c.ground() != Ground.SOLID || !c.feetFree() || !c.headFree()) {
            return false;
        }
        if (requireSky && !c.skyAbove()) {
            return false;
        }
        return c.groundY() >= minY && c.groundY() + 2 <= maxY;
    }

    /**
     * Zufälliger Punkt im Ring um ({@code cx}, {@code cz}), gleichmäßig über die Fläche verteilt, und so, dass er
     * mindestens {@code margin} Blöcke innerhalb einer quadratischen Weltgrenze mit Radius {@code border} liegt.
     * Passt der Ring nicht in die Grenze, wird er auf sie gekürzt.
     */
    public static Point pick(RandomGenerator rng, Band band, int cx, int cz, int border, int margin) {
        int limit = Math.max(1, border - margin);
        int max = Math.min(band.max(), limit);
        int min = Math.min(band.min(), Math.max(0, max - 1));
        for (int i = 0; i < 64; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            // gleichmäßig auf der Ringfläche: Radius über die Wurzel
            double r = Math.sqrt(min * (double) min + rng.nextDouble() * (max * (double) max - min * (double) min));
            int x = cx + (int) Math.round(Math.cos(angle) * r);
            int z = cz + (int) Math.round(Math.sin(angle) * r);
            if (Math.abs(x) <= limit && Math.abs(z) <= limit) {
                return new Point(x, z);
            }
        }
        // Ecken knapp: Punkt auf die Grenze schieben
        int x = cx + Math.max(-limit, Math.min(limit, min + 1));
        return new Point(Math.max(-limit, Math.min(limit, x)), cz);
    }
}
