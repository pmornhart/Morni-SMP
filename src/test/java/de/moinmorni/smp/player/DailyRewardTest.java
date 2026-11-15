package de.moinmorni.smp.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.moinmorni.smp.TestClock;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.player.DailyReward.Outcome;
import de.moinmorni.smp.player.DailyReward.Status;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyRewardTest {

    @Test
    void streakGrowsAndBreaks() {
        Outcome first = DailyReward.evaluate("", 0, "2026-11-15", "2026-11-14", 900, 900, 2_500, 500, 7);
        assertEquals(Status.OK, first.status());
        assertEquals(1, first.streak());
        assertEquals(2_500, first.money());
        Outcome next = DailyReward.evaluate("2026-11-15", 1, "2026-11-16", "2026-11-15", 1_000, 900, 2_500, 500, 7);
        assertEquals(2, next.streak());
        assertEquals(3_000, next.money());
        Outcome capped = DailyReward.evaluate("2026-11-15", 7, "2026-11-16", "2026-11-15", 1_000, 900, 2_500, 500, 7);
        assertEquals(7, capped.streak());
        Outcome broken = DailyReward.evaluate("2026-11-10", 5, "2026-11-16", "2026-11-15", 1_000, 900, 2_500, 500, 7);
        assertEquals(1, broken.streak());
        Outcome early = DailyReward.evaluate("2026-11-15", 1, "2026-11-16", "2026-11-15", 600, 900, 2_500, 500, 7);
        assertEquals(Status.TOO_EARLY, early.status());
        assertEquals(300, early.missingSeconds());
        assertEquals(Status.ALREADY,
                DailyReward.evaluate("2026-11-16", 2, "2026-11-16", "2026-11-15", 5_000, 900, 2_500, 500, 7).status());
    }

    @Test
    void claimOnlyOncePerDayAndOnlyAfterPlaying() {
        try (Database db = Database.memory()) {
            TestClock clock = new TestClock();
            Bank bank = new Bank(db, clock, 0);
            Players players = new Players(db, clock);
            UUID u = UUID.randomUUID();
            players.join(u, "Anna");
            bank.ensure(u, "Anna");
            assertEquals(Status.TOO_EARLY,
                    DailyReward.claim(players, bank, u, "2026-11-15", "2026-11-14", 900, 2_500, 500, 7).status());
            players.addPlaytime(u, 600, "2026-11-15");
            players.addPlaytime(u, 300, "2026-11-15");
            assertEquals(900, players.todaySeconds(u, "2026-11-15"));
            assertEquals(Status.OK,
                    DailyReward.claim(players, bank, u, "2026-11-15", "2026-11-14", 900, 2_500, 500, 7).status());
            assertEquals(Status.ALREADY,
                    DailyReward.claim(players, bank, u, "2026-11-15", "2026-11-14", 900, 2_500, 500, 7).status());
            assertEquals(2_500, bank.balance(u));
            players.addPlaytime(u, 900, "2026-11-16");
            assertEquals(900, players.todaySeconds(u, "2026-11-16"), "neuer Tag fängt bei 0 an");
            Outcome o = DailyReward.claim(players, bank, u, "2026-11-16", "2026-11-15", 900, 2_500, 500, 7);
            assertEquals(2, o.streak());
            assertEquals(2_500 + 3_000, bank.balance(u));
        }
    }

    @Test
    void homesHaveALimit() {
        try (Database db = Database.memory()) {
            Players players = new Players(db, new TestClock());
            UUID u = UUID.randomUUID();
            players.join(u, "Anna");
            for (String n : new String[] {"haus", "mine", "farm"}) {
                assertEquals(Players.HomeStatus.OK, players.setHome(u, new Players.Home(n, "world", 1, 64, 1, 0, 0), 3));
            }
            assertEquals(Players.HomeStatus.LIMIT, players.setHome(u, new Players.Home("vier", "world", 1, 64, 1, 0, 0), 3));
            assertEquals(Players.HomeStatus.REPLACED, players.setHome(u, new Players.Home("HAUS", "world", 5, 70, 5, 0, 0), 3));
            assertEquals(5.0, players.home(u, "haus").orElseThrow().x());
            assertEquals(Players.HomeStatus.BAD_NAME, players.setHome(u, new Players.Home("a b", "world", 1, 64, 1, 0, 0), 3));
            assertEquals(true, players.deleteHome(u, "Mine"));
            assertEquals(2, players.homes(u).size());
        }
    }
}
