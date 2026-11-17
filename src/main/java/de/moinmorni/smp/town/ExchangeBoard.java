package de.moinmorni.smp.town;

import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.shop.Catalog;
import de.moinmorni.smp.shop.Courses;
import de.moinmorni.smp.shop.ServerShop;
import de.moinmorni.smp.shop.Sparkline;
import de.moinmorni.smp.shop.Ware;
import de.moinmorni.smp.text.Texts;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Die Kurstafel der Börse an der Seite des Händlerhauses: was Vroni für die wichtigsten Erze verlangt und zahlt und
 * welche Waren sich gerade am stärksten bewegen. Schwebende Schrift vor der Holztafel, alle 30 Sekunden neu.
 * Gegenstands-Namen kommen als Übersetzungs-Schlüssel – jeder liest sie in seiner Sprache.
 */
public final class ExchangeBoard {

    /** Merkmal der Schrift (zum Wiederfinden und Aufräumen). */
    public static final String TAG = "morni_smp_exchange";
    /** Diese Waren stehen immer auf der Tafel (falls im Sortiment). */
    static final List<String> HEADLINE = List.of("diamond", "netherite_ingot", "emerald", "gold_ingot", "iron_ingot",
            "redstone", "lapis_lazuli", "coal");
    private static final int SHOWN = 6;
    private static final int MOVERS = 4;
    private static final float SCALE = 0.72f;

    private final Town town;
    private final ServerShop shop;

    public ExchangeBoard(Town town, ServerShop shop) {
        this.town = town;
        this.shop = shop;
    }

    /** Tafel neu schreiben (legt die Schrift an, falls sie fehlt). */
    public void refresh() {
        if (!town.ready()) {
            return;
        }
        Location at = town.exchange();
        World w = at.getWorld();
        if (w == null || !w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        TextDisplay display = null;
        for (Entity e : w.getNearbyEntities(at, 3, 4, 3)) {
            if (e instanceof TextDisplay td && e.getScoreboardTags().contains(TAG)) {
                if (display == null) {
                    display = td;
                } else {
                    e.remove();
                }
            }
        }
        if (display == null) {
            display = w.spawn(at, TextDisplay.class, td -> {
                td.addScoreboardTag(TAG);
                td.setBillboard(Display.Billboard.FIXED);
                td.setRotation(at.getYaw(), 0f);
                td.setBackgroundColor(Color.fromARGB(200, 18, 24, 20));
                td.setShadowed(true);
                td.setLineWidth(250);
                td.setBrightness(new Display.Brightness(15, 15));
                td.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                        new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
                td.setPersistent(true);
            });
        }
        display.text(text());
    }

    /** Alte Schrift weg (z. B. vor einem Neubau). */
    public void remove() {
        if (!town.ready()) {
            return;
        }
        Location at = town.exchange();
        if (at.getWorld() == null) {
            return;
        }
        for (Entity e : at.getWorld().getNearbyEntities(at, 3, 4, 3)) {
            if (e.getScoreboardTags().contains(TAG)) {
                e.remove();
            }
        }
    }

    Component text() {
        Catalog cat = shop.catalog();
        StringBuilder sb = new StringBuilder();
        sb.append("<au><b>⚖ Börse · Exchange</b></au>\n");
        sb.append("<mu>Vroni verkauft / zahlt · sells / pays</mu>\n\n");
        int shown = 0;
        for (String id : HEADLINE) {
            Ware w = cat.ware("minecraft:" + id);
            if (w == null || shown >= SHOWN) {
                continue;
            }
            shown++;
            ServerShop.Price pr = shop.price(w);
            sb.append(name(w)).append(" <au>").append(Money.compact(pr.buy(), "de")).append("</au>");
            if (w.sellable()) {
                sb.append(" <mu>/</mu> <em>").append(Money.compact(pr.sell(), "de")).append("</em>");
            }
            sb.append(arrow(pr.factor())).append('\n');
        }
        sb.append("\n<em>Bewegungen</em> <mu>· movers</mu>\n");
        int n = 0;
        for (Courses.Mover m : shop.courses().movers()) {
            Ware w = cat.ware(m.key());
            if (w == null) {
                continue;
            }
            int pct = Sparkline.change(1.0, m.factor());
            sb.append(pct > 0 ? "<green>▲ +" + pct : "<red>▼ −" + Math.abs(pct)).append(" %</").append(pct > 0 ? "green" : "red")
                    .append("> ").append(name(w)).append('\n');
            if (++n >= MOVERS) {
                break;
            }
        }
        if (n == 0) {
            sb.append("<mu>alles beim Grundpreis · all at base price</mu>\n");
        }
        sb.append("\n<mu>/boerse · /wert · /verkaufen hand</mu>");
        return Texts.MM.deserialize(sb.toString());
    }

    private static String name(Ware w) {
        Material m = Material.matchMaterial(w.key());
        return m == null ? "<white>" + w.id() + "</white>" : "<white><lang:" + m.translationKey() + "></white>";
    }

    private static String arrow(double factor) {
        int t = Sparkline.trend(factor);
        if (t == 0) {
            return "";
        }
        int pct = Sparkline.change(1.0, factor);
        return t > 0 ? " <green>▲" + pct + "%</green>" : " <red>▼" + Math.abs(pct) + "%</red>";
    }
}
