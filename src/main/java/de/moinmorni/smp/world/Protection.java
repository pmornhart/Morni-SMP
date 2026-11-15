package de.moinmorni.smp.world;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.claims.ClaimRules;
import de.moinmorni.smp.claims.Claims;
import de.moinmorni.smp.combat.CombatTag;
import de.moinmorni.smp.market.ui.StallUi;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.town.Layout.StallSpot;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Schutz von Stadt und Grundstücken. In der Stadt baut niemand (außer Admins im Bau-Modus und Mietern an ihrem
 * Stand: Kisten, Fässer, Schilder); Türen, Werkbänke und Ähnliches darf jeder benutzen. Auf einem Grundstück dürfen
 * nur Besitzer und Vertraute bauen und Kisten öffnen. Von außen hineinfließen, -schieben, -brennen oder -sprengen
 * geht nicht. PvP gibt es nur in der Wildnis und auf Grundstücken, deren Besitzer es erlaubt.
 */
public final class Protection implements Listener {

    /** Sentinel-Besitzer für „Stadt“ (für die Regeln über Grenzen hinweg). */
    static final UUID TOWN = new UUID(0, 0);

    /** Blöcke, die in der Stadt jeder benutzen darf. */
    private static final Set<Material> TOWN_USE = EnumSet.of(Material.CRAFTING_TABLE, Material.ENDER_CHEST,
            Material.ENCHANTING_TABLE, Material.CARTOGRAPHY_TABLE, Material.LOOM, Material.STONECUTTER,
            Material.GRINDSTONE, Material.SMITHING_TABLE, Material.BELL, Material.LECTERN);

    private final Smp s;
    private final StallUi stalls;

    public Protection(Smp s, StallUi stalls) {
        this.s = s;
        this.stalls = stalls;
    }

    // ---------------------------------------------------------------- Abfragen

    /** Besitzer einer Stelle: {@link #TOWN} in der Stadt, Grundstücksbesitzer oder null (Wildnis). */
    UUID owner(World w, int x, int z) {
        if (s.town.inArea(w, x, z)) {
            return TOWN;
        }
        Claims.Claim c = s.claims.at(w.getName(), x >> 4, z >> 4);
        return c == null ? null : c.owner();
    }

    UUID owner(Block b) {
        return owner(b.getWorld(), b.getX(), b.getZ());
    }

    UUID owner(Location l) {
        return owner(l.getWorld(), l.getBlockX(), l.getBlockZ());
    }

    /** PvP-Zone einer Stelle. */
    public ClaimRules.Zone zone(Location l) {
        if (s.town.inArea(l)) {
            return ClaimRules.Zone.TOWN;
        }
        Claims.Claim c = s.claims.at(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4);
        if (c == null) {
            return ClaimRules.Zone.WILDERNESS;
        }
        return c.pvp() ? ClaimRules.Zone.CLAIM_PVP : ClaimRules.Zone.CLAIM_PEACEFUL;
    }

    /** Darf der Spieler hier Blöcke setzen/abbauen? {@code type}: der betroffene Block (für Marktstände). */
    public boolean mayBuild(Player p, Block b, Material type) {
        if (s.bypass(p)) {
            return true;
        }
        if (s.town.inArea(b)) {
            StallSpot spot = s.town.stallZone(b);
            return spot != null && stalls.mayBuild(p, spot) && type != null && StallUi.movable(type);
        }
        Claims.Claim c = s.claims.at(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4);
        return c == null || ClaimRules.mayUse(c.owner(), s.claims.trusted(c.owner(), p.getUniqueId()), false);
    }

    /** Darf der Spieler hier Dinge benutzen (Kisten, Tiere, Rahmen …)? */
    public boolean mayUse(Player p, Location l) {
        if (s.bypass(p)) {
            return true;
        }
        UUID owner = owner(l);
        if (owner == null) {
            return true;
        }
        if (owner == TOWN) {
            return false;
        }
        return s.claims.trusted(owner, p.getUniqueId());
    }

    private void deny(Player p, Block b) {
        UUID owner = owner(b);
        if (owner == TOWN) {
            s.msg.bar(p, "protect.town");
        } else if (owner != null) {
            Claims.Claim c = s.claims.at(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4);
            s.msg.bar(p, "protect.claim", Msg.t("owner", c == null ? "?" : c.ownerName()));
        }
    }

