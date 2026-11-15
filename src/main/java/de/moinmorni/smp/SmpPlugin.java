package de.moinmorni.smp;

import de.moinmorni.smp.bounty.Bounties;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.combat.CombatTag;
import de.moinmorni.smp.command.AdminCommand;
import de.moinmorni.smp.command.ClaimCommand;
import de.moinmorni.smp.command.MiscCommands;
import de.moinmorni.smp.command.MoneyCommands;
import de.moinmorni.smp.command.Router;
import de.moinmorni.smp.command.TravelCommands;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Meta;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.hud.Sidebar;
import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.Buyback;
import de.moinmorni.smp.market.Mailbox;
import de.moinmorni.smp.market.MarketStats;
import de.moinmorni.smp.market.Notices;
import de.moinmorni.smp.market.Orders;
import de.moinmorni.smp.market.PriceHistory;
import de.moinmorni.smp.market.Shops;
import de.moinmorni.smp.market.Stalls;
import de.moinmorni.smp.market.ui.AuctionUi;
import de.moinmorni.smp.market.ui.BuybackUi;
import de.moinmorni.smp.market.ui.Deliveries;
import de.moinmorni.smp.market.ui.MailboxUi;
import de.moinmorni.smp.market.ui.NoticeSender;
import de.moinmorni.smp.market.ui.OrderUi;
import de.moinmorni.smp.market.ui.ShopListener;
import de.moinmorni.smp.market.ui.StallUi;
import de.moinmorni.smp.net.Network;
import de.moinmorni.smp.net.Reporter;
import de.moinmorni.smp.player.PlayerEvents;
import de.moinmorni.smp.player.Players;
import de.moinmorni.smp.teleport.RandomTeleport;
import de.moinmorni.smp.teleport.Teleports;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.town.Boards;
import de.moinmorni.smp.town.Town;
import de.moinmorni.smp.town.TownBuilder;
import de.moinmorni.smp.town.TownPlan;
import de.moinmorni.smp.ui.ChatPrompt;
import de.moinmorni.smp.ui.Confirm;
import de.moinmorni.smp.ui.Menus;
import de.moinmorni.smp.ui.Picker;
import de.moinmorni.smp.world.Protection;
import de.moinmorni.smp.world.TownEvents;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MorniSMP: Überleben mit freiem Markt. Startet alle Bausteine (Datenbank, Geld, Markt, Grundstücke, Stadt …),
 * meldet die Befehle an und lässt die regelmäßigen Aufgaben laufen.
 */
public final class SmpPlugin extends JavaPlugin {

    private static final long SECOND = 20L;
    private static final long MINUTE = 60 * SECOND;

