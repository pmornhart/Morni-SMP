package de.moinmorni.smp.town;

import de.moinmorni.smp.db.Meta;
import de.moinmorni.smp.text.Texts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Baut die Marktstadt in die Welt: erst das Gelände (Plateau, Abhang zum Gelände ringsum), dann alle Blöcke des
 * Plans, zuletzt Schilder und Banner – Stück für Stück, höchstens ein paar Millisekunden pro Tick, damit der Server
 * flüssig bleibt. Gebaut wird nur beim ersten Start und wenn {@link TownPlan#VERSION} steigt (oder per
 * {@code /smpadmin stadt neu}); außerhalb der Stadt wird nie etwas verändert.
 */
public final class TownBuilder {

    private static final long BUDGET_NS = 20_000_000L;
    private static final String KEY_VERSION = "town.version";
    private static final String KEY_GROUND = "town.y";

    private final JavaPlugin plugin;
    private final Town town;
    private final TownPlan.Result plan;
    private final Meta meta;
    private final Logger log;
    private boolean running;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Set<String> broken = new HashSet<>();

    public TownBuilder(JavaPlugin plugin, Town town, TownPlan.Result plan, Meta meta) {
        this.plugin = plugin;
        this.town = town;
        this.plan = plan;
        this.meta = meta;
        this.log = plugin.getLogger();
    }

    public boolean running() {
        return running;
    }

