package de.moinmorni.smp;

import de.moinmorni.smp.economy.Money;
import de.moinmorni.smp.market.Buyback;
import de.moinmorni.smp.market.Fees;
import de.moinmorni.smp.teleport.SafeSpot;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Werte aus der config.yml (Erklärungen stehen dort). Geld in Hundertstel-Talern. */
public final class Settings {

    // Währung
    public final String symbol;
    public final long startBalance;
    public final long payConfirmAbove;

    // Welt
    public final String world;
    public final String spawnWorld;
    public final int spawnHeight;
    public final String gateOverworld;
    public final int borderRadius;
    public final int borderRadiusNether;
    public final int borderRadiusEnd;
    public final int combatSeconds;
    public final ZoneId zone;

    // Teleport
    public final int teleportDelaySeconds;
    public final boolean adminsInstant;
    public final int maxHomes;
    public final int rtpCooldownSeconds;
    public final Map<String, SafeSpot.Band> rtpBands;

    // Markt
    public final int ahFeeBp;
    public final long ahDurationMs;
    public final int ahMaxListings;
    public final int orderFeeBp;
    public final long orderDurationMs;
    public final int orderMaxActive;
    public final int shopFeeBp;
    public final long stallRent;
    public final long stallWarnMs;
    public final int stallMaxWeeks;

    // Grundstücke
    public final int claimsFree;
    public final long claimPrice;
    public final int claimsMax;

    // Ankauf
    public final List<Buyback.Offer> buyback;
    public final List<Buyback.Tier> buybackTiers;

    // Belohnung
    public final int rewardMinMinutes;
    public final long rewardBase;
    public final long rewardPerDay;
    public final int rewardMaxStreak;
    public final List<String> rewardItems;
    public final List<String> rewardWeekItems;

    // Kopfgeld
    public final long bountyMinimum;

    // Anzeige
    public final boolean sidebarDefault;
    public final String address;