    private final Smp s = new Smp();
    private TownPlan.Result plan;
    private TownBuilder builder;
    private AuctionUi auctionUi;
    private OrderUi orderUi;
    private MailboxUi mailboxUi;
    private BuybackUi buybackUi;
    private StallUi stallUi;
    private ShopListener shopListener;
    private RandomTeleport rtp;
    private Sidebar sidebar;
    private volatile boolean stopping;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LongSupplier clock = System::currentTimeMillis;
        s.plugin = this;
        s.settings = Settings.load(getConfig());
        s.network = Network.connect(this);
        s.msg = new Msg(s.network);
        try {
            s.db = Database.open(getDataFolder().toPath().resolve("mornismp.db"));
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Datenbank geht nicht auf – MorniSMP bleibt aus.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        s.meta = new Meta(s.db);
        s.bank = new Bank(s.db, clock, s.settings.startBalance);
        s.mailbox = new Mailbox(s.db, clock);
        s.notices = new Notices(s.db, clock);
        s.prices = new PriceHistory(s.db, clock);
        s.stats = new MarketStats(s.db);
        s.ah = new AuctionHouse(s.db, s.bank, s.mailbox, s.notices, s.prices, s.stats, clock, s.settings.ahFeeBp,
                s.settings.ahDurationMs, s.settings.ahMaxListings);
        s.orders = new Orders(s.db, s.bank, s.mailbox, s.notices, s.prices, s.stats, clock, s.settings.orderFeeBp,
                s.settings.orderDurationMs, s.settings.orderMaxActive);
        s.buyback = new Buyback(s.db, s.bank);
        s.buyback.configure(s.settings.buyback, s.settings.buybackTiers);
        s.stalls = new Stalls(s.db, s.bank, clock, s.settings.stallRent);
        s.stalls.configure(s.settings.stallRent, Stalls.WEEK_MS, s.settings.stallWarnMs, s.settings.stallMaxWeeks);
        s.shops = new Shops(s.db, s.bank, s.notices, s.prices, s.stats, clock, s.settings.shopFeeBp);
        s.claims = new Claims(s.db, s.bank, clock);
        s.players = new Players(s.db, clock);
        s.bounties = new Bounties(s.db, s.bank, clock);
        plan = TownPlan.create();
        s.town = new Town(plan.layout());
        s.stalls.ensure(plan.layout().stalls().size());
        s.boards = new Boards(s.town, s.bank, s.stats, s.stalls);
        s.deliveries = new Deliveries(s.mailbox, s.msg);
        s.prompt = new ChatPrompt(this);
        s.picker = new Picker(s.msg, s.prompt);
        s.confirm = new Confirm(s.msg);
        s.teleports = new Teleports(s);
        s.combat = new CombatTag(s);
        s.reporter = new Reporter(s.network, s.db);
        s.noticeSender = new NoticeSender(s.notices, s.msg);

        auctionUi = new AuctionUi(s);
        orderUi = new OrderUi(s);
        mailboxUi = new MailboxUi(s);
        buybackUi = new BuybackUi(s);
        stallUi = new StallUi(s);
        shopListener = new ShopListener(s);
        rtp = new RandomTeleport(s);
        sidebar = new Sidebar(s);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new Menus(this), this);
        pm.registerEvents(s.prompt, this);
        pm.registerEvents(shopListener, this);
        pm.registerEvents(new Protection(s, stallUi), this);
        pm.registerEvents(new TownEvents(s, this), this);
        pm.registerEvents(new PlayerEvents(s, this), this);
        pm.registerEvents(s.teleports, this);
        pm.registerEvents(s.combat, this);

        commands();
        worlds();
        buildTown(false);

