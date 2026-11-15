package de.moinmorni.smp.net;

import de.moinmorni.smp.db.Database;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meldet Werte an die Netzwerk-Statistik ({@code stats("mirni", …)}): Verkäufe, Einkäufe, erfüllte Aufträge und
 * Spielminuten werden gesammelt und alle 30 Sekunden hochgezählt. Der Kontostand ({@code balance}) wird „gesetzt“:
 * die Schnittstelle kann nur hochzählen, darum geht nur die Änderung seit der letzten Meldung raus (Spalte
 * {@code accounts.reported}) – so steht im Netzwerk immer der echte Kontostand.
 *
 * <p>Ohne Netzwerk oder ohne Hub bleibt alles liegen und geht beim nächsten Mal mit.
 */
public final class Reporter {

    private static final int SALES = 0;
    private static final int PURCHASES = 1;
    private static final int ORDERS = 2;
    private static final int PLAYTIME = 3;
    private static final String[] KEYS = {"sales", "purchases", "orders_filled", "playtime_min"};
    private static final int MAX_BALANCES = 500;

    private final Network network;
    private final Database db;
    private final Map<UUID, long[]> pending = new ConcurrentHashMap<>();

    public Reporter(Network network, Database db) {
        this.network = network;
        this.db = db;
    }

    public void sale(UUID uuid) {
        bump(uuid, SALES, 1);
    }

    public void purchase(UUID uuid) {
        bump(uuid, PURCHASES, 1);
    }

    public void orderFilled(UUID uuid) {
        bump(uuid, ORDERS, 1);
    }

    public void playtime(UUID uuid, int minutes) {
        bump(uuid, PLAYTIME, minutes);
    }

    private void bump(UUID uuid, int index, long by) {
        if (uuid == null || by <= 0) {
            return;
        }
        pending.compute(uuid, (k, v) -> {
            long[] a = v == null ? new long[KEYS.length] : v;
            a[index] += by;
            return a;
        });
    }

    /** Alles Gesammelte melden (Haupt-Thread). Liefert die Zahl der gemeldeten Spieler. */
    public int flush() {
        if (!network.connected() || !network.hubOnline()) {
            return 0;
        }
        Map<UUID, Map<String, Long>> out = new HashMap<>();
        for (UUID uuid : List.copyOf(pending.keySet())) {
            long[] a = pending.remove(uuid);
            if (a == null) {
                continue;
            }
            Map<String, Long> add = out.computeIfAbsent(uuid, k -> new HashMap<>());
            for (int i = 0; i < KEYS.length; i++) {
                if (a[i] != 0) {
                    add.put(KEYS[i], a[i]);
                }
            }
        }
        // Kontostände: nur die Differenz seit der letzten Meldung (als „hochzählen“, auch negativ)
        List<Object[]> changed = db.tx(c -> {
            List<Object[]> rows = Database.query(c, "SELECT uuid, balance, reported FROM accounts "
                    + "WHERE balance <> reported LIMIT ?", rs -> new Object[] {UUID.fromString(rs.getString(1)),
                    rs.getLong(2), rs.getLong(3)}, MAX_BALANCES);
            for (Object[] r : rows) {
                Database.update(c, "UPDATE accounts SET reported = ? WHERE uuid = ?", r[1], r[0]);
            }
            return rows;
        });
        for (Object[] r : changed) {
            long delta = (Long) r[1] - (Long) r[2];
            out.computeIfAbsent((UUID) r[0], k -> new HashMap<>()).put("balance", delta);
        }
        out.forEach((uuid, add) -> {
            if (!add.isEmpty()) {
                network.stats(uuid, add, Map.of());
            }
        });
        return out.size();
    }
}