    private Settings(FileConfiguration c) {
        symbol = c.getString("waehrung.zeichen", "₮");
        startBalance = taler(c, "waehrung.startguthaben", 100);
        payConfirmAbove = taler(c, "waehrung.zahlen-bestaetigen-ab", 1000);

        world = c.getString("welt.name", "world");
        spawnWorld = c.getString("spawnwelt.name", "smp_spawn");
        spawnHeight = clamp(c.getInt("spawnwelt.hoehe", 90), 0, 300);
        String og = c.getString("reisetore.oberwelt", "nah").toLowerCase(Locale.ROOT);
        gateOverworld = og.equals("mittel") || og.equals("weit") ? og : "nah";
        borderRadius = clamp(c.getInt("welt.grenze-radius", 10_000), 500, 29_000_000);
        borderRadiusNether = clamp(c.getInt("welt.grenze-radius-nether", 1_250), 100, 29_000_000);
        borderRadiusEnd = clamp(c.getInt("welt.grenze-radius-end", 10_000), 500, 29_000_000);
        combatSeconds = clamp(c.getInt("welt.kampf-sekunden", 15), 0, 120);
        ZoneId z;
        try {
            z = ZoneId.of(c.getString("welt.zeitzone", "Europe/Berlin"));
        } catch (Exception e) {
            z = ZoneId.of("Europe/Berlin");
        }
        zone = z;

        teleportDelaySeconds = clamp(c.getInt("teleport.wartezeit-sekunden", 3), 0, 30);
        adminsInstant = c.getBoolean("teleport.admins-sofort", true);
        maxHomes = clamp(c.getInt("teleport.zuhause-max", 3), 1, 50);
        rtpCooldownSeconds = clamp(c.getInt("zufallsteleport.abklingzeit-sekunden", 60), 0, 3600);
        rtpBands = Map.of(
                "nah", band(c, "zufallsteleport.nah", 500, 2_000),
                "mittel", band(c, "zufallsteleport.mittel", 2_000, 5_000),
                "weit", band(c, "zufallsteleport.weit", 5_000, 9_500),
                "nether", band(c, "zufallsteleport.nether", 100, 1_100),
                "end", band(c, "zufallsteleport.end", 1_000, 5_000));

        ahFeeBp = Fees.basisPoints(c.getDouble("auktionshaus.gebuehr-prozent", 2.0));
        ahDurationMs = hours(c.getDouble("auktionshaus.dauer-stunden", 48));
        ahMaxListings = clamp(c.getInt("auktionshaus.max-angebote", 10), 1, 100);
        orderFeeBp = Fees.basisPoints(c.getDouble("auftraege.gebuehr-prozent", 2.0));
        orderDurationMs = hours(c.getDouble("auftraege.dauer-tage", 7) * 24);
        orderMaxActive = clamp(c.getInt("auftraege.max-auftraege", 5), 1, 50);
        shopFeeBp = Fees.basisPoints(c.getDouble("laeden.gebuehr-prozent", 0.0));
        stallRent = taler(c, "marktstaende.miete-pro-woche", 300);
        stallWarnMs = hours(c.getDouble("marktstaende.warnen-stunden-vorher", 24));
        stallMaxWeeks = clamp(c.getInt("marktstaende.max-wochen-im-voraus", 4), 1, 52);

        claimsFree = clamp(c.getInt("grundstuecke.gratis", 4), 0, 1000);
        claimPrice = taler(c, "grundstuecke.preis", 250);
        claimsMax = clamp(c.getInt("grundstuecke.max", 64), 1, 10_000);

        buyback = new ArrayList<>();
        // eigene Preisliste nur, wenn sie in der config.yml steht – sonst die mitgelieferte (Werte darin haben eigene
        // Vorgaben, die sonst von getDouble(…, 0) überdeckt würden)
        ConfigurationSection prices = c.contains("ankauf.preise", true) || c.getDefaults() == null
                ? c.getConfigurationSection("ankauf.preise")
                : c.getDefaults().getConfigurationSection("ankauf.preise");
        if (prices != null) {
            for (String key : prices.getKeys(false)) {
                ConfigurationSection s = prices.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                String k = key.toLowerCase(Locale.ROOT);
                buyback.add(new Buyback.Offer(k.contains(":") ? k : "minecraft:" + k,
                        Money.ofTaler(s.getDouble("preis", 0)), Math.max(1, s.getInt("tag", 256))));
            }
        }
        buybackTiers = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("ankauf.stufen")) {
            Object from = m.get("ab");
            Object pct = m.get("prozent");
            if (from instanceof Number f && pct instanceof Number p) {
                buybackTiers.add(new Buyback.Tier(Math.max(0, f.doubleValue()), clamp(p.intValue(), 0, 100)));
            }
        }

        rewardMinMinutes = clamp(c.getInt("belohnung.mindest-minuten", 15), 0, 600);
        rewardBase = taler(c, "belohnung.taler", 25);
        rewardPerDay = taler(c, "belohnung.plus-pro-serientag", 5);
        rewardMaxStreak = clamp(c.getInt("belohnung.max-serie", 7), 1, 365);
        rewardItems = c.getStringList("belohnung.gegenstaende");
        rewardWeekItems = c.getStringList("belohnung.gegenstaende-ab-serie-7");

        bountyMinimum = taler(c, "kopfgeld.mindestens", 50);

        sidebarDefault = c.getBoolean("anzeige.leiste-standard", true);
        address = c.getString("anzeige.adresse", "moinmorni.net");
    }

    public static Settings load(FileConfiguration c) {
        Settings s = new Settings(c);
        Money.symbol(s.symbol);
        return s;
    }

    private static long taler(FileConfiguration c, String path, double fallback) {
        return Math.max(0, Money.ofTaler(c.getDouble(path, fallback)));
    }

    private static long hours(double h) {
        return Math.max(60_000L, Math.round(h * 3_600_000));
    }

    private static SafeSpot.Band band(FileConfiguration c, String path, int min, int max) {
        List<Integer> v = c.getIntegerList(path);
        if (v.size() == 2 && v.get(0) >= 0 && v.get(1) > v.get(0)) {
            return new SafeSpot.Band(v.get(0), v.get(1));
        }
        return new SafeSpot.Band(min, max);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
