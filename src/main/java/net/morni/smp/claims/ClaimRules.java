package net.morni.smp.claims;

import java.util.Objects;
import java.util.UUID;

/**
 * Die Schutzregeln als reine Rechnung (ohne Server, darum gut zu testen). „Besitzer“ ist der Besitzer des betroffenen
 * Chunks; {@code null} heißt Wildnis.
 */
public final class ClaimRules {

    private ClaimRules() {
    }

    /** Wo jemand steht, wenn es um PvP geht. */
    public enum Zone {
        WILDERNESS, TOWN, CLAIM_PEACEFUL, CLAIM_PVP
    }

    /**
     * Bauen, abbauen, Kisten öffnen, Türen, Tiere, Rahmen …: in der Wildnis jeder; auf einem Grundstück nur Besitzer,
     * Vertraute und Admins (im Admin-Modus).
     */
    public static boolean mayUse(UUID owner, boolean actorTrusted, boolean adminBypass) {
        return owner == null || actorTrusted || adminBypass;
    }

    /**
     * Fließt Wasser/Lava (oder schiebt ein Kolben, wächst etwas) von einem Chunk in einen anderen? Erlaubt, wenn das
     * Ziel Wildnis ist oder beide demselben gehören. So kann niemand von außen in ein Grundstück hineinfluten.
     */
    public static boolean crossAllowed(UUID fromOwner, UUID toOwner) {
        return toOwner == null || Objects.equals(fromOwner, toOwner);
    }

    /** Feuer breitet sich auf Grundstücken nicht aus (auch nicht vom Besitzer gelegt – zu gefährlich für Holzhäuser). */
    public static boolean fireSpreads(UUID owner) {
        return owner == null;
    }

    /** Explosionen zerstören keine Blöcke auf Grundstücken (und in der Stadt). */
    public static boolean explosionBreaks(UUID owner, boolean town) {
        return owner == null && !town;
    }

    /** PvP: nur wenn beide an einem Ort stehen, wo es erlaubt ist (Wildnis oder Grundstück mit PvP an). */
    public static boolean pvp(Zone attacker, Zone victim) {
        return allows(attacker) && allows(victim);
    }

    private static boolean allows(Zone z) {
        return z == Zone.WILDERNESS || z == Zone.CLAIM_PVP;
    }

    /** Chunk-Abstand (Chebyshev) zwischen zwei Chunks. */
    public static int distance(int cx1, int cz1, int cx2, int cz2) {
        return Math.max(Math.abs(cx1 - cx2), Math.abs(cz1 - cz2));
    }
}
