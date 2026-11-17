package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.market.ui.NoticeSender;
import de.moinmorni.smp.shop.ServerShop;
import de.moinmorni.smp.SmpPlugin;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.Stalls;
import de.moinmorni.smp.text.Msg;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /smpadmin} (Recht {@code morni.smp.admin} oder OP): Geld geben/nehmen/setzen, Bau-Modus, Stadt neu bauen,
 * Einstellungen neu laden, Marktstand räumen, Grundstück entfernen, Kennzahlen.
 */
public final class AdminCommand implements Router.Handler {

    private static final List<String> SUBS = List.of("geld", "bau", "stadt", "neuladen", "stand", "gs", "info", "shop",
            "boerse");
    private static final java.util.Set<String> MONEY_ACTIONS = java.util.Set.of("geben", "give", "nehmen", "take",
            "setzen", "set");

    private final Smp s;
    private final SmpPlugin plugin;
    private final MoneyCommands money;

    public AdminCommand(Smp s, SmpPlugin plugin, MoneyCommands money) {
        this.s = s;
        this.plugin = plugin;
        this.money = money;
        this.exchange = new ExchangeAdmin(s);
    }

    private final ExchangeAdmin exchange;

    @Override
    public boolean playersOnly() {
        return false;
    }

    private boolean allowed(CommandSender sender) {
        return !(sender instanceof Player p) || s.admin(p);
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        if (!allowed(sender)) {
            s.msg.send(sender, "command.no-permission");
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "geld", "money" -> money(sender, args);
            case "bau", "build" -> build(sender);
            case "stadt", "town" -> town(sender, args);
            case "neuladen", "reload" -> {
                plugin.reloadSettings();
                s.msg.send(sender, "admin.reloaded");
            }
            case "stand", "stall" -> stall(sender, args);
            case "gs", "claim" -> claim(sender);
            case "info" -> info(sender);
            case "shop", "haendler" -> shop(sender);
            case "boerse", "börse", "exchange" -> exchange.run(sender, args);
            default -> {
                s.msg.send(sender, "admin.help.title");
                for (String k : new String[] {"geld", "bau", "stadt", "neuladen", "stand", "gs", "info", "shop", "boerse"}) {
                    s.msg.line(sender, "admin.help." + k);
                }
            }
        }
    }

