package de.moinmorni.smp.hud;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.text.Msg;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Die Leiste rechts: Kontostand, wo man steht (Marktstadt, Grundstück, Wildnis), Kopfgeld, Kampf, wer online ist.
 * Jeder Spieler hat ein eigenes Scoreboard; die Teams des Haupt-Scoreboards (Ränge des Netzwerks über dem Kopf und in
 * der Tab-Liste) werden hineinkopiert, damit dort nichts verloren geht. {@code /leiste} schaltet sie ab.
 */
public final class Sidebar {

    private static final String OBJECTIVE = "mornismp";
    /** Unsichtbare, eindeutige Einträge je Zeile (der Text kommt über den eigenen Namen der Zeile). */
    private static final String[] ENTRIES = new String[15];

    static {
        for (int i = 0; i < ENTRIES.length; i++) {
            ENTRIES[i] = "§" + Integer.toHexString(i) + "§r";
        }
    }

    private final Smp s;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    public Sidebar(Smp s) {
        this.s = s;
    }

    public boolean enabled(Player p) {
        return s.players.info(p.getUniqueId()).map(i -> i.sidebar()).orElse(s.settings.sidebarDefault);
    }

    /** Beim Betreten oder Einschalten. */
    public void show(Player p) {
        if (!enabled(p)) {
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective(OBJECTIVE, Criteria.DUMMY, s.msg.text(p, "sidebar.title"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.numberFormat(NumberFormat.blank());
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p);
    }

    public void hide(Player p) {
        if (boards.remove(p.getUniqueId()) != null && p.isOnline()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** {@code /leiste}: an/aus. Liefert den neuen Zustand. */
    public boolean toggle(Player p) {
        boolean on = !boards.containsKey(p.getUniqueId());
        s.players.sidebar(p.getUniqueId(), on);
        if (on) {
            show(p);
        } else {
            hide(p);
        }
        return on;
    }

    /** Alle zwei Sekunden. */
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) {
                update(p);
            }
        }
    }

    private void update(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) {
            return;
        }
        if (p.getScoreboard() != sb) {
            p.setScoreboard(sb);
        }
        copyTeams(sb);
        Objective o = sb.getObjective(OBJECTIVE);
        if (o == null) {
            return;
        }
        String lang = s.msg.lang(p);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(s.msg.text(lang, "sidebar.balance", Msg.t("money", Money.format(s.bank.balance(p.getUniqueId()), lang))));
        lines.add(s.msg.text(lang, "sidebar.place", Msg.c("place", place(p, lang))));
        long bounty = s.bounties.of(p.getUniqueId());
        if (bounty > 0) {
            lines.add(s.msg.text(lang, "sidebar.bounty", Msg.t("money", Money.compact(bounty, lang))));
        }
        int combat = s.combat.secondsLeft(p);
        if (combat > 0) {
            lines.add(s.msg.text(lang, "sidebar.combat", Msg.t("seconds", combat)));
        }
        int mail = s.mailbox.count(p.getUniqueId());
        if (mail > 0) {
            lines.add(s.msg.text(lang, "sidebar.mail", Msg.t("count", mail)));
        }
        lines.add(s.msg.text(lang, "sidebar.online", Msg.t("count", Bukkit.getOnlinePlayers().size())));
        lines.add(Component.text(" "));
        lines.add(s.msg.text(lang, "sidebar.address", Msg.t("address", s.settings.address)));
        int n = Math.min(lines.size(), ENTRIES.length);
        for (int i = 0; i < ENTRIES.length; i++) {
            if (i < n) {
                Score sc = o.getScore(ENTRIES[i]);
                sc.setScore(n - i);
                sc.customName(lines.get(i));
            } else {
                sb.resetScores(ENTRIES[i]);
            }
        }
    }

    private Component place(Player p, String lang) {
        Location l = p.getLocation();
        if (s.town.inArea(l)) {
            return s.msg.text(lang, "sidebar.place.town");
        }
        Claims.Claim c = s.claims.at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
        if (c != null) {
            return c.owner().equals(p.getUniqueId()) ? s.msg.text(lang, "sidebar.place.own")
                    : s.msg.text(lang, "sidebar.place.claim", Msg.t("owner", c.ownerName()));
        }
        return switch (l.getWorld().getEnvironment()) {
            case NETHER -> s.msg.text(lang, "sidebar.place.nether");
            case THE_END -> s.msg.text(lang, "sidebar.place.end");
            default -> s.msg.text(lang, "sidebar.place.wild");
        };
    }

    /** Teams (Ränge) aus dem Haupt-Scoreboard übernehmen. */
    private static void copyTeams(Scoreboard sb) {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : main.getTeams()) {
            Team c = sb.getTeam(t.getName());
            if (c == null) {
                c = sb.registerNewTeam(t.getName());
            }
            if (!c.prefix().equals(t.prefix())) {
                c.prefix(t.prefix());
            }
            if (!c.suffix().equals(t.suffix())) {
                c.suffix(t.suffix());
            }
            if (!c.displayName().equals(t.displayName())) {
                c.displayName(t.displayName());
            }
            if (t.hasColor() && (!c.hasColor() || !c.color().equals(t.color()))) {
                c.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(t.color()));
            }
            for (Team.Option opt : Team.Option.values()) {
                if (c.getOption(opt) != t.getOption(opt)) {
                    c.setOption(opt, t.getOption(opt));
                }
            }
            for (String entry : t.getEntries()) {
                if (!c.hasEntry(entry)) {
                    c.addEntry(entry);
                }
            }
            for (String entry : c.getEntries()) {
                if (!t.hasEntry(entry)) {
                    c.removeEntry(entry);
                }
            }
        }
        for (Team c : sb.getTeams()) {
            if (main.getTeam(c.getName()) == null) {
                c.unregister();
            }
        }
    }
}
