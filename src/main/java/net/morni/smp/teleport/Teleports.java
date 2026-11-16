package net.morni.smp.teleport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.morni.smp.Smp;
import net.morni.smp.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Teleports mit Wartezeit: ein Balken läuft {@code teleportDelaySeconds} Sekunden herunter, wer sich bewegt, Schaden
 * nimmt oder in einen Kampf gerät, bricht ab. Admins (im Admin-Modus) dürfen sofort. Vor jedem Teleport merkt sich
 * MorniSMP den alten Ort für {@code /back} (auch den Ort des Todes).
 */
public final class Teleports implements Listener {

    private static final double MOVE_TOLERANCE_SQ = 0.6 * 0.6;

    private record Pending(Location target, Location start, BossBar bar, BukkitTask task) {
    }

    /** Eine Teleport-Anfrage (/tpa: zu {@code to}; /tpahere: {@code to} soll zu {@code from}). */
    public record Request(UUID from, String fromName, boolean here, long expires) {
    }

    private final Smp s;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();
    /** Ziel der Anfrage → Anfragen nach Absender. */
    private final Map<UUID, Map<UUID, Request>> requests = new ConcurrentHashMap<>();

    public Teleports(Smp s) {
        this.s = s;
    }

    /** Teleport mit Wartezeit. {@code placeKey}: Text-Schlüssel für das Ziel („zum Spawn“ …). */
    public void start(Player p, Location target, String placeKey, Runnable after) {
        if (target == null || target.getWorld() == null) {
            s.msg.send(p, "teleport.nowhere");
            return;
        }
        if (s.combat.tagged(p) && !s.admin(p)) {
            s.msg.send(p, "combat.blocked", Msg.t("seconds", s.combat.secondsLeft(p)));
            return;
        }
        cancel(p, null);
        int seconds = s.settings.teleportDelaySeconds;
        if (seconds <= 0 || s.settings.adminsInstant && s.admin(p)) {
            go(p, target, placeKey, after);
            return;
        }
        String lang = s.msg.lang(p);
        Component place = s.msg.text(lang, placeKey);
        BossBar bar = BossBar.bossBar(s.msg.text(lang, "teleport.bar", Msg.c("place", place), Msg.t("seconds", seconds)),
                1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        s.msg.bar(p, "teleport.dont-move");
        long started = System.currentTimeMillis();
        long total = seconds * 1000L;
        UUID id = p.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(s.plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            Pending pe = pending.get(id);
            if (pl == null || pe == null) {
                return;
            }
            long left = total - (System.currentTimeMillis() - started);
            if (left <= 0) {
                finish(pl);
                go(pl, target, placeKey, after);
                return;
            }
            bar.progress(Math.max(0f, Math.min(1f, left / (float) total)));
            bar.name(s.msg.text(lang, "teleport.bar", Msg.c("place", place), Msg.t("seconds", (left + 999) / 1000)));
        }, 2L, 2L);
        pending.put(id, new Pending(target, p.getLocation(), bar, task));
    }

    /** Teleport sofort (nach der Wartezeit oder für Admins). */
    private void go(Player p, Location target, String placeKey, Runnable after) {
        remember(p);
        p.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                s.msg.bar(p, "teleport.done", Msg.c("place", s.msg.text(p, placeKey)));
                if (after != null) {
                    after.run();
                }
            } else {
                s.msg.send(p, "teleport.failed");
            }
        });
    }

    /** Ort für /back merken. */
    public void remember(Player p) {
        back.put(p.getUniqueId(), p.getLocation());
    }

    public Location back(Player p) {
        return back.get(p.getUniqueId());
    }

    private void finish(Player p) {
        Pending pe = pending.remove(p.getUniqueId());
        if (pe != null) {
            pe.task().cancel();
            p.hideBossBar(pe.bar());
        }
    }

    /** Laufenden Teleport abbrechen (mit Hinweis {@code reasonKey}, null = still). */
    public void cancel(Player p, String reasonKey) {
        if (pending.containsKey(p.getUniqueId())) {
            finish(p);
            if (reasonKey != null) {
                s.msg.send(p, reasonKey);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
            }
        }
    }

    public boolean waiting(Player p) {
        return pending.containsKey(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Pending pe = pending.get(e.getPlayer().getUniqueId());
        if (pe == null || !e.hasChangedPosition()) {
            return;
        }
        Location to = e.getTo();
        if (!to.getWorld().equals(pe.start().getWorld()) || to.distanceSquared(pe.start()) > MOVE_TOLERANCE_SQ) {
            cancel(e.getPlayer(), "teleport.cancel.moved");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getFinalDamage() > 0) {
            cancel(p, "teleport.cancel.damage");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        cancel(e.getEntity(), null);
        back.put(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        finish(e.getPlayer());
        requests.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- Anfragen (/tpa)

    /** Anfrage stellen. Liefert false, wenn schon eine offen ist. */
    public boolean request(Player from, Player to, boolean here) {
        Map<UUID, Request> forTarget = requests.computeIfAbsent(to.getUniqueId(), k -> new ConcurrentHashMap<>());
        Request old = forTarget.get(from.getUniqueId());
        if (old != null && old.expires() > System.currentTimeMillis()) {
            return false;
        }
        forTarget.put(from.getUniqueId(), new Request(from.getUniqueId(), from.getName(), here,
                System.currentTimeMillis() + 60_000));
        return true;
    }

    /** Anfrage an {@code to} herausnehmen: von {@code fromName} oder die neueste (null). */
    public Request take(Player to, String fromName) {
        Map<UUID, Request> forTarget = requests.get(to.getUniqueId());
        if (forTarget == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        forTarget.values().removeIf(r -> r.expires() <= now);
        Request best = null;
        for (Request r : forTarget.values()) {
            if (fromName != null && !r.fromName().equalsIgnoreCase(fromName)) {
                continue;
            }
            if (best == null || r.expires() > best.expires()) {
                best = r;
            }
        }
        if (best != null) {
            forTarget.remove(best.from());
        }
        return best;
    }
}
