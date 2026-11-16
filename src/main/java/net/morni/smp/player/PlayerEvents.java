package net.morni.smp.player;

import net.morni.smp.Smp;
import net.morni.smp.SmpPlugin;
import net.morni.smp.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Betreten und Verlassen: Konto und Spielerdaten anlegen, Neue in die Marktstadt stellen und begrüßen, gespeicherte
 * Nachrichten und Hinweise aufs Abholfach zeigen, Kopfgeld beim Tod auszahlen, besuchte Dimensionen merken.
 */
public final class PlayerEvents implements Listener {

    private final Smp s;
    private final SmpPlugin plugin;

    public PlayerEvents(Smp s, SmpPlugin plugin) {
        this.s = s;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = s.players.join(p.getUniqueId(), p.getName());
        boolean newAccount = !s.bank.exists(p.getUniqueId());
        s.bank.ensure(p.getUniqueId(), p.getName());
        if (p.getWorld().getEnvironment() == World.Environment.NETHER) {
            s.players.visited(p.getUniqueId(), "nether");
        } else if (p.getWorld().getEnvironment() == World.Environment.THE_END) {
            s.players.visited(p.getUniqueId(), "end");
        }
        if (first && s.town.ready()) {
            p.teleport(s.town.spawn());
        }
        Bukkit.getScheduler().runTaskLater(s.plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (first) {
                s.msg.title(p, "welcome.title", "welcome.subtitle");
                s.msg.send(p, "welcome.first", Msg.money("money", newAccount ? s.settings.startBalance : 0, s.msg.lang(p)));
                s.msg.line(p, "welcome.help");
            } else {
                s.msg.send(p, "welcome.back", Msg.money("balance", s.bank.balance(p.getUniqueId()), s.msg.lang(p)));
            }
            s.noticeSender.flush(p);
            int mail = s.mailbox.count(p.getUniqueId());
            if (mail > 0) {
                s.msg.send(p, "mail.reminder", Msg.t("count", mail));
            }
        }, 30L);
        plugin.sidebar().show(p);
        plugin.reportState();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.sidebar().hide(e.getPlayer());
        Bukkit.getScheduler().runTask(s.plugin, plugin::reportState);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        long amount = s.bounties.claim(victim.getUniqueId(), killer.getUniqueId());
        if (amount > 0) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                s.msg.send(o, "bounty.claimed", Msg.t("killer", killer.getName()), Msg.t("target", victim.getName()),
                        Msg.money("amount", amount, s.msg.lang(o)));
            }
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        World.Environment env = e.getPlayer().getWorld().getEnvironment();
        if (env == World.Environment.NETHER) {
            s.players.visited(e.getPlayer().getUniqueId(), "nether");
        } else if (env == World.Environment.THE_END) {
            s.players.visited(e.getPlayer().getUniqueId(), "end");
        }
    }
}
