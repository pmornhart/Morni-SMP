package de.moinmorni.smp.town;

import de.moinmorni.smp.town.Layout.StallSpot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Die Marktstadt zur Laufzeit: Wo liegt sie (Welt, Höhe), was ist wo (Stände, Auslöser, Reisetore)? Sie steht bei 0/0
 * der eigenen Spawn-Welt (Leere, nur die Stadt-Insel); {@link #ground()} ist die Höhe der Bodenschicht. Die ganze
 * Spawn-Welt gilt als Stadt (geschützt, keine Grundstücke).
 */
public final class Town {

    private final Layout layout;
    private volatile World world;
    private volatile int ground = Integer.MIN_VALUE;
    private volatile boolean ready;

    public Town(Layout layout) {
        this.layout = layout;
    }

    public Layout layout() {
        return layout;
    }

    public World world() {
        return world;
    }

    public void world(World w) {
        this.world = w;
    }

    /** Höhe der Bodenschicht (y = 0 im Plan). */
    public int ground() {
        return ground;
    }

    public void ground(int y) {
        this.ground = y;
    }

    /** Fertig gebaut und benutzbar? */
    public boolean ready() {
        return ready && ground != Integer.MIN_VALUE && world != null;
    }

    public void ready(boolean r) {
        this.ready = r;
    }

    public boolean known() {
        return ground != Integer.MIN_VALUE && world != null;
    }

    private static int cheb(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }

    /** Liegt die Stelle in der geschützten Stadt? Das ist die ganze Spawn-Welt. */
    public boolean inArea(World w, int x, int z) {
        return world != null && w != null && w.equals(world);
    }

    /** Ist das die Spawn-Welt? */
    public boolean isSpawnWorld(World w) {
        return world != null && world.equals(w);
    }

    public boolean inArea(Location l) {
        return l != null && inArea(l.getWorld(), l.getBlockX(), l.getBlockZ());
    }

    public boolean inArea(Block b) {
        return inArea(b.getWorld(), b.getX(), b.getZ());
    }

    /** Auf dem flachen Plateau (innerhalb des Abhangs)? */
    public boolean onPlateau(Location l) {
        return l != null && world != null && world.equals(l.getWorld())
                && cheb(l.getBlockX(), l.getBlockZ()) <= layout.flat();
    }

    /** Chunk berührt die Stadt (für Grundstücke mit Abstand {@code margin} Chunks)? */
    public boolean chunkNear(World w, int cx, int cz, int margin) {
        if (world == null || w == null || !w.equals(world)) {
            return false;
        }
        int minChunk = Math.floorDiv(-layout.area(), 16) - margin;
        int maxChunk = Math.floorDiv(layout.area(), 16) + margin;
        return cx >= minChunk && cx <= maxChunk && cz >= minChunk && cz <= maxChunk;
    }

    /** Stand, in dessen Mieter-Bereich der Block liegt (sonst null). */
    public StallSpot stallZone(Block b) {
        if (!known() || !inArea(b)) {
            return null;
        }
        int y = b.getY() - ground;
        for (StallSpot s : layout.stalls()) {
            if (s.zone().contains(b.getX(), y, b.getZ())) {
                return s;
            }
        }
        return null;
    }

    /** Stand, vor oder in dem jemand steht (sonst null). */
    public StallSpot stallFront(Location l) {
        if (!known() || !inArea(l)) {
            return null;
        }
        int y = l.getBlockY() - ground;
        for (StallSpot s : layout.stalls()) {
            if (s.front().contains(l.getBlockX(), y, l.getBlockZ())) {
                return s;
            }
        }
        return null;
    }

    public StallSpot stall(int id) {
        for (StallSpot s : layout.stalls()) {
            if (s.id() == id) {
                return s;
            }
        }
        return null;
    }

    /** Was ein Rechtsklick auf diesen Block öffnet, oder null. */
    public String trigger(Block b) {
        if (!known() || !inArea(b)) {
            return null;
        }
        return layout.triggers().get(Plan.pack(b.getX(), b.getY() - ground, b.getZ()));
    }

    /** Reisetor, in dem die Füße stehen (sonst null). */
    public Layout.Gate gateAt(Location l) {
        if (!known() || !inArea(l)) {
            return null;
        }
        for (Layout.Gate g : layout.gates()) {
            if (g.box().contains(l.getBlockX(), l.getBlockY() - ground, l.getBlockZ())) {
                return g;
            }
        }
        return null;
    }

    /** Wohin man aus einem Reisetor zurückgeschoben wird (in der Stadt, Blick zur Stadt). */
    public Location gateBack(Layout.Gate g) {
        return new Location(world, g.backX(), ground + 1, g.backZ(), g.backYaw(), 0f);
    }

    /** Ankunftspunkt (Füße), Blick nach Norden zum Brunnen. */
    public Location spawn() {
        if (!known()) {
            return world == null ? null : world.getSpawnLocation();
        }
        return new Location(world, layout.spawnX(), ground + layout.spawnY(), layout.spawnZ(), layout.spawnYaw(), 0f);
    }

    public Location board() {
        return new Location(world, layout.boardX(), ground + layout.boardY(), layout.boardZ());
    }

    /** Weltposition eines Plan-Punkts. */
    public Block block(int x, int y, int z) {
        return world.getBlockAt(x, ground + y, z);
    }
}
