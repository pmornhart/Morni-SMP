package net.morni.smp.command;

import java.util.List;
import java.util.UUID;
import net.morni.smp.Smp;
import net.morni.smp.economy.Bank;
import net.morni.smp.economy.Money;
import net.morni.smp.text.Msg;
import net.morni.smp.ui.Items;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /geld}, {@code /zahlen}, {@code /reichste}. */
public final class MoneyCommands {

    private final Smp s;

    public MoneyCommands(Smp s) {
        this.s = s;
    }

    /** Spieler (online oder schon mal da gewesen) zu einem Namen. */
    record Who(UUID uuid, String name, Player online) {
    }

    Who find(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return new Who(p.getUniqueId(), p.getName(), p);
        }
        UUID u = s.bank.byName(name);
        return u == null ? null : new Who(u, s.bank.name(u), null);
    }

    public Router.Handler balance() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                if (args.length == 0) {
                    if (!(sender instanceof Player p)) {
                        s.msg.send(sender, "command.players-only");
                        return;
                    }
                    s.msg.send(p, "money.balance", s.msg.money("balance", s.bank.balance(p.getUniqueId()), p));
                    return;
                }
                Who w = find(args[0]);
                if (w == null) {
                    s.msg.send(sender, "player.unknown", Msg.t("name", args[0]));
                    return;
                }
                s.msg.send(sender, "money.balance.other", Msg.t("name", w.name()),
                        s.msg.money("balance", s.bank.balance(w.uuid()), sender));
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 ? Router.online() : List.of();
            }

            @Override
            public boolean playersOnly() {
                return false;
            }
        };
    }

    public Router.Handler pay() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player p = (Player) sender;
                if (args.length < 2) {
                    s.msg.send(p, "pay.usage");
                    return;
                }
                Who w = find(args[0]);
                if (w == null) {
                    s.msg.send(p, "player.unknown", Msg.t("name", args[0]));
                    return;
                }
                long amount = Money.parse(String.join("", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (amount <= 0) {
                    s.msg.send(p, "money.invalid");
                    return;
                }
                if (w.uuid().equals(p.getUniqueId())) {
                    s.msg.send(p, "pay.self");
                    return;
                }
                if (amount >= s.settings.payConfirmAbove && s.settings.payConfirmAbove > 0) {
                    String lang = s.msg.lang(p);
                    s.confirm.open(p, s.msg.text(lang, "pay.confirm.title"),
                            Items.head(Bukkit.getOfflinePlayer(w.uuid()), s.msg.text(lang, "pay.confirm.to",
                                    Msg.t("name", w.name())), List.of()),
                            List.of(s.msg.text(lang, "pay.confirm.amount", Msg.money("amount", amount, lang))),
                            s.msg.text(lang, "pay.confirm.yes", Msg.money("amount", amount, lang)),
                            () -> transfer(p, w, amount), null);
                    return;
                }
                transfer(p, w, amount);
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 ? Router.online() : List.of();
            }
        };
    }

    private void transfer(Player p, Who w, long amount) {
        Bank.Result r = s.bank.pay(p.getUniqueId(), w.uuid(), amount, "zahlung", p.getName() + "→" + w.name());
        String lang = s.msg.lang(p);
        switch (r) {
            case OK -> {
                s.msg.send(p, "pay.sent", Msg.money("amount", amount, lang), Msg.t("name", w.name()),
                        Msg.money("balance", s.bank.balance(p.getUniqueId()), lang));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.3f);
                Player to = Bukkit.getPlayer(w.uuid());
                if (to != null) {
                    s.msg.send(to, "pay.received", Msg.money("amount", amount, s.msg.lang(to)),
                            Msg.t("name", p.getName()));
                    to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                } else {
                    s.notices.add(w.uuid(), "pay.received", "paid", Long.toString(amount), "name", p.getName());
                }
            }
            case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", Msg.money("missing",
                    amount - s.bank.balance(p.getUniqueId()), lang));
            case SELF -> s.msg.send(p, "pay.self");
            default -> s.msg.send(p, "money.invalid");
        }
    }

    public Router.Handler top() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                List<Bank.Rich> list = s.bank.top(10);
                s.msg.send(sender, "rich.title");
                int i = 1;
                for (Bank.Rich r : list) {
                    s.msg.line(sender, "rich.line", Msg.t("rank", i++), Msg.t("name", r.name()),
                            s.msg.money("balance", r.balance(), sender));
                }
                if (list.isEmpty()) {
                    s.msg.line(sender, "rich.empty");
                }
            }

            @Override
            public boolean playersOnly() {
                return false;
            }
        };
    }

}
