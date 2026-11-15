package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.player.Players;
import de.moinmorni.smp.teleport.RandomTeleport;
import de.moinmorni.smp.teleport.Teleports;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /spawn}, Zuhause, {@code /tpa} & Co., {@code /back}, {@code /rtp}. */
public final class TravelCommands {

    /** Name, wenn man bei /sethome keinen angibt. */
    public static final String DEFAULT_HOME = "zuhause";

    private final Smp s;
    private final RandomTeleport rtp;

    public TravelCommands(Smp s, RandomTeleport rtp) {
        this.s = s;
        this.rtp = rtp;
    }

    public Router.Handler spawn() {
        return (sender, args) -> {
            Player p = (Player) sender;
            s.teleports.start(p, s.town.spawn(), "place.spawn", null);
        };
    }

    public Router.Handler rtp() {
        return (sender, args) -> rtp.command((Player) sender, args);
    }

    public Router.Handler back() {
        return (sender, args) -> {
            Player p = (Player) sender;
            Location l = s.teleports.back(p);
            if (l == null || l.getWorld() == null) {
                s.msg.send(p, "back.none");
                return;
            }
            s.teleports.start(p, l, "place.back", null);
        };
    }

    // ---------------------------------------------------------------- Zuhause

    public Router.Handler setHome() {
        return (sender, args) -> {
            Player p = (Player) sender;
            String name = args.length == 0 ? DEFAULT_HOME : args[0];
            if (s.town.inArea(p.getLocation())) {
                s.msg.send(p, "home.not-in-town");
                return;
            }
            Location l = p.getLocation();
            Players.HomeStatus st = s.players.setHome(p.getUniqueId(), new Players.Home(name, l.getWorld().getName(),
                    l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()), s.settings.maxHomes);
            switch (st) {
                case OK -> s.msg.send(p, "home.set", Msg.t("name", name));
                case REPLACED -> s.msg.send(p, "home.moved", Msg.t("name", name));
                case LIMIT -> s.msg.send(p, "home.limit", Msg.t("max", s.settings.maxHomes));
                case BAD_NAME -> s.msg.send(p, "home.bad-name");
            }
        };
    }

