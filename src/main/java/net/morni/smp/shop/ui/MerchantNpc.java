package net.morni.smp.shop.ui;

import java.util.ArrayList;
import java.util.List;
import net.morni.smp.Smp;
import net.morni.smp.shop.Catalog;
import net.morni.smp.text.Texts;
import net.morni.smp.town.Layout;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Die Händlerin im Händlerhaus: eine Dorfbewohnerin ohne eigenen Willen (bewegt sich nicht, nimmt keinen Schaden,
 * kein Handels-Fenster), die zu Spielern in der Nähe schaut. Rechtsklick öffnet {@code /shop}. Über dem Tresen drehen
 * sich zwei Schaustücke, die alle paar Sekunden eine andere Abteilung zeigen.
 *
 * <p>Alles ist nicht dauerhaft gespeichert ({@code persistent = false}) und wird regelmäßig geprüft: fehlt etwas
 * (Chunk war nicht geladen, Neubau der Stadt), kommt es neu.
 */
public final class MerchantNpc implements Listener {

    static final String TAG = "mornismp_merchant";
    private static final String TAG_SHOW = "mornismp_showcase";
    private static final String TAG_HINT = "mornismp_merchant_hint";
    private static final double LOOK_RANGE = 8;

    private final Smp s;
    private final ShopUi ui;
    private int turn;

    public MerchantNpc(Smp s, ShopUi ui) {
        this.s = s;
        this.ui = ui;
    }

    private Location spot(Layout.Spot sp) {
        return new Location(s.town.world(), sp.x(), s.town.ground() + sp.y(), sp.z(), sp.yaw(), 0f);
    }

    /** Alle 10 Sekunden: Händlerin und Schaustücke da? Sonst neu hinstellen (nur in geladenen Chunks). */
    public void repair() {
        if (!s.town.ready() || !s.settings.shopEnabled) {
            remove();
            return;
        }
        Location at = spot(s.town.layout().merchant());
        World w = at.getWorld();
        if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        List<Entity> merchants = new ArrayList<>();
        List<Entity> shows = new ArrayList<>();
        List<Entity> hints = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(at, 12, 8, 12)) {
            if (e.getScoreboardTags().contains(TAG)) {
                merchants.add(e);
            } else if (e.getScoreboardTags().contains(TAG_SHOW)) {
                shows.add(e);
            } else if (e.getScoreboardTags().contains(TAG_HINT)) {
                hints.add(e);
            }
        }
        keepOne(merchants);
        if (merchants.isEmpty()) {
            spawnMerchant(at);
        } else if (merchants.get(0).getLocation().distanceSquared(at) > 0.25) {
            merchants.get(0).teleport(at);
        }
        keepOne(hints);
        if (hints.isEmpty()) {
            spawnHint(at.clone().add(0, 2.35, 0));
        }
        if (shows.size() != s.town.layout().showcase().size()) {
            shows.forEach(Entity::remove);
            int i = 0;
            for (Layout.Spot sp : s.town.layout().showcase()) {
                spawnShowcase(spot(sp), i++);
            }
        }
    }

    private static void keepOne(List<Entity> list) {
        while (list.size() > 1) {
            list.remove(list.size() - 1).remove();
        }
    }

    /** Beim Abschalten / ohne Händlerhaus: alles weg. */
    public void remove() {
        if (s.town.world() == null) {
            return;
        }
        for (Entity e : s.town.world().getEntities()) {
            if (e.getScoreboardTags().contains(TAG) || e.getScoreboardTags().contains(TAG_SHOW)
                    || e.getScoreboardTags().contains(TAG_HINT)) {
                e.remove();
            }
        }
    }

    private void spawnMerchant(Location at) {
        at.getWorld().spawn(at, Villager.class, v -> {
            v.addScoreboardTag(TAG);
            v.setPersistent(false);
            v.setAI(false);
            v.setSilent(true);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.setRemoveWhenFarAway(false);
            v.setProfession(Villager.Profession.CARTOGRAPHER);
            v.setVillagerType(Villager.Type.PLAINS);
            v.setVillagerLevel(5);
            // kein eigener Name: der steht schon in der Schrift darüber (sonst doppelt, wenn man sie anschaut)
        });
    }

    private void spawnHint(Location at) {
        at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.addScoreboardTag(TAG_HINT);
            t.setPersistent(false);
            t.text(Texts.render("de", "merchant.npc.hint"));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(90, 15, 20, 30));
            t.setViewRange(0.4f);
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
        });
    }

    private void spawnShowcase(Location at, int index) {
        at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.addScoreboardTag(TAG_SHOW);
            d.setPersistent(false);
            d.setItemStack(new ItemStack(showItem(index)));
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(0.5f);
            d.setInterpolationDuration(10);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
        });
    }

    /** Symbol der Abteilung, die dieses Schaustück gerade zeigt. */
    private Material showItem(int index) {
        List<Catalog.Category> cats = s.shop.catalog().categories();
        if (cats.isEmpty()) {
            return Material.EMERALD;
        }
        Catalog.Category c = cats.get((turn / 6 + index * (cats.size() / 2 + 1)) % cats.size());
        return ShopUi.material(c.icon(), Material.EMERALD);
    }

    /** Jede halbe Sekunde: Schaustücke drehen, alle 3 Sekunden wechseln; Händlerin schaut zum nächsten Spieler. */
    public void tick() {
        if (!s.town.ready() || !s.settings.shopEnabled) {
            return;
        }
        turn++;
        Location at = spot(s.town.layout().merchant());
        World w = at.getWorld();
        if (!w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        int index = 0;
        for (Entity e : w.getNearbyEntities(at, 12, 8, 12)) {
            if (e instanceof ItemDisplay d && e.getScoreboardTags().contains(TAG_SHOW)) {
                float angle = (float) Math.toRadians((turn * 18) % 360);
                d.setInterpolationDelay(0);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(angle),
                        new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
                if (turn % 6 == 0) {
                    d.setItemStack(new ItemStack(showItem(index)));
                }
                index++;
            } else if (e instanceof Villager v && e.getScoreboardTags().contains(TAG)) {
                lookAtNearest(v, at);
            }
        }
    }

    private void lookAtNearest(Villager v, Location home) {
        Player best = null;
        double bestD = LOOK_RANGE * LOOK_RANGE;
        for (Player p : v.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(home);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        float yaw = home.getYaw();
        float pitch = 0f;
        if (best != null) {
            Location eye = v.getEyeLocation();
            Location to = best.getEyeLocation();
            double dx = to.getX() - eye.getX();
            double dy = to.getY() - eye.getY();
            double dz = to.getZ() - eye.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
        v.setRotation(yaw, Math.max(-30f, Math.min(30f, pitch)));
    }

    // ---------------------------------------------------------------- Klicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getScoreboardTags().contains(TAG)) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        if (s.combat.tagged(p) && !s.admin(p)) {
            s.msg.send(p, "combat.blocked", net.morni.smp.text.Msg.t("seconds", s.combat.secondsLeft(p)));
            return;
        }
        ui.main(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) {
            e.setCancelled(true);
        }
    }
}
