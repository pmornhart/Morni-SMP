package de.moinmorni.smp.teleport;

import de.moinmorni.smp.Smp;
import de.moinmorni.smp.text.Msg;
import de.moinmorni.smp.ui.Items;
import de.moinmorni.smp.ui.Menu;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Zufalls-Teleport: Menü mit Oberwelt (Nah, Mittel, Weit), Nether und End – auch die Reisetore der Marktstadt landen
 * hier. Gesucht wird ein sicherer Ort (fester Boden, Platz für Kopf und Füße, kein Grundstück; Oberwelt und End unter
 * freiem Himmel, Nether zwischen y 32 und 110 unter dem Dach, nie auf Lava); die Chunks werden im Hintergrund geladen.
 * Im End geht es auf die äußeren Inseln, findet sich dort nichts, auf die Hauptinsel – nie in die Leere. Per Befehl
 * läuft danach die normale Wartezeit, aus einem Reisetor geht es sofort. Abklingzeit nach jedem Sprung.
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
        NAH(Material.OAK_SAPLING, 11), MITTEL(Material.SPRUCE_SAPLING, 12), WEIT(Material.COMPASS, 13),
        NETHER(Material.NETHERRACK, 15), END(Material.END_STONE, 16);

        final Material icon;
        final int slot;

        Target(Material icon, int slot) {
            this.icon = icon;
            this.slot = slot;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Target of(String s) {
            String v = s.toLowerCase(Locale.ROOT);
            for (Target t : values()) {
                if (t.id().equals(v)) {
                    return t;
                }
            }
            return switch (v) {
                case "near", "kurz", "oberwelt", "overworld", "welt" -> NAH;
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
        m.show(10, Items.button(Material.GRASS_BLOCK, s.msg.text(lang, "rtp.overworld"),
                List.of(s.msg.text(lang, "rtp.overworld.lore"))));
        for (Target t : Target.values()) {
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
        go(p, t, false);
    }

    /** Aus einem Reisetor: „overworld“ (Ring aus der config.yml), „nether“, „end“ – ohne Wartezeit. */
    public void gate(Player p, String target) {
        Target t = switch (target) {
            case "nether" -> Target.NETHER;
            case "end" -> Target.END;
            default -> Target.of(s.settings.gateOverworld);
        };
        go(p, t == null ? Target.NAH : t, true);
    }

    private void go(Player p, Target t, boolean instant) {
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
            s.msg.send(p, "rtp.no-world");
            return;
        }
        s.msg.send(p, "rtp.searching." + (t == Target.NETHER || t == Target.END ? t.id() : "overworld"));
        int border = (int) Math.min(Integer.MAX_VALUE / 2, w.getWorldBorder().getSize() / 2);
        attempt(new Search(p.getUniqueId(), w, s.settings.rtpBands.get(t.id()), border, instant,
                t == Target.END), 0);
    }

    /** Eine laufende Suche. {@code endFallback}: im End zuletzt auf der Hauptinsel suchen. */
    private record Search(UUID id, World w, SafeSpot.Band band, int border, boolean instant, boolean endFallback) {
    }

    /** Hauptinsel des End: Ring um 0/0 (außerhalb des Brunnens, innerhalb des Inselrands). */
    private static final SafeSpot.Band END_MAIN = new SafeSpot.Band(12, 70);

    private void attempt(Search q, int n) {
        Player p = Bukkit.getPlayer(q.id());
        if (p == null) {
            searching.remove(q.id());
            return;
        }
        boolean main = q.endFallback() && n >= TRIES;
        if (n >= TRIES + (q.endFallback() ? TRIES : 0)) {
            searching.remove(q.id());
            s.msg.send(p, "rtp.failed");
            return;
        }
        SafeSpot.Point pt = main
                ? SafeSpot.pick(ThreadLocalRandom.current(), END_MAIN, 0, 0, q.border(), 0)
                : SafeSpot.pick(ThreadLocalRandom.current(), q.band(), 0, 0, q.border(), MARGIN);
        World w = q.w();
        w.getChunkAtAsync(pt.x() >> 4, pt.z() >> 4, true).whenComplete((chunk, err) ->
                Bukkit.getScheduler().runTask(s.plugin, () -> {
                    Player pl = Bukkit.getPlayer(q.id());
                    if (pl == null) {
                        searching.remove(q.id());
                        return;
                    }
                    Location spot = err == null ? check(w, pt.x(), pt.z()) : null;
                    if (spot == null) {
                        attempt(q, n + 1);
                        return;
                    }
                    searching.remove(q.id());
                    Runnable arrived = () -> {
                        cooldown.put(q.id(), System.currentTimeMillis() + s.settings.rtpCooldownSeconds * 1000L);
                        s.msg.send(pl, "rtp.arrived", Msg.t("x", spot.getBlockX()), Msg.t("z", spot.getBlockZ()));
                    };
                    if (q.instant()) {
                        s.teleports.remember(pl);
                        pl.teleportAsync(spot).thenAccept(ok -> {
                            if (Boolean.TRUE.equals(ok)) {
                                pl.playSound(pl.getLocation(), org.bukkit.Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.4f);
                                arrived.run();
                            }
                        });
                    } else {
                        s.teleports.start(pl, spot, "place.wild", arrived);
                    }
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
            maxY = 112;
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
        for (int y = 32; y <= 110; y++) {
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
        if (Tag.LEAVES.isTagged(m) || Tag.WART_BLOCKS.isTagged(m) || m == Material.SHROOMLIGHT) {
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