    public Router.Handler home() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player p = (Player) sender;
                List<Players.Home> homes = s.players.homes(p.getUniqueId());
                if (homes.isEmpty()) {
                    s.msg.send(p, "home.none");
                    return;
                }
                if (args.length == 0 && homes.size() > 1) {
                    menu(p, homes);
                    return;
                }
                Optional<Players.Home> h = args.length == 0 ? Optional.of(homes.get(0))
                        : s.players.home(p.getUniqueId(), args[0]);
                if (h.isEmpty()) {
                    s.msg.send(p, "home.unknown", Msg.t("name", args[0]));
                    return;
                }
                go(p, h.get());
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 && sender instanceof Player p ? names(p) : List.of();
            }
        };
    }

    private void go(Player p, Players.Home h) {
        World w = Bukkit.getWorld(h.world());
        if (w == null) {
            s.msg.send(p, "home.world-gone");
            return;
        }
        s.teleports.start(p, new Location(w, h.x(), h.y(), h.z(), h.yaw(), h.pitch()), "place.home", null);
    }

    private void menu(Player p, List<Players.Home> homes) {
        String lang = s.msg.lang(p);
        Menu m = new Menu(3, s.msg.text(lang, "home.menu.title"));
        for (int i = 0; i < homes.size() && i < 18; i++) {
            Players.Home h = homes.get(i);
            m.set(i, Items.button(Material.RED_BED, Component.text(h.name()), List.of(
                    s.msg.text(lang, "home.menu.where", Msg.t("world", h.world()), Msg.t("x", (int) h.x()),
                            Msg.t("y", (int) h.y()), Msg.t("z", (int) h.z())),
                    s.msg.text(lang, "home.menu.click"))), pl -> {
                        pl.closeInventory();
                        go(pl, h);
                    });
        }
        m.set(26, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    private List<String> names(Player p) {
        List<String> out = new ArrayList<>();
        for (Players.Home h : s.players.homes(p.getUniqueId())) {
            out.add(h.name());
        }
        return out;
    }

    public Router.Handler delHome() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player p = (Player) sender;
                if (args.length == 0) {
                    s.msg.send(p, "home.del.usage");
                    return;
                }
                if (s.players.deleteHome(p.getUniqueId(), args[0])) {
                    s.msg.send(p, "home.deleted", Msg.t("name", args[0]));
                } else {
                    s.msg.send(p, "home.unknown", Msg.t("name", args[0]));
                }
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 && sender instanceof Player p ? names(p) : List.of();
            }
        };
    }

    public Router.Handler homes() {
        return (sender, args) -> {
            Player p = (Player) sender;
            List<Players.Home> homes = s.players.homes(p.getUniqueId());
            if (homes.isEmpty()) {
                s.msg.send(p, "home.none");
                return;
            }
            s.msg.send(p, "home.list", Msg.t("count", homes.size()), Msg.t("max", s.settings.maxHomes));
            for (Players.Home h : homes) {
                p.sendMessage(s.msg.text(p, "home.list.line", Msg.t("name", h.name()), Msg.t("world", h.world()))
                        .clickEvent(ClickEvent.runCommand("/home " + h.name())));
            }
        };
    }

    // ---------------------------------------------------------------- Anfragen

    public Router.Handler tpa(boolean here) {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player p = (Player) sender;
                if (args.length == 0) {
                    s.msg.send(p, here ? "tpa.usage.here" : "tpa.usage");
                    return;
                }
                Player to = Bukkit.getPlayerExact(args[0]);
                if (to == null) {
                    s.msg.send(p, "player.offline", Msg.t("name", args[0]));
                    return;
                }
                if (to.equals(p)) {
                    s.msg.send(p, "tpa.self");
                    return;
                }
                if (!s.teleports.request(p, to, here)) {
                    s.msg.send(p, "tpa.pending", Msg.t("name", to.getName()));
                    return;
                }
                s.msg.send(p, "tpa.sent", Msg.t("name", to.getName()));
                s.msg.send(to, here ? "tpa.incoming.here" : "tpa.incoming", Msg.t("name", p.getName()));
                to.sendMessage(s.msg.text(to, "tpa.buttons", Msg.t("name", p.getName()))
                        .append(Component.space())
                        .append(s.msg.text(to, "tpa.accept.button").clickEvent(
                                ClickEvent.runCommand("/tpaccept " + p.getName())))
                        .append(Component.space())
                        .append(s.msg.text(to, "tpa.deny.button").clickEvent(
                                ClickEvent.runCommand("/tpdeny " + p.getName()))));
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 ? Router.online() : List.of();
            }
        };
    }

    public Router.Handler accept(boolean yes) {
        return (sender, args) -> {
            Player p = (Player) sender;
            Teleports.Request r = s.teleports.take(p, args.length == 0 ? null : args[0]);
            if (r == null) {
                s.msg.send(p, "tpa.none");
                return;
            }
            Player from = Bukkit.getPlayer(r.from());
            if (from == null) {
                s.msg.send(p, "player.offline", Msg.t("name", r.fromName()));
                return;
            }
            if (!yes) {
                s.msg.send(p, "tpa.denied.you", Msg.t("name", from.getName()));
                s.msg.send(from, "tpa.denied", Msg.t("name", p.getName()));
                return;
            }
            s.msg.send(p, "tpa.accepted.you", Msg.t("name", from.getName()));
            s.msg.send(from, "tpa.accepted", Msg.t("name", p.getName()));
            if (r.here()) {
                s.teleports.start(p, from.getLocation(), "place.player", null);
            } else {
                s.teleports.start(from, p.getLocation(), "place.player", null);
            }
        };
    }
}