        getServer().getScheduler().runTaskTimer(this, s.combat::tick, SECOND, SECOND);
        getServer().getScheduler().runTaskTimer(this, sidebar::tick, 2 * SECOND, 2 * SECOND);
        getServer().getScheduler().runTaskTimer(this, this::everyMinute, MINUTE, MINUTE);
        getServer().getScheduler().runTaskTimer(this, this::every30s, 10 * SECOND, 30 * SECOND);
        for (Player p : Bukkit.getOnlinePlayers()) {
            s.players.join(p.getUniqueId(), p.getName());
            s.bank.ensure(p.getUniqueId(), p.getName());
            sidebar.show(p);
        }
        getLogger().info("MorniSMP läuft" + (s.network.connected() ? " im MoinMorni-Netzwerk." : " allein (ohne Netzwerk)."));
    }

    @Override
    public void onDisable() {
        stopping = true;
        if (s.db == null) {
            return;
        }
        try {
            if (s.reporter != null) {
                s.reporter.flush();
            }
            s.network.state("closed", 0);
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Abschluss-Meldung ans Netzwerk ging nicht", e);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sidebar != null) {
                sidebar.hide(p);
            }
            p.closeInventory();
        }
        s.db.close();
    }

    private void commands() {
        Router r = new Router(s);
        MoneyCommands money = new MoneyCommands(s);
        TravelCommands travel = new TravelCommands(s, rtp);
        MiscCommands misc = new MiscCommands(s, this, money);
        r.add(this, "geld", money.balance());
        r.add(this, "zahlen", money.pay());
        r.add(this, "reichste", money.top());
        r.add(this, "ah", (sender, args) -> auctionUi.command((Player) sender, args));
        r.add(this, "abholen", (sender, args) -> mailboxUi.open((Player) sender, 0));
        r.add(this, "auftraege", (sender, args) -> orderUi.command((Player) sender, args));
        r.add(this, "verkaufen", (sender, args) -> buybackUi.open((Player) sender));
        r.add(this, "stand", (sender, args) -> stallUi.command((Player) sender, args));
        r.add(this, "gs", new ClaimCommand(s, this));
        r.add(this, "rtp", travel.rtp());
        r.add(this, "spawn", travel.spawn());
        r.add(this, "back", travel.back());
        r.add(this, "home", travel.home());
        r.add(this, "sethome", travel.setHome());
        r.add(this, "delhome", travel.delHome());
        r.add(this, "homes", travel.homes());
        r.add(this, "tpa", travel.tpa(false));
        r.add(this, "tpahere", travel.tpa(true));
        r.add(this, "tpaccept", travel.accept(true));
        r.add(this, "tpdeny", travel.accept(false));
        r.add(this, "kopfgeld", misc.bounty());
        r.add(this, "belohnung", misc.reward());
        r.add(this, "leiste", misc.sidebar());
        r.add(this, "mornismp", misc.help());
        r.add(this, "smpadmin", new AdminCommand(s, this, money));
    }

    /** Weltgrenzen um 0/0 (Nether kleiner, wie üblich). */
    private void worlds() {
        for (World w : Bukkit.getWorlds()) {
            int radius = switch (w.getEnvironment()) {
                case NETHER -> s.settings.borderRadiusNether;
                case THE_END -> s.settings.borderRadiusEnd;
                default -> s.settings.borderRadius;
            };
            WorldBorder b = w.getWorldBorder();
            b.setCenter(0, 0);
            b.setSize(radius * 2.0);
            b.setWarningDistance(16);
        }
    }

    private World mainWorld() {
        World w = Bukkit.getWorld(s.settings.world);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    private void buildTown(boolean force) {
        if (builder == null) {
            builder = new TownBuilder(this, s.town, plan, s.meta);
        }
        builder.ensure(mainWorld(), force, () -> {
            // vor einem Neubau: Stände räumen (Kisten ins Abholfach), Miete läuft weiter
            for (Stalls.Stall st : s.stalls.all()) {
                if (st.tenant() != null) {
                    stallUi.evacuate(st);
                }
            }
        }, () -> {
            s.boards.stallSigns();
            s.boards.refresh();
            reportState();
        });
    }

    /** {@code /smpadmin stadt neu}. Liefert false, wenn gerade gebaut wird. */
    public boolean rebuildTown() {
        if (builder != null && builder.running()) {
            return false;
        }
        buildTown(true);
        return true;
    }

    /** {@code /smpadmin neuladen}: config.yml neu lesen. */
    public void reloadSettings() {
        reloadConfig();
        s.settings = Settings.load(getConfig());
        s.bank.startBalance(s.settings.startBalance);
        s.ah.configure(s.settings.ahFeeBp, s.settings.ahDurationMs, s.settings.ahMaxListings);
        s.orders.configure(s.settings.orderFeeBp, s.settings.orderDurationMs, s.settings.orderMaxActive);
        s.shops.configure(s.settings.shopFeeBp);
        s.stalls.configure(s.settings.stallRent, Stalls.WEEK_MS, s.settings.stallWarnMs, s.settings.stallMaxWeeks);
        s.buyback.configure(s.settings.buyback, s.settings.buybackTiers);
        worlds();
        s.boards.stallSigns();
    }

    private void everyMinute() {
        String today = s.today();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            s.players.addPlaytime(id, 60, today);
            s.reporter.playtime(id, 1);
        }
        for (UUID seller : s.ah.expire()) {
            s.noticeSender.flush(seller);
        }
        for (UUID owner : s.orders.expire()) {
            s.noticeSender.flush(owner);
        }
        stallUi.tick();
    }

    private void every30s() {
        s.boards.refresh();
        try {
            s.reporter.flush();
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "Statistik ans Netzwerk ging nicht", e);
        }
        reportState();
    }

    /** Zustand fürs Netzwerk (Lobby-Figur, Webseite). */
    public void reportState() {
        int online = Bukkit.getOnlinePlayers().size();
        s.network.state(s.town.ready() ? "running" : "starting", online);
    }

    public boolean stopping() {
        return stopping;
    }

    public AuctionUi auctionUi() {
        return auctionUi;
    }

    public OrderUi orderUi() {
        return orderUi;
    }

    public MailboxUi mailboxUi() {
        return mailboxUi;
    }

    public BuybackUi buybackUi() {
        return buybackUi;
    }

    public StallUi stallUi() {
        return stallUi;
    }

    public ShopListener shopListener() {
        return shopListener;
    }

    public RandomTeleport rtp() {
        return rtp;
    }

    public Sidebar sidebar() {
        return sidebar;
    }
}