    /**
     * Stadt sicherstellen: steht sie schon in dieser Fassung, nur merken; sonst bauen.
     *
     * @param force  immer neu bauen
     * @param before vor dem Bauen (z. B. Marktstände räumen)
     * @param after  wenn alles steht
     */
    public void ensure(World world, boolean force, Runnable before, Runnable after) {
        if (running) {
            return;
        }
        town.world(world);
        int stored = meta.getInt(KEY_VERSION, 0);
        int ground = meta.getInt(KEY_GROUND, Integer.MIN_VALUE);
        if (ground != Integer.MIN_VALUE) {
            town.ground(ground);
        }
        if (!force && stored >= TownPlan.VERSION && ground != Integer.MIN_VALUE) {
            keepMarketLoaded(world);
            town.ready(true);
            after.run();
            return;
        }
        running = true;
        town.ready(false);
        log.info("Marktstadt: baue Fassung " + TownPlan.VERSION + (stored > 0 ? " (vorher " + stored + ")" : "") + " …");
        if (before != null && stored > 0) {
            before.run();
        }
        int min = Math.floorDiv(-town.layout().area(), 16);
        int max = Math.floorDiv(town.layout().area(), 16);
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = min; cx <= max; cx++) {
            for (int cz = min; cz <= max; cz++) {
                chunks.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        CompletableFuture.allOf(chunks.toArray(new CompletableFuture[0])).whenComplete((v, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        log.severe("Marktstadt: Chunks ließen sich nicht laden: " + err);
                        running = false;
                        return;
                    }
                    for (CompletableFuture<Chunk> f : chunks) {
                        f.join().addPluginChunkTicket(plugin);
                    }
                    build(world, ground, after);
                }));
    }

    private void build(World world, int storedGround, Runnable after) {
        int area = town.layout().area();
        int flat = town.layout().flat();
        // natürliche Höhen (Boden ohne Bäume) und oberster Block je Spalte
        int size = area * 2 + 1;
        int[] natural = new int[size * size];
        int[] top = new int[size * size];
        List<Integer> flatHeights = new ArrayList<>();
        for (int x = -area; x <= area; x++) {
            for (int z = -area; z <= area; z++) {
                int i = (x + area) * size + (z + area);
                natural[i] = naturalGround(world, x, z);
                top[i] = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                if (Math.max(Math.abs(x), Math.abs(z)) <= flat && (x % 3 == 0) && (z % 3 == 0)) {
                    flatHeights.add(natural[i]);
                }
            }
        }
        int ground = storedGround;
        if (ground == Integer.MIN_VALUE) {
            int[] hs = flatHeights.stream().mapToInt(Integer::intValue).sorted().toArray();
            int median = hs.length == 0 ? world.getSeaLevel() : hs[hs.length / 2];
            int sea = world.getSeaLevel();
            ground = Math.max(sea + 4, Math.min(sea + 40, median + 3));
            ground = Math.min(ground, world.getMaxHeight() - 40);
        }
        town.ground(ground);
        final int g = ground;
        log.info("Marktstadt: Bodenhöhe " + g);

        // Arbeit in Portionen: Gelände-Spalten, dann Blöcke des Plans (von unten nach oben), dann Schilder/Banner
        List<long[]> columns = new ArrayList<>(size * size);
        for (int x = -area; x <= area; x++) {
            for (int z = -area; z <= area; z++) {
                columns.add(new long[] {x, z});
            }
        }
        // von unten nach oben (Physik ist aus, aber so steht jede Laterne schon auf ihrem Pfosten)
        long[] byHeight = plan.plan().blocks().keySet().stream()
                .sorted((a, b) -> Integer.compare(Plan.y(a), Plan.y(b))).mapToLong(Long::longValue).toArray();
        int[] cursor = {0, 0, 0};
        long started = System.currentTimeMillis();
        new BukkitRunnable() {
            @Override
            public void run() {
                long until = System.nanoTime() + BUDGET_NS;
                while (System.nanoTime() < until) {
                    if (cursor[0] < columns.size()) {
                        long[] c = columns.get(cursor[0]++);
                        int x = (int) c[0];
                        int z = (int) c[1];
                        int i = (x + area) * size + (z + area);
                        shape(world, x, z, g, natural[i], top[i], area, flat);
                    } else if (cursor[1] < byHeight.length) {
                        long p = byHeight[cursor[1]++];
                        place(world, Plan.x(p), g + Plan.y(p), Plan.z(p), plan.plan().blocks().get(p));
                    } else {
                        cancel();
                        finish(world, g, started, after);
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Gelände einer Spalte: Plateau (aufschütten/abtragen) oder Abhang zum alten Gelände. */
    private void shape(World w, int x, int z, int ground, int natural, int top, int area, int flat) {
        int d = Math.max(Math.abs(x), Math.abs(z));
        int target;
        if (d <= flat) {
            target = ground - 1; // die Bodenschicht selbst setzt der Plan
        } else {
            double t = (d - flat) / (double) (area - flat + 1);
            double s = t * t * (3 - 2 * t);
            target = (int) Math.round(ground + (natural - ground) * s);
        }
        int min = w.getMinHeight() + 1;
        // aufschütten
        for (int y = Math.max(min, natural + 1); y <= target; y++) {
            String fill = d > flat && y == target ? "grass_block" : (target - y < 3 ? "dirt" : "stone");
            place(w, x, y, z, fill);
        }
        // abtragen (alles über dem Ziel, aber kein Wasser – Seen und Meer bleiben)
        int clearFrom = d <= flat ? ground + 1 : target + 1;
        for (int y = clearFrom; y <= Math.max(top, natural); y++) {
            Block b = w.getBlockAt(x, y, z);
            if (!b.getType().isAir() && !b.isLiquid()) {
                b.setType(Material.AIR, false);
            }
        }
        if (d > flat && target <= natural && target >= min) {
            Block b = w.getBlockAt(x, target, z);
            if (b.getType() == Material.DIRT || b.getType() == Material.STONE || b.getType().isAir()) {
                b.setType(Material.GRASS_BLOCK, false);
            }
        }
    }

    private static final Set<Material> NOT_GROUND = EnumSet.of(Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK, Material.MUSHROOM_STEM, Material.BAMBOO, Material.CACTUS, Material.BEE_NEST,
            Material.VINE, Material.SNOW, Material.MOSS_CARPET, Material.COCOA);

    /** Höchster natürlicher Boden (ohne Bäume, Pilze, Bambus). */
    private static int naturalGround(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        while (y > w.getMinHeight()) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || NOT_GROUND.contains(m) || m.isAir()) {
                y--;
            } else {
                break;
            }
        }
        return y;
    }

    private void place(World w, int x, int y, int z, String data) {
        if (data == null || y < w.getMinHeight() || y >= w.getMaxHeight()) {
            return;
        }
        BlockData bd = cache.get(data);
        if (bd == null) {
            if (broken.contains(data)) {
                return;
            }
            try {
                bd = Bukkit.createBlockData(data.startsWith("minecraft:") ? data : "minecraft:" + data);
                cache.put(data, bd);
            } catch (IllegalArgumentException e) {
                broken.add(data);
                log.warning("Marktstadt: Block „" + data + "“ gibt es in dieser Version nicht – übersprungen.");
                return;
            }
        }
        w.getBlockAt(x, y, z).setBlockData(bd, false);
    }

    private void finish(World world, int ground, long started, Runnable after) {
        // Schilder und Banner
        for (Plan.Sign s : plan.plan().signs()) {
            Block b = world.getBlockAt(s.x(), ground + s.y(), s.z());
            if (b.getState() instanceof Sign sign) {
                write(sign.getSide(Side.FRONT), s.front());
                write(sign.getSide(Side.BACK), s.back().isEmpty() ? s.front() : s.back());
                sign.setWaxed(true);
                sign.update(true, false);
            }
        }
        for (Plan.Banner bn : plan.plan().banners()) {
            Block b = world.getBlockAt(bn.x(), ground + bn.y(), bn.z());
            BlockState st = b.getState();
            if (st instanceof Banner banner) {
                List<Pattern> patterns = new ArrayList<>();
                for (String[] p : bn.patterns()) {
                    PatternType type = patternType(p[1]);
                    if (type != null) {
                        patterns.add(new Pattern(DyeColor.valueOf(p[0].toUpperCase(Locale.ROOT)), type));
                    }
                }
                banner.setPatterns(patterns);
                banner.update(true, false);
            }
        }
        // Tiere, Monster und herumliegende Sachen, die beim Bauen in der Stadt waren, verschwinden
        int area = town.layout().area();
        for (Entity e : world.getEntities()) {
            Location l = e.getLocation();
            if (!(e instanceof Player) && Math.max(Math.abs(l.getBlockX()), Math.abs(l.getBlockZ())) <= area
                    && !e.getScoreboardTags().contains(Boards.TAG)) {
                e.remove();
            }
        }
        Location spawn = town.spawn();
        world.setSpawnLocation(spawn);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        for (Player p : world.getPlayers()) {
            if (town.inArea(p.getLocation())) {
                p.teleport(spawn);
            }
        }
        meta.set(KEY_VERSION, TownPlan.VERSION);
        meta.set(KEY_GROUND, ground);
        int min = Math.floorDiv(-area, 16);
        int max = Math.floorDiv(area, 16);
        for (int cx = min; cx <= max; cx++) {
            for (int cz = min; cz <= max; cz++) {
                world.getChunkAt(cx, cz).removePluginChunkTicket(plugin);
            }
        }
        keepMarketLoaded(world);
        running = false;
        town.ready(true);
        log.info("Marktstadt fertig (" + plan.plan().blocks().size() + " Blöcke, "
                + (System.currentTimeMillis() - started) / 1000 + " s).");
        if (after != null) {
            after.run();
        }
    }

    /** Der Marktplatz bleibt geladen (Bestenliste, Stände laufen ab, auch wenn keiner dort ist). */
    private void keepMarketLoaded(World world) {
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.getChunkAt(cx, cz).addPluginChunkTicket(plugin);
            }
        }
    }

    private static void write(SignSide side, List<String> lines) {
        for (int i = 0; i < 4; i++) {
            side.line(i, i < lines.size() ? Texts.MM.deserialize(lines.get(i)) : Component.empty());
        }
    }

    @SuppressWarnings("deprecation")
    private static PatternType patternType(String name) {
        try {
            return org.bukkit.Registry.BANNER_PATTERN.get(org.bukkit.NamespacedKey.minecraft(name));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
