package de.moinmorni.smp.town;

import java.util.List;
import java.util.Map;

/**
 * Was das Plugin zur Laufzeit über die Stadt wissen muss – alles relativ zur Stadtmitte, {@code y = 0} ist die
 * Bodenschicht des Plateaus.
 */
public record Layout(
        /* Ankunft: Füße auf dieser Höhe, Blick nach Norden */
        double spawnX, double spawnY, double spawnZ, float spawnYaw,
        /* geschützte Fläche (Chebyshev-Abstand) und flaches Plateau */
        int area, int flat,
        List<StallSpot> stalls,
        /* Block → was ein Rechtsklick öffnet: „ah“, „orders“, „buyback“, „mail“, „stall“ */
        Map<Long, String> triggers,
        /* die drei Reisetore (Oberwelt, Nether, End) */
        List<Gate> gates,
        /* Bestenliste (Mitte unten der Schrift) */
        double boardX, double boardY, double boardZ) {

    /** Kasten mit Ecken (einschließlich). */
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public static Box of(int ax, int ay, int az, int bx, int by, int bz) {
            return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by),
                    Math.max(az, bz));
        }

        public boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        public boolean containsXZ(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }
    }

    /**
     * Ein Reisetor. {@code target}: „overworld“, „nether“ oder „end“. {@code box}: Füße darin → Zufalls-Teleport in diese
     * Dimension. {@code back*}: wohin man (in die Stadt) zurückgeschoben wird, bis der Platz gefunden ist.
     * {@code membrane}: die Öffnung, in der das schimmernde Tor-Bild steht ({@code axis}: „x“ = Fläche entlang x).
     * {@code label*}: Mitte der großen Beschriftung über dem Bogen (Stadtseite).
     */
    public record Gate(String target, Box box, double backX, double backZ, float backYaw, Box membrane, String axis,
                       double labelX, double labelY, double labelZ) {
    }

    /**
     * Ein Marktstand. {@code zone}: hier darf der Mieter Kisten, Fässer und Schilder aufstellen. {@code front}: wer hier
     * steht, kann ihn mieten. {@code sign}: Hängeschild mit Nummer und Mieter.
     */
    public record StallSpot(int id, Box zone, Box front, int signX, int signY, int signZ) {
    }
}
