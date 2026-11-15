package de.moinmorni.smp.world;

import de.moinmorni.smp.text.Texts;
import de.moinmorni.smp.town.Layout;
import de.moinmorni.smp.town.Layout.Box;
import de.moinmorni.smp.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Das Schimmern der drei Reisetore: in der Öffnung ein Bild (Nether: echtes Portal-Wabern, End: dunkler Sternenhimmel,
 * Oberwelt: heller Himmel), darüber eine große Beschriftung auf Deutsch und Englisch, dazu Funken. Alles nur Anzeige
 * (keine Blöcke, nichts zum Abbauen), wird beim Start neu gesetzt.
 */
public final class GateDecor {

    public static final String TAG = "morni_smp_gate";

    private final Town town;

    public GateDecor(Town town) {
        this.town = town;
    }

    /** Alte Anzeigen weg, neue hin (nach dem Bau bzw. beim Start). */
    public void refresh() {
        if (!town.ready()) {
            return;
        }
        World w = town.world();
        for (Entity e : w.getEntities()) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
        for (Layout.Gate g : town.layout().gates()) {
            membrane(w, g);
            label(w, g);
        }
    }

    private void membrane(World w, Layout.Gate g) {
        Box b = g.membrane();
        int ground = town.ground();
        boolean alongX = g.axis().equals("x");
        float width = alongX ? b.x2() - b.x1() + 1 : b.z2() - b.z1() + 1;
        float height = b.y2() - b.y1() + 1;
        BlockData data;
        float thick;
        switch (g.target()) {
            case "nether" -> {
                data = Bukkit.createBlockData("minecraft:nether_portal[axis=" + g.axis() + "]");
                thick = 1f; // das Portal-Modell ist selbst dünn
            }
            case "end" -> {
                data = Bukkit.createBlockData("minecraft:black_stained_glass");
                thick = 0.12f;
            }
            default -> {
                data = Bukkit.createBlockData("minecraft:light_blue_stained_glass");
                thick = 0.12f;
            }
        }
        // Mitte der Öffnung in der Tiefe
        double cx = alongX ? b.x1() : b.x1() + 0.5;
        double cz = alongX ? b.z1() + 0.5 : b.z1();
        Location at = new Location(w, cx, ground + b.y1(), cz);
        float off = -thick / 2f;
        Vector3f scale = alongX ? new Vector3f(width, height, thick) : new Vector3f(thick, height, width);
        Vector3f shift = alongX ? new Vector3f(0, 0, off) : new Vector3f(off, 0, 0);
        w.spawn(at, BlockDisplay.class, d -> {
            d.addScoreboardTag(TAG);
            d.addScoreboardTag(TAG + "_" + g.target());
            d.setViewRange(4f);
            d.setDisplayWidth(2 * Math.max(scale.x, scale.z) + 2f); // Kasten um die Ecke, an der die Anzeige steht
            d.setDisplayHeight(scale.y + 1f);
            d.setPersistent(false);
            d.setBlock(data);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(shift, new AxisAngle4f(), scale, new AxisAngle4f()));
        });
    }

    /** Große Zielschrift (Oberwelt · Overworld / Nether / Das End · The End), darunter klein „Reisetor · Travel gate“. */
    private void label(World w, Layout.Gate g) {
        String title = switch (g.target()) {
            case "nether" -> "<red><b>Nether</b></red>";
            case "end" -> "<light_purple><b>Das End</b></light_purple> <gray>·</gray> <light_purple><b>The End</b></light_purple>";
            default -> "<green><b>Oberwelt</b></green> <gray>·</gray> <green><b>Overworld</b></green>";
        };
        double y = town.ground() + g.labelY();
        text(w, g, new Location(w, g.labelX(), y + 0.75, g.labelZ()), title, 3.0f);
        text(w, g, new Location(w, g.labelX(), y, g.labelZ()), "<gold>Reisetor · Travel gate</gold>", 1.5f);
    }

    private static void text(World w, Layout.Gate g, Location at, String mini, float scale) {
        w.spawn(at, TextDisplay.class, t -> {
            t.addScoreboardTag(TAG);
            t.addScoreboardTag(TAG + "_" + g.target());
            t.setPersistent(false);
            t.text(Texts.MM.deserialize(mini));
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(110, 15, 20, 30));
            t.setLineWidth(600);
            t.setViewRange(4f); // auch vom Marktplatz aus (≈ 250 Blöcke)
            // Ohne eigene Größe ist die Anzeige für den Client ein Punkt: liegt der Punkt außerhalb des Blickfelds,
            // wird die ganze Schrift weggelassen (so fehlte sie beim End-Tor). Größe = Fläche der Schrift.
            t.setDisplayWidth(14f);
            t.setDisplayHeight(3f);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
    }

    /** Alle 10 Sekunden: fehlt an einem Tor etwas (Chunk war beim Start nicht bereit), neu setzen. */
    public void repair() {
        if (!town.ready()) {
            return;
        }
        World w = town.world();
        for (Layout.Gate g : town.layout().gates()) {
            Location c = new Location(w, g.labelX(), town.ground() + g.labelY(), g.labelZ());
            if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) {
                continue;
            }
            long n = w.getNearbyEntities(c, 12, 12, 12).stream()
                    .filter(e -> e.getScoreboardTags().contains(TAG + "_" + g.target())).count();
            if (n < 3) {
                for (Entity e : w.getNearbyEntities(c, 12, 12, 12)) {
                    if (e.getScoreboardTags().contains(TAG + "_" + g.target())) {
                        e.remove();
                    }
                }
                membrane(w, g);
                label(w, g);
            }
        }
    }

    /** Brunnen: oben sprudelt es, vom Schalenrand tropft und rieselt Wasser ins Becken (nur Partikel). */
    private static void fountain(World w, int ground) {
        Location top = new Location(w, 0.5, ground + 8.6, 0.5);
        w.spawnParticle(Particle.SPLASH, top, 10, 0.25, 0.15, 0.25, 0.08);
        w.spawnParticle(Particle.FALLING_WATER, top, 4, 0.6, 0.1, 0.6, 0);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            Location edge = new Location(w, 0.5 + Math.cos(a) * 2.7, ground + 6.95, 0.5 + Math.sin(a) * 2.7);
            w.spawnParticle(Particle.FALLING_WATER, edge, 2, 0.12, 0.02, 0.12, 0);
            w.spawnParticle(Particle.DRIPPING_WATER, edge, 1, 0.1, 0.0, 0.1, 0);
        }
        w.spawnParticle(Particle.SPLASH, new Location(w, 0.5, ground + 2.05, 0.5), 8, 2.6, 0.02, 2.6, 0.05);
    }

    /** Alle halbe Sekunde: Funken in den Toren, wenn jemand in der Nähe ist. */
    public void particles() {
        if (!town.ready()) {
            return;
        }
        World w = town.world();
        if (w.getPlayers().isEmpty()) {
            return;
        }
        int ground = town.ground();
        fountain(w, ground);
        for (Layout.Gate g : town.layout().gates()) {
            Box b = g.membrane();
            Location c = new Location(w, (b.x1() + b.x2() + 1) / 2.0, ground + (b.y1() + b.y2() + 1) / 2.0,
                    (b.z1() + b.z2() + 1) / 2.0);
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(c) < 64 * 64) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            double sx = (b.x2() - b.x1() + 1) / 2.0;
            double sz = (b.z2() - b.z1() + 1) / 2.0;
            switch (g.target()) {
                case "nether" -> {
                    w.spawnParticle(Particle.PORTAL, c, 12, sx, 1.4, sz, 0.3);
                    w.spawnParticle(Particle.FLAME, c, 2, sx, 1.4, sz, 0.01);
                }
                case "end" -> {
                    w.spawnParticle(Particle.END_ROD, c, 3, sx, 1.4, sz, 0.01);
                    w.spawnParticle(Particle.REVERSE_PORTAL, c, 8, sx, 1.4, sz, 0.02);
                }
                default -> {
                    w.spawnParticle(Particle.HAPPY_VILLAGER, c, 4, sx, 1.4, sz, 0.0);
                    w.spawnParticle(Particle.CHERRY_LEAVES, c, 2, sx, 1.4, sz, 0.0);
                }
            }
        }
    }
}
