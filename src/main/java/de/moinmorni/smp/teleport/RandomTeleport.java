package de.moinmorni.smp.teleport;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.player.Players;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Zufalls-Teleport: Menü mit Nah, Mittel und Weit (dazu Nether und End, wenn man dort schon war). Gesucht wird ein
 * sicherer Ort (fester Boden, Platz für Kopf und Füße, unter freiem Himmel, kein Grundstück, nicht in der Stadt); die
 * Chunks werden dabei im Hintergrund geladen. Danach läuft die normale Wartezeit. Abklingzeit nach jedem Sprung.
 */
public final class RandomTeleport {

    /** Wie viele Punkte höchstens probiert werden. */
    private static final int TRIES = 24;
    /** Abstand zur Weltgrenze. */
    private static final int MARGIN = 32;

    private static final Set<Material> DANGEROUS = EnumSet.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.COBWEB, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE,
            Material.LAVA, Material.LAVA_CAULDRON, Material.BUBBLE_COLUMN);

    private final Smp s;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public RandomTeleport(Smp s) {
        this.s = s;
    }

    /** Die Ziele in der Reihenfolge des Menüs. */
    enum Target {
        NAH(Material.OAK_SAPLING, 10), MITTEL(Material.SPRUCE_SAPLING, 12), WEIT(Material.COMPASS, 14),
        NETHER(Material.NETHERRACK, 16), END(Material.END_STONE, 22);

        final Material icon;
        final int slot;

        Target(Material icon, int slot) {
            this.icon = icon;
            this.slot = slot;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Target of(String s) {
            String v = s.toLowerCase(Locale.ROOT);
            for (Target t : values()) {
                if (t.id().equals(v)) {
                    return t;
                }
            }
            return switch (v) {
                case "near", "kurz" -> NAH;
                case "medium", "mid" -> MITTEL;
                case "far", "fern" -> WEIT;
                default -> null;
            };
        }
    }

    public void command(Player p, String[] args) {
        if (args.length > 0) {
            Target t = Target.of(args[0]);
            if (t == null) {
                s.msg.send(p, "rtp.unknown");
                return;
            }
            go(p, t);
            return;
        }
        open(p);
    }

    public void open(Player p) {
        String lang = s.msg.lang(p);
        Menu m = new Menu(3, s.msg.text(lang, "rtp.title"));
        Optional<Players.Info> info = s.players.info(p.getUniqueId());
        for (Target t : Target.values()) {
            if (!available(t, info)) {
                continue;
            }
            SafeSpot.Band band = s.settings.rtpBands.get(t.id());
            m.set(t.slot, Items.button(t.icon, s.msg.text(lang, "rtp." + t.id()), List.of(
                    s.msg.text(lang, "rtp." + t.id() + ".lore"),
                    s.msg.text(lang, "rtp.range", Msg.t("min", band.min()), Msg.t("max", band.max())),
                    s.msg.text(lang, "rtp.click"))), pl -> {
                        pl.closeInventory();
                        go(pl, t);
                    });
        }
        long left = cooldownLeft(p);
        m.show(4, Items.button(Material.CLOCK, s.msg.text(lang, "rtp.info"), List.of(left > 0
                ? s.msg.text(lang, "rtp.cooldown.lore", Msg.t("time", Msg.duration(left, lang)))
                : s.msg.text(lang, "rtp.ready.lore"))));
        m.set(26, Items.button(Material.BARRIER, s.msg.text(lang, "menu.close")), (Player pl) -> pl.closeInventory());
        m.open(p);
    }

    private boolean available(Target t, Optional<Players.Info> info) {
        return switch (t) {
            case NETHER -> world(World.Environment.NETHER) != null && info.map(Players.Info::nether).orElse(false);
            case END -> world(World.Environment.THE_END) != null && info.map(Players.Info::end).orElse(false);
            default -> true;
        };
    }

    private World world(World.Environment env) {
        if (env == World.Environment.NORMAL) {
            World w = Bukkit.getWorld(s.settings.world);
            return w != null ? w : Bukkit.getWorlds().get(0);
        }
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) {
                return w;
            }
        }
        return null;
    }

    public long cooldownLeft(Player p) {
        Long until = cooldown.get(p.getUniqueId());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void go(Player p, Target t) {
        if (!available(t, s.players.info(p.getUniqueId()))) {
            s.msg.send(p, "rtp.not-yet");
            return;
        }
        long left = cooldownLeft(p);
        if (left > 0 && !s.admin(p)) {
            s.msg.send(p, "rtp.cooldown", Msg.t("time", Msg.duration(left, s.msg.lang(p))));
            return;
        }
        if (s.combat.tagged(p) && !s.admin(p)) {
            s.msg.send(p, "combat.blocked", Msg.t("seconds", s.combat.secondsLeft(p)));
            return;
        }
        if (!searching.add(p.getUniqueId())) {
            s.msg.send(p, "rtp.busy");
            return;
        }
        World w = world(switch (t) {
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
            default -> World.Environment.NORMAL;
        });
        if (w == null) {
            searching.remove(p.getUniqueId());
            s.msg.send(p, "rtp.not-yet");
            return;
        }
        s.msg.send(p, "rtp.searching");
        int border = (int) Math.min(Integer.MAX_VALUE / 2, w.getWorldBorder().getSize() / 2);
        attempt(p.getUniqueId(), w, s.settings.rtpBands.get(t.id()), border, 0);
    }

    private void attempt(UUID id, World w, SafeSpot.Band band, int border, int n) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            searching.remove(id);
            return;
        }
        if (n >= TRIES) {
            searching.remove(id);
            s.msg.send(p, "rtp.failed");
            return;
        }
        Location centre = w.getWorldBorder().getCenter();
        SafeSpot.Point pt = SafeSpot.pick(ThreadLocalRandom.current(), band, centre.getBlockX(), centre.getBlockZ(),
                border, MARGIN);
        w.getChunkAtAsync(pt.x() >> 4, pt.z() >> 4, true).whenComplete((chunk, err) ->
                Bukkit.getScheduler().runTask(s.plugin, () -> {
                    Player pl = Bukkit.getPlayer(id);
                    if (pl == null) {
                        searching.remove(id);
                        return;
                    }
                    Location spot = err == null ? check(w, pt.x(), pt.z()) : null;
                    if (spot == null) {
                        attempt(id, w, band, border, n + 1);
                        return;
                    }
                    searching.remove(id);
                    s.teleports.start(pl, spot, "place.wild", () -> {
                        cooldown.put(id, System.currentTimeMillis() + s.settings.rtpCooldownSeconds * 1000L);
                        s.msg.send(pl, "rtp.arrived", Msg.t("x", spot.getBlockX()), Msg.t("z", spot.getBlockZ()));
                    });
                }));
    }

    /** Sicherer Ort an dieser Stelle (Füße), oder null. */
    Location check(World w, int x, int z) {
        boolean claimed = s.claims.at(w.getName(), x >> 4, z >> 4) != null;
        boolean town = s.town.inArea(w, x, z);
        boolean inside = w.getWorldBorder().isInside(new Location(w, x + 0.5, 64, z + 0.5));
        SafeSpot.Column col;
        int minY;
        int maxY;
        boolean sky;
        if (w.getEnvironment() == World.Environment.NETHER) {
            col = netherColumn(w, x, z);
            minY = 32;
            maxY = 118;
            sky = false;
        } else {
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            col = column(w, x, y, z, true);
            minY = w.getEnvironment() == World.Environment.THE_END ? 20 : w.getSeaLevel() - 2;
            maxY = w.getMaxHeight() - 2;
            sky = true;
        }
        if (!SafeSpot.ok(col, minY, maxY, sky, claimed, town, inside)) {
            return null;
        }
        return new Location(w, x + 0.5, col.groundY() + 1, z + 0.5, ThreadLocalRandom.current().nextFloat() * 360f, 0f);
    }

    private static SafeSpot.Column netherColumn(World w, int x, int z) {
        for (int y = 32; y < 118; y++) {
            SafeSpot.Column c = column(w, x, y, z, false);
            if (c.ground() == SafeSpot.Ground.SOLID && c.feetFree() && c.headFree()) {
                return c;
            }
        }
        return new SafeSpot.Column(SafeSpot.Ground.NONE, 0, false, false, false);
    }

    private static SafeSpot.Column column(World w, int x, int y, int z, boolean sky) {
        Block ground = w.getBlockAt(x, y, z);
        return new SafeSpot.Column(ground(ground.getType()), y, free(w.getBlockAt(x, y + 1, z)),
                free(w.getBlockAt(x, y + 2, z)), sky);
    }

    static SafeSpot.Ground ground(Material m) {
        if (DANGEROUS.contains(m)) {
            return SafeSpot.Ground.DANGEROUS;
        }
        if (m == Material.WATER || m == Material.KELP || m == Material.KELP_PLANT || m == Material.SEAGRASS
                || m == Material.TALL_SEAGRASS) {
            return SafeSpot.Ground.LIQUID;
        }
        if (Tag.LEAVES.isTagged(m)) {
            return SafeSpot.Ground.LEAVES;
        }
        if (m.isSolid() && m.isOccluding() || m.isSolid() && (Tag.SLABS.isTagged(m) || Tag.STAIRS.isTagged(m)
                || m == Material.DIRT_PATH || m == Material.FARMLAND || m == Material.SNOW_BLOCK)) {
            return SafeSpot.Ground.SOLID;
        }
        return SafeSpot.Ground.NONE;
    }

    private static boolean free(Block b) {
        Material m = b.getType();
        return b.isPassable() && !b.isLiquid() && !DANGEROUS.contains(m);
    }
}
