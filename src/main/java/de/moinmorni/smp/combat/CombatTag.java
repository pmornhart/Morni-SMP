package de.moinmorni.smp.combat;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.text.Msg;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Kampf-Markierung: Wer einen anderen Spieler trifft (oder getroffen wird), ist {@code combatSeconds} Sekunden „im
 * Kampf“. Solange gibt es keine Teleports (Befehle sind gesperrt, laufende Wartezeiten brechen ab). Wer im Kampf das
 * Spiel verlässt, stirbt – die Sachen fallen an Ort und Stelle, ein Kopfgeld geht an den letzten Gegner.
 */
public final class CombatTag implements Listener {

    /** Diese Befehle sind im Kampf gesperrt (alles, was wegbringt). */
    private static final Set<String> BLOCKED = Set.of("spawn", "stadt", "home", "zuhause", "tpa", "tpahere",
            "tpaccept", "tpannehmen", "back", "zurueck", "zurück", "rtp", "zufall", "wild", "reisetor", "lobby", "hub",
            "server", "ah", "auktionshaus", "auftraege", "aufträge", "verkaufen", "ankauf", "abholen", "shop", "haendler", "händler",
            "kaufhaus");

    private final Smp s;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastEnemy = new ConcurrentHashMap<>();

    public CombatTag(Smp s) {
        this.s = s;
    }

    public boolean tagged(Player p) {
        Long u = until.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    /** Restliche Sekunden im Kampf (0 = nicht im Kampf). */
    public int secondsLeft(Player p) {
        Long u = until.get(p.getUniqueId());
        if (u == null) {
            return 0;
        }
        long left = u - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** Wer hat bei einem Treffer angegriffen? Spieler direkt, per Pfeil/Dreizack/Trank oder gezündetem TNT. */
    public static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (s.settings.combatSeconds <= 0 || !(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(attacker, victim);
        tag(victim, attacker);
    }

    private void tag(Player p, Player enemy) {
        boolean fresh = !tagged(p);
        until.put(p.getUniqueId(), System.currentTimeMillis() + s.settings.combatSeconds * 1000L);
        lastEnemy.put(p.getUniqueId(), enemy.getUniqueId());
        s.teleports.cancel(p, "teleport.cancel.combat");
        if (fresh) {
            s.msg.send(p, "combat.tagged", Msg.t("seconds", s.settings.combatSeconds));
        }
    }

    /** Jede Sekunde: Aktionsleiste, Ende melden. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = until.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (en.getValue() <= now) {
                it.remove();
                lastEnemy.remove(en.getKey());
                s.msg.bar(p, "combat.over");
            } else {
                s.msg.bar(p, "combat.bar", Msg.t("seconds", secondsLeft(p)));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!tagged(e.getPlayer()) || s.admin(e.getPlayer())) {
            return;
        }
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (BLOCKED.contains(label)) {
            e.setCancelled(true);
            s.msg.send(e.getPlayer(), "combat.blocked", Msg.t("seconds", secondsLeft(e.getPlayer())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        until.remove(e.getEntity().getUniqueId());
        lastEnemy.remove(e.getEntity().getUniqueId());
    }

    /** Rauswurf durch das Team zählt nicht als Flucht. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        until.remove(e.getPlayer().getUniqueId());
        lastEnemy.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (!tagged(p) || s.plugin.stopping()) {
            until.remove(p.getUniqueId());
            return;
        }
        UUID enemy = lastEnemy.get(p.getUniqueId());
        Player killer = enemy == null ? null : Bukkit.getPlayer(enemy);
        if (killer != null) {
            p.setKiller(killer);
        }
        p.setHealth(0);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(p)) {
                s.msg.send(o, "combat.logout", Msg.t("name", p.getName()));
            }
        }
    }
}
