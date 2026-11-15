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
 * Marktstände öffnen ihre Menüs; wer durch eines der drei Reisetore geht, landet per Zufalls-Teleport in Oberwelt,
 * Nether oder End. Wer ohne Bett stirbt oder von der Insel fällt, ist wieder in der Stadt.
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
        if (!s.town.isSpawnWorld(e.getTo().getWorld())) {
            return;
        }
        // Von der Insel gefallen: zurück auf den Marktplatz (kein Tod in der Leere)
        if (e.getTo().getY() < s.town.ground() - 48) {
            p.setFallDistance(0);
            p.teleport(s.town.spawn());
            s.msg.bar(p, "spawn.fell");
            return;
        }
        de.moinmorni.smp.town.Layout.Gate gate = s.town.gateAt(e.getTo());
        if (gate == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = gateSeen.get(p.getUniqueId());
        if (last != null && now - last < GATE_PAUSE_MS) {
            return;
        }
        gateSeen.put(p.getUniqueId(), now);
        // einen Schritt zurück in die Stadt, bis der Platz gefunden ist
        p.teleport(s.town.gateBack(gate));
        plugin.rtp().gate(p, gate.target());
    }

    /** Ohne Bett/Anker (und beim Verlassen des End): zurück in die Marktstadt der Spawn-Welt. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (!e.isBedSpawn() && !e.isAnchorSpawn() && s.town.ready()) {
            e.setRespawnLocation(s.town.spawn());
        }
    }

    /** In der Spawn-Welt gibt es keine echten Portale (falls doch eins entsteht, führt es nirgends hin). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent e) {
        if (s.town.isSpawnWorld(e.getFrom().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gateSeen.remove(e.getPlayer().getUniqueId());
    }
}