    // ---------------------------------------------------------------- Bauen

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!mayBuild(e.getPlayer(), e.getBlock(), e.getBlock().getType())) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!mayBuild(p, e.getBlockPlaced(), e.getBlockPlaced().getType())) {
            e.setCancelled(true);
            deny(p, e.getBlockPlaced());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Block b = e.getBlockClicked().getRelative(e.getBlockFace());
        if (!mayBuild(e.getPlayer(), b, null) || !mayBuild(e.getPlayer(), e.getBlock(), null)) {
            e.setCancelled(true);
            deny(e.getPlayer(), b);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (!mayBuild(e.getPlayer(), e.getBlockClicked(), null)) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlockClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        if (e.getPlayer() != null && !mayBuild(e.getPlayer(), e.getBlock(), null)) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
            return;
        }
        UUID from = owner(e.getBlock());
        e.getBlocks().removeIf(st -> !ClaimRules.crossAllowed(from, owner(st.getBlock())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && !mayBuild(e.getPlayer(), e.getBlock().getRelative(e.getBlockFace()), null)) {
            e.setCancelled(true);
            deny(e.getPlayer(), e.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = CombatTag.attacker(e.getRemover());
        Hanging h = e.getEntity();
        if (p != null) {
            if (!mayUse(p, h.getLocation())) {
                e.setCancelled(true);
                deny(p, h.getLocation().getBlock());
            }
        } else if (owner(h.getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- Benutzen

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        Player p = e.getPlayer();
        if (e.getAction() == Action.PHYSICAL) {
            // Ackerboden zertrampeln, Schildkröteneier
            if ((b.getType() == Material.FARMLAND || b.getType() == Material.TURTLE_EGG) && !mayBuild(p, b, null)) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getHand() == EquipmentSlot.OFF_HAND && e.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        if (s.bypass(p)) {
            return;
        }
        Material m = b.getType();
        UUID owner = owner(b);
        if (owner == null) {
            return;
        }
        boolean interactive = m.isInteractable();
        if (owner == TOWN) {
            if (!interactive || openForAll(m) || TOWN_USE.contains(m)) {
                return;
            }
            StallSpot spot = s.town.stallZone(b);
            if (spot != null && stalls.mayBuild(p, spot)) {
                return;
            }
            e.setUseInteractedBlock(Event.Result.DENY);
            if (e.getHand() == EquipmentSlot.HAND) {
                deny(p, b);
            }
            return;
        }
        if (s.claims.trusted(owner, p.getUniqueId())) {
            return;
        }
        if (interactive && !(m == Material.CRAFTING_TABLE || m == Material.ENDER_CHEST)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            if (e.getHand() == EquipmentSlot.HAND) {
                deny(p, b);
            }
        }
    }

    /** Türen, Tore, Knöpfe: in der Stadt für alle. */
    private static boolean openForAll(Material m) {
        return Tag.DOORS.isTagged(m) || Tag.TRAPDOORS.isTagged(m) || Tag.FENCE_GATES.isTagged(m)
                || Tag.BUTTONS.isTagged(m) || Tag.PRESSURE_PLATES.isTagged(m);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (en instanceof Player || en instanceof Villager || en instanceof WanderingTrader || en instanceof Vehicle) {
            return;
        }
        if (en instanceof Tameable t && t.getOwnerUniqueId() != null
                && t.getOwnerUniqueId().equals(e.getPlayer().getUniqueId())) {
            return;
        }
        if (!mayUse(e.getPlayer(), en.getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer(), en.getLocation().getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!mayUse(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicle(VehicleDestroyEvent e) {
        Player p = CombatTag.attacker(e.getAttacker());
        if (p != null && !mayUse(p, e.getVehicle().getLocation())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- Schaden

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = CombatTag.attacker(e.getDamager());
        Entity victim = e.getEntity();
        if (attacker == null) {
            // Explosion von Creepern & Co. trifft keine Rahmen/Rüstungsständer auf Grundstücken
            if ((victim instanceof Hanging || victim instanceof ArmorStand) && owner(victim.getLocation()) != null) {
                e.setCancelled(true);
            }
            return;
        }
        if (victim instanceof Player target) {
            if (attacker.equals(target)) {
                return;
            }
            if (!ClaimRules.pvp(zone(attacker.getLocation()), zone(target.getLocation()))) {
                e.setCancelled(true);
                s.msg.bar(attacker, "protect.pvp");
            }
            return;
        }
        if (victim instanceof Monster || victim instanceof Slime || victim instanceof Phantom) {
            return;
        }
        if (victim instanceof Animals || victim instanceof Hanging || victim instanceof ArmorStand
                || victim instanceof Villager) {
            if (!mayUse(attacker, victim.getLocation())) {
                e.setCancelled(true);
                deny(attacker, victim.getLocation().getBlock());
            }
        }
    }

    // ---------------------------------------------------------------- Natur

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!ClaimRules.crossAllowed(owner(e.getBlock()), owner(e.getToBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        UUID from = owner(e.getBlock());
        for (Block b : e.getBlocks()) {
            if (!ClaimRules.crossAllowed(from, owner(b)) || !ClaimRules.crossAllowed(from,
                    owner(b.getRelative(e.getDirection())))) {
                e.setCancelled(true);
                return;
            }
        }
        if (!ClaimRules.crossAllowed(from, owner(e.getBlock().getRelative(e.getDirection())))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonRetractEvent e) {
        UUID from = owner(e.getBlock());
        for (Block b : e.getBlocks()) {
            if (!ClaimRules.crossAllowed(from, owner(b))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        Material src = e.getSource().getType();
        if ((src == Material.FIRE || src == Material.SOUL_FIRE) && !ClaimRules.fireSpreads(owner(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (!ClaimRules.fireSpreads(owner(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null) {
            if (!mayBuild(e.getPlayer(), e.getBlock(), null)) {
                e.setCancelled(true);
                deny(e.getPlayer(), e.getBlock());
            }
            return;
        }
        if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                && !ClaimRules.fireSpreads(owner(e.getBlock()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> {
            UUID o = owner(b);
            return !ClaimRules.explosionBreaks(o == TOWN ? null : o, o == TOWN);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> {
            UUID o = owner(b);
            return !ClaimRules.explosionBreaks(o == TOWN ? null : o, o == TOWN);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        Entity en = e.getEntity();
        if (en instanceof FallingBlock) {
            return;
        }
        if (en instanceof Player p) {
            if (!mayBuild(p, e.getBlock(), null)) {
                e.setCancelled(true);
            }
            return;
        }
        if (owner(e.getBlock()) != null) {
            e.setCancelled(true); // Endermen, Verwüster, Wither, Silberfischchen
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent e) {
        UUID from = owner(e.getLocation());
        e.getBlocks().removeIf(st -> !ClaimRules.crossAllowed(from, owner(st.getBlock())));
    }

    /** Keine Monster in der Stadt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if ((e.getEntity() instanceof Monster || e.getEntity() instanceof Phantom || e.getEntity() instanceof Slime)
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && s.town.inArea(e.getLocation())) {
            e.setCancelled(true);
        }
    }
}
