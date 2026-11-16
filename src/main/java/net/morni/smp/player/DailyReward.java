package net.morni.smp.player;

import net.morni.smp.db.Database;
import net.morni.smp.economy.Bank;

import java.util.UUID;

/**
 * Tägliche Belohnung: einmal am Tag, nur wer heute schon {@code minSeconds} gespielt hat. Wer jeden Tag kommt, baut
 * eine Serie auf (mehr Taler, bis {@code maxStreak}); ein ausgelassener Tag fängt wieder bei 1 an. Nichts davon gibt
 * es zu kaufen.
 */
public final class DailyReward {

    public enum Status {
        OK, TOO_EARLY, ALREADY
    }

    /** {@code missingSeconds}: wie lange noch spielen (bei TOO_EARLY). */
    public record Outcome(Status status, int streak, long money, long missingSeconds) {
    }

    private DailyReward() {
    }

    /**
     * Rein rechnerisch: darf {@code today} abgeholt werden, und mit welcher Serie und wie viel Geld?
     *
     * @param lastReward Tag der letzten Abholung („“ = noch nie)
     * @param yesterday  der Tag vor {@code today}
     */
    public static Outcome evaluate(String lastReward, int streak, String today, String yesterday, long daySeconds,
                                   long minSeconds, long base, long perDay, int maxStreak) {
        if (today.equals(lastReward)) {
            return new Outcome(Status.ALREADY, streak, 0, 0);
        }
        if (daySeconds < minSeconds) {
            return new Outcome(Status.TOO_EARLY, streak, 0, minSeconds - daySeconds);
        }
        int next = yesterday.equals(lastReward) ? Math.min(streak + 1, Math.max(1, maxStreak)) : 1;
        return new Outcome(Status.OK, next, base + perDay * (next - 1), 0);
    }

    /**
     * Abholen: prüfen, Serie und Tag speichern, Geld gutschreiben – alles in einem Auftrag (kein doppeltes Abholen,
     * auch nicht bei zwei schnellen Klicks).
     */
    public static Outcome claim(Players players, Bank bank, UUID uuid, String today, String yesterday, long minSeconds,
                                long base, long perDay, int maxStreak) {
        return players.db().tx(c -> {
            Object[] r = Database.one(c, "SELECT last_reward, streak, CASE WHEN day = ? THEN day_seconds ELSE 0 END "
                    + "FROM players WHERE uuid = ?", rs -> new Object[] {rs.getString(1), rs.getInt(2), rs.getLong(3)},
                    today, uuid);
            if (r == null) {
                return new Outcome(Status.TOO_EARLY, 0, 0, minSeconds);
            }
            Outcome o = evaluate((String) r[0], (Integer) r[1], today, yesterday, (Long) r[2], minSeconds, base,
                    perDay, maxStreak);
            if (o.status() != Status.OK) {
                return o;
            }
            Database.update(c, "UPDATE players SET last_reward = ?, streak = ? WHERE uuid = ?", today, o.streak(),
                    uuid);
            if (o.money() > 0) {
                bank.move(c, Bank.SERVER, uuid.toString(), o.money(), "belohnung", today);
            }
            return o;
        });
    }
}
