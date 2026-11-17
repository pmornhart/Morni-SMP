package de.moinmorni.smp.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Bestehende Server bekommen die neuen Preise – außer der Betreiber hat seine Liste selbst geändert. */
class PriceListTest {

    private static YamlConfiguration load(String res) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                PriceListTest.class.getResourceAsStream(res), StandardCharsets.UTF_8));
    }

    @Test
    void unchangedOldListIsReplacedOnce() {
        YamlConfiguration old = load("/config-0.2.0.yml");
        String fp = PriceList.fingerprint(old.getConfigurationSection("haendler.waren"));
        assertTrue(PriceList.OLD.contains(fp), "Fingerabdruck der Liste aus 0.2.0: " + fp);
        assertEquals(PriceList.Result.UPDATED, PriceList.migrate(old));
        assertFalse(old.contains("haendler.waren", true), "alte Liste weg – die mitgelieferte gilt");
        assertEquals(PriceList.CURRENT, old.getInt("haendler.preisliste"));
        assertTrue(old.contains("haendler.an", true), "der Rest bleibt");
        assertEquals(PriceList.Result.NONE, PriceList.migrate(old), "nur einmal");
    }

    @Test
    void ownListIsKept() {
        YamlConfiguration own = load("/config-0.2.0.yml");
        own.set("haendler.waren.erze.diamond.kauf", 99);
        assertEquals(PriceList.Result.KEPT, PriceList.migrate(own));
        assertEquals(99, own.getInt("haendler.waren.erze.diamond.kauf"));
        assertEquals(PriceList.Result.NONE, PriceList.migrate(own));
    }

    @Test
    void newBundledListIsCurrentAndNoListMeansNothingToDo() {
        YamlConfiguration now = load("/config.yml");
        assertEquals(PriceList.CURRENT, now.getInt("haendler.preisliste"));
        assertFalse(PriceList.OLD.contains(PriceList.fingerprint(now.getConfigurationSection("haendler.waren"))));
        assertEquals(PriceList.Result.NONE, PriceList.migrate(now));
        YamlConfiguration small = new YamlConfiguration();
        small.set("belohnung.mindest-minuten", 0);
        assertEquals(PriceList.Result.NONE, PriceList.migrate(small), "ältere config.yml ohne Liste: Vorgaben gelten");
    }
}
