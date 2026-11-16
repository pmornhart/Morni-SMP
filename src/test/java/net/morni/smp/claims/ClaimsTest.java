package net.morni.smp.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.morni.smp.TestClock;
import net.morni.smp.claims.ClaimRules.Zone;
import net.morni.smp.db.Database;
import net.morni.smp.economy.Bank;
import org.junit.jupiter.api.Test;

class ClaimsTest {

    @Test
    void firstFourFreeThenPaidUpToMax() {
        try (Database db = Database.memory()) {
            TestClock clock = new TestClock();
            Bank bank = new Bank(db, clock, 1_000);
            Claims claims = new Claims(db, bank, clock);
            UUID a = UUID.randomUUID();
            bank.ensure(a, "Anna");
            for (int i = 0; i < 4; i++) {
                Claims.ClaimResult r = claims.claim("world", i, 0, a, "Anna", 4, 600, 6);
                assertEquals(Claims.Status.OK, r.status());
                assertEquals(0, r.paid());
            }
            assertEquals(Claims.Status.OK, claims.claim("world", 4, 0, a, "Anna", 4, 600, 6).status());
            assertEquals(400, bank.balance(a));
            assertEquals(Claims.Status.NOT_ENOUGH, claims.claim("world", 5, 0, a, "Anna", 4, 600, 6).status());
            assertNull(claims.at("world", 5, 0), "nichts halb angelegt");
            bank.deposit(a, 10_000, "test", null);
            assertEquals(Claims.Status.OK, claims.claim("world", 5, 0, a, "Anna", 4, 600, 6).status());
            assertEquals(Claims.Status.LIMIT, claims.claim("world", 6, 0, a, "Anna", 4, 600, 6).status());
            assertEquals(Claims.Status.OWN, claims.claim("world", 0, 0, a, "Anna", 4, 600, 6).status());
            UUID b = UUID.randomUUID();
            assertEquals(Claims.Status.TAKEN, claims.claim("world", 0, 0, b, "Bert", 4, 600, 6).status());
            assertEquals(6, claims.count(a));

            assertFalse(claims.unclaim("world", 0, 0, b, false));
            assertTrue(claims.unclaim("world", 0, 0, a, false));
            assertEquals(5, claims.count(a));
            // nach dem Neuladen ist alles noch da
            Claims again = new Claims(db, bank, clock);
            assertEquals(5, again.count(a));
        }
    }

    @Test
    void trustAndPvpFlag() {
        try (Database db = Database.memory()) {
            TestClock clock = new TestClock();
            Bank bank = new Bank(db, clock, 0);
            Claims claims = new Claims(db, bank, clock);
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            claims.claim("world", 10, 10, a, "Anna", 4, 0, 10);
            assertTrue(claims.trusted(a, a));
            assertFalse(claims.trusted(a, b));
            claims.trust(a, b, "Bert");
            assertTrue(claims.trusted(a, b));
            assertEquals(1, claims.trustedNames(a).size());
            assertTrue(new Claims(db, bank, clock).trusted(a, b), "Vertrauen bleibt gespeichert");
            assertTrue(claims.untrust(a, b));
            assertFalse(claims.trusted(a, b));
            assertFalse(claims.pvp("world", 10, 10, b, true), "nur der Besitzer");
            assertTrue(claims.pvp("world", 10, 10, a, true));
            assertTrue(claims.at("world", 10, 10).pvp());
        }
    }

    @Test
    void protectionRules() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // bauen
        assertTrue(ClaimRules.mayUse(null, false, false), "Wildnis: jeder");
        assertFalse(ClaimRules.mayUse(a, false, false), "fremdes Grundstück: nein");
        assertTrue(ClaimRules.mayUse(a, true, false), "vertraut: ja");
        assertTrue(ClaimRules.mayUse(a, false, true), "Admin-Modus: ja");
        // Wasser, Lava, Kolben über Grenzen
        assertTrue(ClaimRules.crossAllowed(a, a));
        assertTrue(ClaimRules.crossAllowed(a, null), "ins Freie darf es");
        assertFalse(ClaimRules.crossAllowed(null, a), "von außen hinein nicht");
        assertFalse(ClaimRules.crossAllowed(b, a), "vom Nachbarn hinein nicht");
        // Feuer, Explosionen
        assertTrue(ClaimRules.fireSpreads(null));
        assertFalse(ClaimRules.fireSpreads(a));
        assertTrue(ClaimRules.explosionBreaks(null, false));
        assertFalse(ClaimRules.explosionBreaks(a, false));
        assertFalse(ClaimRules.explosionBreaks(null, true), "nicht in der Stadt");
        // PvP
        assertTrue(ClaimRules.pvp(Zone.WILDERNESS, Zone.WILDERNESS));
        assertFalse(ClaimRules.pvp(Zone.WILDERNESS, Zone.TOWN));
        assertFalse(ClaimRules.pvp(Zone.TOWN, Zone.WILDERNESS), "aus der Stadt heraus auch nicht");
        assertFalse(ClaimRules.pvp(Zone.WILDERNESS, Zone.CLAIM_PEACEFUL));
        assertTrue(ClaimRules.pvp(Zone.CLAIM_PVP, Zone.WILDERNESS));
        assertEquals(3, ClaimRules.distance(0, 0, -3, 2));
    }
}