    @Override
    public List<String> tab(CommandSender sender, String[] args) {
        if (!allowed(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBS;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("boerse")) {
            return exchange.tab(args);
        }
        if (args.length == 2) {
            return switch (sub) {
                case "geld" -> {
                    java.util.List<String> all = new java.util.ArrayList<>(List.of("geben", "nehmen", "setzen"));
                    all.addAll(Router.online());
                    yield all;
                }
                case "stadt" -> List.of("neu", "info", "tore");
                case "stand" -> List.of("frei");
                case "gs" -> List.of("weg");
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("geld") && MONEY_ACTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return Router.online();
        }
        return List.of();
    }

    /**
     * {@code /smpadmin geld [geben|nehmen|setzen] [spieler] <betrag>}: ohne Art wird gegeben, ohne Spieler gilt es
     * einem selbst – so geben sich Owner und Admins zum Testen schnell Geld ({@code /smpadmin geld 5000}).
     */
    private void money(CommandSender sender, String[] args) {
        int i = 1;
        String action = "geben";
        if (args.length > i && MONEY_ACTIONS.contains(args[i].toLowerCase(Locale.ROOT))) {
            action = args[i].toLowerCase(Locale.ROOT);
            i++;
        }
        int left = args.length - i;
        if (left < 1 || left > 2 || (left == 1 && !(sender instanceof Player))) {
            s.msg.send(sender, "admin.money.usage");
            return;
        }
        MoneyCommands.Who w;
        if (left == 1) {
            Player self = (Player) sender;
            w = new MoneyCommands.Who(self.getUniqueId(), self.getName(), self);
        } else {
            w = money.find(args[i]);
            if (w == null) {
                s.msg.send(sender, "player.unknown", Msg.t("name", args[i]));
                return;
            }
        }
        String text = args[args.length - 1];
        boolean set = action.equals("setzen") || action.equals("set");
        long amount = set && text.equals("0") ? 0 : Money.parse(text);
        if (amount < 0) {
            s.msg.send(sender, "money.invalid");
            return;
        }
        s.bank.ensure(w.uuid(), w.name());
        Bank.Result r = switch (action) {
            case "geben", "give" -> s.bank.deposit(w.uuid(), amount, "admin", sender.getName());
            case "nehmen", "take" -> s.bank.withdraw(w.uuid(), amount, "admin", sender.getName());
            default -> s.bank.set(w.uuid(), amount, "admin");
        };
        if (r == Bank.Result.OK) {
            long balance = s.bank.balance(w.uuid());
            boolean self = sender instanceof Player p && p.getUniqueId().equals(w.uuid());
            if (self) {
                s.msg.send(sender, "admin.money.self", s.msg.money("balance", balance, sender));
            } else {
                s.msg.send(sender, "admin.money.done", Msg.t("name", w.name()), s.msg.money("balance", balance, sender));
                Player target = org.bukkit.Bukkit.getPlayer(w.uuid());
                if (target != null && (action.equals("geben") || action.equals("give"))) {
                    s.msg.send(target, "admin.money.got", s.msg.money("amount", amount, target),
                            s.msg.money("balance", balance, target));
                }
            }
        } else if (r == Bank.Result.NOT_ENOUGH) {
            s.msg.send(sender, "admin.money.not-enough", Msg.t("name", w.name()));
        } else {
            s.msg.send(sender, "admin.money.usage");
        }
    }

    private void build(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            s.msg.send(sender, "command.players-only");
            return;
        }
        if (s.building.remove(p.getUniqueId())) {
            s.msg.send(p, "admin.build.off");
        } else {
            s.building.add(p.getUniqueId());
            s.msg.send(p, "admin.build.on");
        }
    }

    private void town(CommandSender sender, String[] args) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("tore") || args[1].equalsIgnoreCase("gates"))) {
            for (de.moinmorni.smp.town.Layout.Gate g : s.town.layout().gates()) {
                s.msg.line(sender, "admin.town.gate", Msg.t("target", g.target()),
                        Msg.t("back", fmt(g.backX()) + " " + (s.town.ground() + 1) + " " + fmt(g.backZ())),
                        Msg.t("gate", (g.box().x1() + g.box().x2() + 1) / 2.0 + " " + (s.town.ground() + 1) + " "
                                + (g.box().z1() + g.box().z2() + 1) / 2.0));
            }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("neu")) {
            if (plugin.rebuildTown()) {
                s.msg.send(sender, "admin.town.rebuilding");
            } else {
                s.msg.send(sender, "admin.town.busy");
            }
            return;
        }
        Location sp = s.town.spawn();
        s.msg.send(sender, "admin.town.info", Msg.t("world", s.town.world() == null ? "?" : s.town.world().getName()),
                Msg.t("ready", s.town.ready()), Msg.t("y", s.town.ground()),
                Msg.t("stalls", s.town.layout().stalls().size()),
                Msg.t("spawn", sp == null ? "?" : sp.getBlockX() + " " + sp.getBlockY() + " " + sp.getBlockZ()));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private void stall(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("frei")) {
            s.msg.send(sender, "admin.stall.usage");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            s.msg.send(sender, "admin.stall.usage");
            return;
        }
        Optional<Stalls.Stall> st = s.stalls.get(id);
        if (st.isEmpty()) {
            s.msg.send(sender, "admin.stall.usage");
            return;
        }
        int n = st.get().tenant() == null ? 0 : plugin.stallUi().evacuate(st.get());
        s.stalls.free(id);
        s.boards.stallSigns();
        s.msg.send(sender, "admin.stall.freed", Msg.t("id", id), Msg.t("count", n));
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            s.msg.send(sender, "command.players-only");
            return;
        }
        Location l = p.getLocation();
        Claims.Claim c = s.claims.at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
        if (c == null) {
            s.msg.send(p, "claim.none-here");
            return;
        }
        s.claims.unclaim(c.world(), c.cx(), c.cz(), p.getUniqueId(), true);
        int shops = plugin.shopListener().removeInChunk(c.world(), c.cx(), c.cz());
        s.msg.send(p, "admin.claim.removed", Msg.t("owner", c.ownerName()), Msg.t("shops", shops));
    }

    /** Umsatz des Händlerhauses seit Mitternacht (Zeitzone des Servers) und die meistgehandelten Waren. */
    private void shop(CommandSender sender) {
        long since = s.now().toLocalDate().atStartOfDay(s.settings.zone).toInstant().toEpochMilli();
        ServerShop.Turnover t = s.shop.turnover(since);
        s.msg.send(sender, "admin.shop", s.msg.money("income", t.income(), sender), Msg.t("buys", t.purchases()),
                s.msg.money("payout", t.payout(), sender), Msg.t("sells", t.sales()),
                Msg.t("wares", s.shop.catalog().size()));
        for (ServerShop.Top top : t.top()) {
            s.msg.line(sender, "admin.shop.top", Msg.c("item", NoticeSender.itemName(top.key())),
                    Msg.t("bought", top.bought()), Msg.t("sold", top.sold()));
        }
    }

    private void info(CommandSender sender) {
        s.msg.send(sender, "admin.info", s.msg.money("total", s.bank.total(), sender),
                s.msg.money("escrow", s.orders.escrowTotal() + s.bounties.total(), sender),
                Msg.t("listings", s.ah.count(null, "")), Msg.t("orders", s.orders.count(null, "")),
                Msg.t("network", s.network.connected() ? (s.network.hubOnline() ? "online" : "offline") : "–"));
    }
}
