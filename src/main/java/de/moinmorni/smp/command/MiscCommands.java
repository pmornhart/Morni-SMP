package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.SmpPlugin;
import de.moinmorni.smp.bounty.Bounties;
import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.player.DailyReward;
import de.moinmorni.smp.text.Msg;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /kopfgeld}, {@code /belohnung}, {@code /leiste}, {@code /mornismp}. */
public final class MiscCommands {

    private final Smp s;
    private final SmpPlugin plugin;
    private final MoneyCommands money;

    public MiscCommands(Smp s, SmpPlugin plugin, MoneyCommands money) {
        this.s = s;
        this.plugin = plugin;
        this.money = money;
    }

    public Router.Handler bounty() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                Player p = (Player) sender;
                if (args.length < 2) {
                    List<Bounties.Bounty> top = s.bounties.top(10);
                    s.msg.send(p, "bounty.title");
                    for (Bounties.Bounty b : top) {
                        s.msg.line(p, "bounty.line", Msg.t("name", b.targetName()), s.msg.money("amount", b.amount(), p));
                    }
                    if (top.isEmpty()) {
                        s.msg.line(p, "bounty.empty");
                    }
                    s.msg.line(p, "bounty.usage", s.msg.money("min", s.settings.bountyMinimum, p));
                    return;
                }
                MoneyCommands.Who w = money.find(args[0]);
                if (w == null) {
                    s.msg.send(p, "player.unknown", Msg.t("name", args[0]));
                    return;
                }
                long amount = Money.parse(String.join("", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (amount <= 0) {
                    s.msg.send(p, "money.invalid");
                    return;
                }
                switch (s.bounties.place(p.getUniqueId(), w.uuid(), w.name(), amount, s.settings.bountyMinimum)) {
                    case OK -> {
                        long total = s.bounties.of(w.uuid());
                        for (Player o : Bukkit.getOnlinePlayers()) {
                            s.msg.send(o, "bounty.placed", Msg.t("name", p.getName()), Msg.t("target", w.name()),
                                    s.msg.money("amount", amount, o), s.msg.money("total", total, o));
                        }
                    }
                    case SELF -> s.msg.send(p, "bounty.self");
                    case TOO_LOW -> s.msg.send(p, "bounty.too-low", s.msg.money("min", s.settings.bountyMinimum, p));
                    case NOT_ENOUGH -> s.msg.send(p, "money.not-enough", s.msg.money("missing",
                            amount - s.bank.balance(p.getUniqueId()), p));
                }
            }

            @Override
            public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 ? Router.online() : List.of();
            }
        };
    }

    public Router.Handler reward() {
        return (sender, args) -> {
            Player p = (Player) sender;
            DailyReward.Outcome o = DailyReward.claim(s.players, s.bank, p.getUniqueId(), s.today(), s.yesterday(),
                    s.settings.rewardMinMinutes * 60L, s.settings.rewardBase, s.settings.rewardPerDay,
                    s.settings.rewardMaxStreak);
            String lang = s.msg.lang(p);
            switch (o.status()) {
                case ALREADY -> s.msg.send(p, "reward.already", Msg.t("streak", o.streak()));
                case TOO_EARLY -> s.msg.send(p, "reward.too-early", Msg.t("time",
                        Msg.duration(o.missingSeconds() * 1000, lang)), Msg.t("minutes", s.settings.rewardMinMinutes));
                case OK -> {
                    List<String> items = o.streak() >= 7 && !s.settings.rewardWeekItems.isEmpty()
                            ? s.settings.rewardWeekItems : s.settings.rewardItems;
                    for (String spec : items) {
                        ItemStack it = parseItem(spec);
                        if (it != null) {
                            s.deliveries.giveOrMail(p, it, "belohnung");
                        }
                    }
                    s.msg.send(p, "reward.ok", Msg.money("money", o.money(), lang), Msg.t("streak", o.streak()),
                            Msg.t("max", s.settings.rewardMaxStreak));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
                }
            }
        };
    }

    /** „bread:8“ oder „minecraft:bread:8“ oder „bread“. */
    static ItemStack parseItem(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String v = spec.strip().toLowerCase(Locale.ROOT);
        int amount = 1;
        int colon = v.lastIndexOf(':');
        if (colon > 0 && v.substring(colon + 1).matches("\\d{1,3}")) {
            amount = Integer.parseInt(v.substring(colon + 1));
            v = v.substring(0, colon);
        }
        Material m = Material.matchMaterial(v);
        if (m == null || !m.isItem() || amount <= 0) {
            return null;
        }
        return new ItemStack(m, Math.min(amount, m.getMaxStackSize()));
    }

    public Router.Handler sidebar() {
        return (sender, args) -> {
            Player p = (Player) sender;
            boolean on = plugin.sidebar().toggle(p);
            s.msg.send(p, on ? "sidebar.on" : "sidebar.off");
        };
    }

    /** {@code /mornismp}: kurze Hilfe mit allen Befehlen. */
    public Router.Handler help() {
        return new Router.Handler() {
            @Override
            public void run(CommandSender sender, String[] args) {
                s.msg.send(sender, "help.title");
                for (String k : new String[] {"money", "market", "orders", "buyback", "stall", "shop", "claims",
                        "travel", "homes", "tpa", "bounty", "reward", "sidebar"}) {
                    s.msg.line(sender, "help." + k);
                }
            }

            @Override
            public boolean playersOnly() {
                return false;
            }
        };
    }
}
