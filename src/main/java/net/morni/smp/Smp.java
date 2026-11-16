package net.morni.smp;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.morni.smp.bounty.Bounties;
import net.morni.smp.claims.Claims;
import net.morni.smp.combat.CombatTag;
import net.morni.smp.db.Database;
import net.morni.smp.db.Meta;
import net.morni.smp.economy.Bank;
import net.morni.smp.market.AuctionHouse;
import net.morni.smp.market.Buyback;
import net.morni.smp.market.Mailbox;
import net.morni.smp.market.MarketStats;
import net.morni.smp.market.Notices;
import net.morni.smp.market.Orders;
import net.morni.smp.market.PriceHistory;
import net.morni.smp.market.Shops;
import net.morni.smp.market.Stalls;
import net.morni.smp.market.ui.Deliveries;
import net.morni.smp.net.Network;
import net.morni.smp.net.Reporter;
import net.morni.smp.player.Players;
import net.morni.smp.teleport.Teleports;
import net.morni.smp.text.Msg;
import net.morni.smp.town.Boards;
import net.morni.smp.town.Town;
import net.morni.smp.ui.ChatPrompt;
import net.morni.smp.ui.Confirm;
import net.morni.smp.ui.Picker;
import org.bukkit.entity.Player;

/** Alle Dienste an einem Ort – jeder Baustein bekommt diesen Kasten statt zehn einzelner Teile. */
public final class Smp {

    public SmpPlugin plugin;
    public Settings settings;
    public Network network;
    public Msg msg;
    public Database db;
    public Meta meta;
    public Bank bank;
    public Mailbox mailbox;
    public Notices notices;
    public PriceHistory prices;
    public MarketStats stats;
    public AuctionHouse ah;
    public Orders orders;
    public Buyback buyback;
    public net.morni.smp.shop.ServerShop shop;
    public Stalls stalls;
    public Shops shops;
    public Claims claims;
    public Players players;
    public Bounties bounties;
    public Town town;
    public Boards boards;
    public Deliveries deliveries;
    public ChatPrompt prompt;
    public Picker picker;
    public Confirm confirm;
    public Teleports teleports;
    public CombatTag combat;
    public Reporter reporter;
    public net.morni.smp.market.ui.NoticeSender noticeSender;

    /** Admins im Bau-Modus (dürfen in Stadt und fremden Grundstücken bauen). */
    public final Set<UUID> building = new HashSet<>();

    /** Darf verwalten (OP oder Recht morni.smp.admin)? */
    public boolean admin(Player p) {
        return p.isOp() || p.hasPermission("morni.smp.admin");
    }

    /** Admin und gerade im Bau-Modus? */
    public boolean bypass(Player p) {
        return building.contains(p.getUniqueId()) && admin(p);
    }

    /** Heutiger Tag in der Zeitzone des Servers (für Tageszähler). */
    public String today() {
        return LocalDate.now(settings.zone).toString();
    }

    public String yesterday() {
        return LocalDate.now(settings.zone).minusDays(1).toString();
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(settings.zone);
    }
}
