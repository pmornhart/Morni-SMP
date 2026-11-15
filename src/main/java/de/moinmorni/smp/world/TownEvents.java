package de.moinmorni.smp.world;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.SmpPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Was in der Marktstadt auf Klick passiert: Schalter des Auktionshauses, Auftragsbrett, Ankauf, Abholfach und die
 * Marktstände öffnen ihre Menüs; wer ins Reisetor tritt, bekommt das Zufalls-Teleport-Menü. Wer ohne Bett stirbt,
 * wacht in der Stadt auf.
 */
public final class TownEvents implements Listener {

    private static final long GATE_PAUSE_MS = 2_500;

    private final Smp s;
    private final SmpPlugin plugin;
    private final Map<UUID, Long> gateSeen = new ConcurrentHashMap<>();

    public TownEvents(Smp s, SmpPlugin plugin) {
        this.s = s;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || !s.town.ready()) {
            return;
        }
        String what = s.town.trigger(b);
        if (what == null) {
            return;
        }
        Player p = e.getPlayer();
        if (s.bypass(p) && p.isSneaking()) {
            return; // Admins im Bau-Modus können den Block mit Schleichen normal benutzen
        }
        e.setCancelled(true);
        open(p, what);
    }

    /** Menü zu einem Auslöser öffnen. */
    public void open(Player p, String what) {
        if (s.combat.tagged(p) && !s.admin(p)) {
            s.msg.send(p, "combat.blocked", de.moinmorni.smp.text.Msg.t("seconds", s.combat.secondsLeft(p)));
            return;
        }
        switch (what) {
            case "ah" -> plugin.auctionUi().command(p, new String[0]);
            case "orders" -> plugin.orderUi().command(p, new String[0]);
            case "buyback" -> plugin.buybackUi().open(p);
            case "mail" -> plugin.mailboxUi().open(p, 0);
            default -> {
                if (what.startsWith("stall:")) {
                    try {
                        plugin.stallUi().menu(p, Integer.parseInt(what.substring(6)));
                    } catch (NumberFormatException ignored) {
                        // falscher Auslöser im Plan – nichts tun
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock() || !s.town.ready()) {
            return;
        }
        Player p = e.getPlayer();
        if (!s.town.inGate(e.getTo())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = gateSeen.get(p.getUniqueId());
        if (last != null && now - last < GATE_PAUSE_MS) {
            return;
        }
        gateSeen.put(p.getUniqueId(), now);
        // einen Schritt zurück, damit man nicht im Tor stehen bleibt
        org.bukkit.Location back = s.town.gateBack();
        back.setYaw(p.getLocation().getYaw());
        back.setPitch(p.getLocation().getPitch());
        p.teleport(back);
        plugin.rtp().open(p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!e.isBedSpawn() && !e.isAnchorSpawn() && s.town.ready()) {
            e.setRespawnLocation(s.town.spawn());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gateSeen.remove(e.getPlayer().getUniqueId());
    }
}
