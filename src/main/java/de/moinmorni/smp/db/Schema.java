package de.moinmorni.smp.db;

import java.sql.Statement;
import java.util.List;

/**
 * Tabellen von MorniSMP. Jede neue Fassung hängt hinten an (nie alte ändern): {@code PRAGMA user_version} merkt sich,
 * wie weit eine Datei schon ist.
 *
 * <p>Geld steht immer in Hundertstel-Talern (ganze Zahlen, {@code 100} = 1 Taler). Gegenstände stehen als einzelnes
 * Stück ({@code item}, Paper-Format) plus Anzahl ({@code amount}).
 */
final class Schema {

    private Schema() {
    }

    private static final List<String> STEPS = List.of(
            // 1: Grundstock
            """
            CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);

            CREATE TABLE accounts(
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              balance INTEGER NOT NULL CHECK (balance >= 0),
              reported INTEGER NOT NULL DEFAULT 0,
              created INTEGER NOT NULL);
            CREATE INDEX accounts_rich ON accounts(balance DESC);

            CREATE TABLE ledger(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              time INTEGER NOT NULL,
              src TEXT NOT NULL,
              dst TEXT NOT NULL,
              amount INTEGER NOT NULL CHECK (amount > 0),
              reason TEXT NOT NULL,
              ref TEXT);
            CREATE INDEX ledger_src ON ledger(src, time);
            CREATE INDEX ledger_dst ON ledger(dst, time);

            CREATE TABLE listings(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              seller TEXT NOT NULL,
              seller_name TEXT NOT NULL,
              item BLOB NOT NULL,
              item_key TEXT NOT NULL,
              item_name TEXT NOT NULL,
              amount INTEGER NOT NULL CHECK (amount > 0),
              category TEXT NOT NULL,
              price INTEGER NOT NULL CHECK (price > 0),
              created INTEGER NOT NULL,
              expires INTEGER NOT NULL,
              state TEXT NOT NULL,
              buyer TEXT,
              ended INTEGER);
            CREATE INDEX listings_state ON listings(state, category);
            CREATE INDEX listings_seller ON listings(seller, state);

            CREATE TABLE sales(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              item_key TEXT NOT NULL,
              unit_price INTEGER NOT NULL,
              amount INTEGER NOT NULL,
              time INTEGER NOT NULL,
              source TEXT NOT NULL);
            CREATE INDEX sales_key ON sales(item_key, time);

            CREATE TABLE mailbox(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner TEXT NOT NULL,
              item BLOB NOT NULL,
              item_key TEXT NOT NULL,
              item_name TEXT NOT NULL,
              amount INTEGER NOT NULL CHECK (amount > 0),
              reason TEXT NOT NULL,
              created INTEGER NOT NULL);
            CREATE INDEX mailbox_owner ON mailbox(owner, id);

            CREATE TABLE orders(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              item BLOB NOT NULL,
              item_key TEXT NOT NULL,
              item_name TEXT NOT NULL,
              category TEXT NOT NULL,
              wanted INTEGER NOT NULL CHECK (wanted > 0),
              delivered INTEGER NOT NULL DEFAULT 0,
              unit_price INTEGER NOT NULL CHECK (unit_price > 0),
              escrow INTEGER NOT NULL CHECK (escrow >= 0),
              created INTEGER NOT NULL,
              expires INTEGER NOT NULL,
              state TEXT NOT NULL,
              CHECK (delivered >= 0 AND delivered <= wanted));
            CREATE INDEX orders_state ON orders(state, id);
            CREATE INDEX orders_owner ON orders(owner, state);

            CREATE TABLE market_stats(
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              sales INTEGER NOT NULL DEFAULT 0,
              purchases INTEGER NOT NULL DEFAULT 0,
              orders_filled INTEGER NOT NULL DEFAULT 0,
              earned INTEGER NOT NULL DEFAULT 0);

            CREATE TABLE notices(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner TEXT NOT NULL,
              key TEXT NOT NULL,
              args TEXT NOT NULL,
              created INTEGER NOT NULL);
            CREATE INDEX notices_owner ON notices(owner, id);

            CREATE TABLE buyback_daily(
              uuid TEXT NOT NULL,
              day TEXT NOT NULL,
              item_key TEXT NOT NULL,
              sold INTEGER NOT NULL,
              PRIMARY KEY (uuid, day, item_key));

            CREATE TABLE claims(
              world TEXT NOT NULL,
              cx INTEGER NOT NULL,
              cz INTEGER NOT NULL,
              owner TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              pvp INTEGER NOT NULL DEFAULT 0,
              created INTEGER NOT NULL,
              PRIMARY KEY (world, cx, cz));
            CREATE INDEX claims_owner ON claims(owner);

            CREATE TABLE trust(
              owner TEXT NOT NULL,
              trusted TEXT NOT NULL,
              trusted_name TEXT NOT NULL,
              PRIMARY KEY (owner, trusted));

            CREATE TABLE stalls(
              id INTEGER PRIMARY KEY,
              tenant TEXT,
              tenant_name TEXT,
              paid_until INTEGER NOT NULL DEFAULT 0,
              warned INTEGER NOT NULL DEFAULT 0);

            CREATE TABLE shops(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              world TEXT NOT NULL,
              x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
              sx INTEGER NOT NULL, sy INTEGER NOT NULL, sz INTEGER NOT NULL,
              owner TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              item BLOB NOT NULL,
              item_key TEXT NOT NULL,
              item_name TEXT NOT NULL,
              mode TEXT NOT NULL,
              amount INTEGER NOT NULL CHECK (amount > 0),
              price INTEGER NOT NULL CHECK (price > 0),
              stall INTEGER,
              created INTEGER NOT NULL);
            CREATE UNIQUE INDEX shops_container ON shops(world, x, y, z);
            CREATE UNIQUE INDEX shops_sign ON shops(world, sx, sy, sz);
            CREATE INDEX shops_owner ON shops(owner);

            CREATE TABLE players(
              uuid TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              first_join INTEGER NOT NULL,
              last_seen INTEGER NOT NULL,
              playtime INTEGER NOT NULL DEFAULT 0,
              day TEXT NOT NULL DEFAULT '',
              day_seconds INTEGER NOT NULL DEFAULT 0,
              streak INTEGER NOT NULL DEFAULT 0,
              last_reward TEXT NOT NULL DEFAULT '',
              nether INTEGER NOT NULL DEFAULT 0,
              the_end INTEGER NOT NULL DEFAULT 0,
              sidebar INTEGER NOT NULL DEFAULT 1);

            CREATE TABLE homes(
              uuid TEXT NOT NULL,
              name TEXT NOT NULL,
              world TEXT NOT NULL,
              x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
              yaw REAL NOT NULL, pitch REAL NOT NULL,
              PRIMARY KEY (uuid, name));

            CREATE TABLE bounties(
              target TEXT PRIMARY KEY,
              target_name TEXT NOT NULL,
              amount INTEGER NOT NULL CHECK (amount > 0),
              updated INTEGER NOT NULL);
            """,
            // 2: Händlerhaus – wie viel jeder heute schon gekauft hat (für Tagesgrenzen einzelner Waren)
            """
            CREATE TABLE shop_daily(
              uuid TEXT NOT NULL,
              day TEXT NOT NULL,
              item_key TEXT NOT NULL,
              bought INTEGER NOT NULL CHECK (bought >= 0),
              PRIMARY KEY (uuid, day, item_key));
            CREATE INDEX ledger_reason ON ledger(reason, time);
            """,
            // 3: Börse – Kurs (Druck) je Ware und stündlicher Verlauf
            """
            CREATE TABLE courses(
              item_key TEXT PRIMARY KEY,
              pressure REAL NOT NULL,
              updated INTEGER NOT NULL);

            CREATE TABLE course_log(
              item_key TEXT NOT NULL,
              hour INTEGER NOT NULL,
              pressure REAL NOT NULL,
              updated INTEGER NOT NULL,
              bought INTEGER NOT NULL DEFAULT 0,
              sold INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (item_key, hour));
            CREATE INDEX course_log_hour ON course_log(hour);
            """);

    static void migrate(Database db) {
        db.run(c -> {
            long version;
            try (Statement s = c.createStatement(); var rs = s.executeQuery("PRAGMA user_version")) {
                version = rs.next() ? rs.getLong(1) : 0;
            }
            for (int i = (int) version; i < STEPS.size(); i++) {
                try (Statement s = c.createStatement()) {
                    for (String sql : STEPS.get(i).split(";")) {
                        if (!sql.isBlank()) {
                            s.execute(sql);
                        }
                    }
                    s.execute("PRAGMA user_version = " + (i + 1));
                }
            }
        });
    }
}
