package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.SmpPlugin;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.text.Msg;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /gs}: Grundstücke (je ein Chunk). Unterbefehle: sichern, aufgeben, info, liste, vertrauen, misstrauen, pvp,
 * grenzen. Ohne Unterbefehl: Info zu dem Chunk, auf dem man steht.
 */
public final class ClaimCommand implements Router.Handler {

    private static final List<String> SUBS = List.of("sichern", "aufgeben", "info", "liste", "vertrauen",
            "misstrauen", "pvp", "grenzen", "hilfe");

    private final Smp s;
    private final SmpPlugin plugin;

    public ClaimCommand(Smp s, SmpPlugin plugin) {
        this.s = s;
        this.plugin = plugin;
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        Player p = (Player) sender;
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sichern", "claim", "kaufen", "neu" -> claim(p);
            case "aufgeben", "unclaim", "weg", "freigeben" -> unclaim(p);
            case "info" -> info(p);
            case "liste", "list" -> list(p);
            case "vertrauen", "trust" -> trust(p, args);
            case "misstrauen", "untrust", "entfernen" -> untrust(p, args);
            case "pvp" -> pvp(p, args);
            case "grenzen", "rand", "border", "show" -> show(p);
            default -> help(p);
        }
    }

    @Override
    public List<String> tab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return SUBS;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("pvp")) {
                return List.of("an", "aus");
            }
            if (sub.equals("vertrauen") || sub.equals("misstrauen") || sub.equals("trust") || sub.equals("untrust")) {
                return Router.online();
            }
        }
        return List.of();
    }

    private void help(Player p) {
        s.msg.send(p, "claim.help.title");
        for (String k : new String[] {"sichern", "aufgeben", "info", "liste", "vertrauen", "misstrauen", "pvp",
                "grenzen"}) {
            s.msg.line(p, "claim.help." + k);
        }
        s.msg.line(p, "claim.help.cost", Msg.t("free", s.settings.claimsFree),
                s.msg.money("price", s.settings.claimPrice, p), Msg.t("max", s.settings.claimsMax));
    }

    private void claim(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        if (s.town.isSpawnWorld(w)) {
            s.msg.send(p, "claim.spawn-world");
            return;
        }
        Claims.ClaimResult r = s.claims.claim(w.getName(), cx, cz, p.getUniqueId(), p.getName(), s.settings.claimsFree,
                s.settings.claimPrice, s.settings.claimsMax);
        switch (r.status()) {
            case OK -> {
                if (r.paid() > 0) {
                    s.msg.send(p, "claim.done.paid", s.msg.money("price", r.paid(), p),
                            Msg.t("count", s.claims.count(p.getUniqueId())));
                } else {
                    s.msg.send(p, "claim.done.free", Msg.t("count", s.claims.count(p.getUniqueId())),
                            Msg.t("free", s.settings.claimsFree));
                }
                show(p);
            }
            case OWN -> s.msg.send(p, "claim.own");
            case TAKEN -> s.msg.send(p, "claim.taken", Msg.t("owner", r.claim().ownerName()));
            case LIMIT -> s.msg.send(p, "claim.limit", Msg.t("max", s.settings.claimsMax));
            case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", s.msg.money("missing",
                    s.settings.claimPrice - s.bank.balance(p.getUniqueId()), p));
            case FORBIDDEN -> s.msg.send(p, "claim.spawn-world");
        }
    }

    private Claims.Claim here(Player p) {
        Location l = p.getLocation();
        return s.claims.at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    private void unclaim(Player p) {
        Claims.Claim c = here(p);
        if (c == null) {
            s.msg.send(p, "claim.none-here");
            return;
        }
        boolean admin = s.bypass(p);
        if (!c.owner().equals(p.getUniqueId()) && !admin) {
            s.msg.send(p, "claim.not-yours", Msg.t("owner", c.ownerName()));
            return;
        }
        if (s.claims.unclaim(c.world(), c.cx(), c.cz(), p.getUniqueId(), admin)) {
            int shops = plugin.shopListener().removeInChunk(c.world(), c.cx(), c.cz());
            s.msg.send(p, "claim.released", Msg.t("shops", shops));
        }
    }

    private void info(Player p) {
        Claims.Claim c = here(p);
        Location l = p.getLocation();
        if (s.town.inArea(l)) {
            s.msg.send(p, "claim.info.town");
        } else if (c == null) {
            s.msg.send(p, "claim.info.free", Msg.t("x", l.getBlockX() >> 4), Msg.t("z", l.getBlockZ() >> 4));
            s.msg.line(p, "claim.info.hint");
        } else {
            s.msg.send(p, "claim.info.owned", Msg.t("owner", c.ownerName()),
                    Msg.t("pvp", s.msg.raw(s.msg.lang(p), c.pvp() ? "word.on" : "word.off")));
            if (c.owner().equals(p.getUniqueId())) {
                List<String> trusted = s.claims.trustedNames(p.getUniqueId());
                s.msg.line(p, "claim.info.trusted", Msg.t("names", trusted.isEmpty() ? "–" : String.join(", ", trusted)));
            }
        }
        s.msg.line(p, "claim.info.count", Msg.t("count", s.claims.count(p.getUniqueId())),
                Msg.t("free", s.settings.claimsFree), Msg.t("max", s.settings.claimsMax));
        show(p);
    }

    private void list(Player p) {
        List<Claims.Claim> list = s.claims.of(p.getUniqueId());
        if (list.isEmpty()) {
            s.msg.send(p, "claim.list.empty");
            return;
        }
        s.msg.send(p, "claim.list.title", Msg.t("count", list.size()));
        for (Claims.Claim c : list) {
            s.msg.line(p, "claim.list.line", Msg.t("world", c.world()), Msg.t("x", c.cx() * 16 + 8),
                    Msg.t("z", c.cz() * 16 + 8), Msg.t("pvp", s.msg.raw(s.msg.lang(p), c.pvp() ? "word.on" : "word.off")));
        }
    }

    private void trust(Player p, String[] args) {
        if (args.length < 2) {
            s.msg.send(p, "claim.trust.usage");
            return;
        }
        Player o = Bukkit.getPlayerExact(args[1]);
        UUID u = o != null ? o.getUniqueId() : s.bank.byName(args[1]);
        String name = o != null ? o.getName() : u == null ? args[1] : s.bank.name(u);
        if (u == null) {
            s.msg.send(p, "player.unknown", Msg.t("name", args[1]));
            return;
        }
        if (u.equals(p.getUniqueId())) {
            s.msg.send(p, "claim.trust.self");
            return;
        }
        s.claims.trust(p.getUniqueId(), u, name);
        s.msg.send(p, "claim.trusted", Msg.t("name", name));
        if (o != null) {
            s.msg.send(o, "claim.trusted.you", Msg.t("name", p.getName()));
        }
    }

    private void untrust(Player p, String[] args) {
        if (args.length < 2) {
            s.msg.send(p, "claim.untrust.usage");
            return;
        }
        UUID u = s.bank.byName(args[1]);
        if (u == null || !s.claims.untrust(p.getUniqueId(), u)) {
            s.msg.send(p, "claim.untrust.unknown", Msg.t("name", args[1]));
            return;
        }
        s.msg.send(p, "claim.untrusted", Msg.t("name", args[1]));
    }

    private void pvp(Player p, String[] args) {
        Claims.Claim c = here(p);
        if (c == null || !c.owner().equals(p.getUniqueId())) {
            s.msg.send(p, "claim.pvp.not-yours");
            return;
        }
        boolean on = args.length >= 2 ? args[1].equalsIgnoreCase("an") || args[1].equalsIgnoreCase("on")
                : !c.pvp();
        s.claims.pvp(c.world(), c.cx(), c.cz(), p.getUniqueId(), on);
        s.msg.send(p, on ? "claim.pvp.on" : "claim.pvp.off");
    }

    /** Ränder des Chunks (und der Nachbarn) 10 Sekunden lang mit Partikeln zeigen. */
    private void show(Player p) {
        Location l = p.getLocation();
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        World w = l.getWorld();
        int[] runs = {0};
        Bukkit.getScheduler().runTaskTimer(s.plugin, task -> {
            if (!p.isOnline() || runs[0]++ >= 10) {
                task.cancel();
                return;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Claims.Claim c = s.claims.at(w.getName(), cx + dx, cz + dz);
                    boolean town = s.town.chunkNear(w, cx + dx, cz + dz, 0);
                    if (c == null && !town) {
                        continue;
                    }
                    Color col = town ? Color.fromRGB(0x5DA9E9) : c.owner().equals(p.getUniqueId())
                            ? Color.fromRGB(0x2BB673) : Color.fromRGB(0xE5484D);
                    edge(p, w, (cx + dx) * 16, (cz + dz) * 16, l.getY(), col);
                }
            }
        }, 0L, 20L);
    }

    private static void edge(Player p, World w, int x0, int z0, double y, Color col) {
        Particle.DustOptions dust = new Particle.DustOptions(col, 1.2f);
        for (int i = 0; i <= 16; i += 2) {
            for (double dy : new double[] {0.3, 1.5}) {
                p.spawnParticle(Particle.DUST, x0 + i, y + dy, z0, 1, dust);
                p.spawnParticle(Particle.DUST, x0 + i, y + dy, z0 + 16, 1, dust);
                p.spawnParticle(Particle.DUST, x0, y + dy, z0 + i, 1, dust);
                p.spawnParticle(Particle.DUST, x0 + 16, y + dy, z0 + i, 1, dust);
            }
        }
    }
}
