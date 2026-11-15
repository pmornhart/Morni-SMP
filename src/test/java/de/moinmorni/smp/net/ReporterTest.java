package de.moinmorni.smp.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Die Netzwerk-Statistik bekommt Zähler und immer den echten Kontostand (als Differenz). */
class ReporterTest {

    /** Nimmt alle Meldungen auf und zählt sie wie der Hub zusammen. */
    static final class FakeNetwork implements Network {
        boolean hub = true;
        final List<Map<String, Long>> reports = new ArrayList<>();
        final Map<UUID, Map<String, Long>> totals = new HashMap<>();

        @Override
        public boolean connected() {
            return true;
        }

        @Override
        public String language(UUID player) {
            return "de";
        }

        @Override
        public boolean hasRight(UUID player, String right) {
            return false;
        }

        @Override
        public void stats(UUID player, Map<String, Long> add, Map<String, Long> best) {
            reports.add(add);
            add.forEach((k, v) -> totals.computeIfAbsent(player, x -> new HashMap<>()).merge(k, v, Long::sum));
        }

        @Override
        public void state(String state, int players) {
        }

        @Override
        public void toLobby(Player player, Component message) {
        }

        @Override
        public boolean hubOnline() {
            return hub;
        }
    }

    @Test
    void countersAndBalanceArriveExactlyOnce() {
        Database db = Database.memory();
        Bank bank = new Bank(db, new TestClock(), 10_000);
        FakeNetwork net = new FakeNetwork();
        Reporter r = new Reporter(net, db);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        bank.ensure(a, "Anna");
        bank.ensure(b, "Ben");
        r.sale(a);
        r.sale(a);
        r.purchase(b);
        r.orderFilled(a);
        r.playtime(b, 3);

        net.hub = false;
        assertEquals(0, r.flush(), "ohne Hub bleibt alles liegen");
        net.hub = true;
        r.flush();
        assertEquals(2L, net.totals.get(a).get("sales"));
        assertEquals(1L, net.totals.get(a).get("orders_filled"));
        assertEquals(1L, net.totals.get(b).get("purchases"));
        assertEquals(3L, net.totals.get(b).get("playtime_min"));
        assertEquals(10_000L, net.totals.get(a).get("balance"));
        assertEquals(10_000L, net.totals.get(b).get("balance"));

        // Geld wandert: im Netzwerk steht danach wieder genau der Kontostand
        bank.pay(a, b, 2_550, "test", null);
        int before = net.reports.size();
        r.flush();
        assertEquals(7_450L, net.totals.get(a).get("balance"));
        assertEquals(12_550L, net.totals.get(b).get("balance"));
        assertEquals(2L, net.totals.get(a).get("sales"), "Zähler werden nicht doppelt gemeldet");
        assertTrue(net.reports.size() > before);

        // nichts Neues → keine Meldung
        before = net.reports.size();
        r.flush();
        assertEquals(before, net.reports.size());
    }
}
