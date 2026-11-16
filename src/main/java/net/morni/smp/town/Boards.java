package net.morni.smp.town;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.morni.smp.economy.Bank;
import net.morni.smp.economy.Money;
import net.morni.smp.market.MarketStats;
import net.morni.smp.market.Stalls;
import net.morni.smp.text.Texts;
import net.morni.smp.town.Layout.StallSpot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

/**
 * Anzeigen in der Stadt: die Bestenliste über dem Brunnen (Reichste, meiste Verkäufe – schwebende Schrift, dreht sich
 * zum Betrachter) und die Hängeschilder der Marktstände (Nummer, frei oder Mieter).
 */
public final class Boards {

    /** Merkmal unserer Anzeigen (zum Wiederfinden und Aufräumen). */
    public static final String TAG = "morni_smp_board";

    private final Town town;
    private final Bank bank;
    private final MarketStats stats;
    private final Stalls stalls;

    public Boards(Town town, Bank bank, MarketStats stats, Stalls stalls) {
        this.town = town;
        this.bank = bank;
        this.stats = stats;
        this.stalls = stalls;
    }

    /** Bestenliste neu schreiben (legt die Anzeige an, falls sie fehlt). */
    public void refresh() {
        if (!town.ready()) {
            return;
        }
        Location at = town.board();
        World w = at.getWorld();
        if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        TextDisplay display = null;
        for (Entity e : w.getNearbyEntities(at, 3, 4, 3)) {
            if (e instanceof TextDisplay td && e.getScoreboardTags().contains(TAG)) {
                if (display == null) {
                    display = td;
                } else {
                    e.remove();
                }
            }
        }
        if (display == null) {
            display = w.spawn(at, TextDisplay.class, td -> {
                td.addScoreboardTag(TAG);
                td.setBillboard(Display.Billboard.VERTICAL);
                td.setBackgroundColor(Color.fromARGB(150, 20, 30, 40));
                td.setShadowed(true);
                td.setLineWidth(220);
                td.setPersistent(true);
            });
        }
        display.text(text());
    }

    private Component text() {
        StringBuilder sb = new StringBuilder();
        sb.append("<au><b>★ Bestenliste ★</b></au>\n<mu>Leaderboard</mu>\n\n");
        sb.append("<em>Reichste</em> <mu>· richest</mu>\n");
        List<Bank.Rich> rich = bank.top(5);
        if (rich.isEmpty()) {
            sb.append("<mu>–</mu>\n");
        }
        int i = 1;
        for (Bank.Rich r : rich) {
            sb.append("<au>").append(i++).append(".</au> <white>").append(escape(r.name())).append("</white> <mu>")
                    .append(Money.compact(r.balance(), "de")).append("</mu>\n");
        }
        sb.append("\n<em>Meiste Verkäufe</em> <mu>· top sellers</mu>\n");
        List<MarketStats.Seller> sellers = stats.topSellers(5);
        if (sellers.isEmpty()) {
            sb.append("<mu>–</mu>\n");
        }
        i = 1;
        for (MarketStats.Seller s : sellers) {
            sb.append("<au>").append(i++).append(".</au> <white>").append(escape(s.name())).append("</white> <mu>")
                    .append(s.sales()).append("×</mu>\n");
        }
        return Texts.MM.deserialize(sb.toString().stripTrailing());
    }

    /** Spielernamen nie als Formatierung lesen. */
    private static String escape(String s) {
        return Texts.MM.escapeTags(s == null ? "?" : s);
    }

    /** Hängeschilder aller Stände aktualisieren. */
    public void stallSigns() {
        if (!town.ready()) {
            return;
        }
        for (Stalls.Stall s : stalls.all()) {
            stallSign(s);
        }
    }

    public void stallSign(Stalls.Stall s) {
        StallSpot spot = town.stall(s.id());
        if (spot == null) {
            return;
        }
        Block b = town.block(spot.signX(), spot.signY(), spot.signZ());
        if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4) || !(b.getState() instanceof Sign sign)) {
            return;
        }
        String rent = Money.format(stalls.rent(), "de");
        write(sign.getSide(Side.FRONT), s.free()
                ? List.of("<au>Stand " + s.id() + "</au>", "<em>frei</em> · free", "<mu>" + rent + "/Woche</mu>",
                "/stand mieten")
                : List.of("<au>Stand " + s.id() + "</au>", "<white>" + escape(s.tenantName()) + "</white>", "", ""));
        write(sign.getSide(Side.BACK), s.free()
                ? List.of("<au>Stall " + s.id() + "</au>", "<em>free</em>", "<mu>" + Money.format(stalls.rent(), "en")
                + "/week</mu>", "/stand mieten")
                : List.of("<au>Stall " + s.id() + "</au>", "<white>" + escape(s.tenantName()) + "</white>", "", ""));
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private static void write(SignSide side, List<String> lines) {
        for (int i = 0; i < 4; i++) {
            side.line(i, i < lines.size() ? Texts.MM.deserialize(lines.get(i)) : Component.empty());
        }
    }
}
