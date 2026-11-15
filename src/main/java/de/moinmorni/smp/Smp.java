package de.moinmorni.smp;

import de.moinmorni.smp.bounty.Bounties;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.combat.CombatTag;
import de.moinmorni.smp.db.Database;
import de.moinmorni.smp.db.Meta;
import de.moinmorni.smp.economy.Bank;
import de.moinmorni.smp.market.AuctionHouse;
import de.moinmorni.smp.market.Buyback;
import de.moinmorni.smp.market.Mailbox;
import de.moinmorni.smp.market.MarketStats;
import de.moinmorni.smp.market.Notices;
import de.moinmorni.smp.market.Orders;
import de.moinmorni.smp.market.PriceHistory;
import de.moinmorni.smp.market.Shops;
import de.moinmorni.smp.market.Stalls;
import de.moinmorni.smp.market.ui.Deliveries;
import de.moinmorni.smp.net.Network;
import de.moinmorni.smp.net.Reporter;
import de.moinmorni.smp.player.Players;
import de.moinmorni.smp.teleport.Teleports;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.town.Boards;
import de.moinmorni.smp.town.Town;
import de.moinmorni.smp.ui.ChatPrompt;
import de.moinmorni.smp.ui.Confirm;
import de.moinmorni.smp.ui.Picker;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    public de.moinmorni.smp.market.ui.NoticeSender noticeSender;

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
