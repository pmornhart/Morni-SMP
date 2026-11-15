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
            d.setPersistent(false);
            d.setBlock(data);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(shift, new AxisAngle4f(), scale, new AxisAngle4f()));
        });
    }

    private void label(World w, Layout.Gate g) {
        String text = switch (g.target()) {
            case "nether" -> "<red><b>NETHER</b></red>\n<gold>Reisetor · Travel gate</gold>";
            case "end" -> "<light_purple><b>END</b></light_purple>\n<gold>Reisetor · Travel gate</gold>";
            default -> "<green><b>OBERWELT</b></green> <gray>·</gray> <green><b>OVERWORLD</b></green>\n"
                    + "<gold>Reisetor · Travel gate</gold>";
        };
        Location at = new Location(w, g.labelX(), town.ground() + g.labelY(), g.labelZ());
        w.spawn(at, TextDisplay.class, t -> {
            t.addScoreboardTag(TAG);
            t.setPersistent(false);
            t.text(Texts.MM.deserialize(text));
            t.setBillboard(Display.Billboard.VERTICAL);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(110, 15, 20, 30));
            t.setLineWidth(400);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(2.2f, 2.2f, 2.2f),
                    new AxisAngle4f()));
        });
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
