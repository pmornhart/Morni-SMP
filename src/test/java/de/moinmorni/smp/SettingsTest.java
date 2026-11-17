package de.moinmorni.smp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Eine alte oder knappe config.yml bekommt alles Fehlende aus der mitgelieferten Datei. */
class SettingsTest {

    @Test
    void missingKeysComeFromTheBundledConfig() throws Exception {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        YamlConfiguration user = new YamlConfiguration();
        user.loadFromString("belohnung:\n  mindest-minuten: 0\n");
        user.setDefaults(defaults);
        user.options().copyDefaults(true);
        Settings s = Settings.load(user);
        assertEquals(0, s.rewardMinMinutes);
        assertEquals(2500, s.rewardBase);
        assertTrue(s.shopWares.size() >= 150 && s.shopWares.size() <= 300, "Waren: " + s.shopWares.size());
        assertEquals(10, s.shopCategories.size());
        assertEquals(4, s.buybackTiers.size());
        assertTrue(s.shopEnabled);
        assertEquals(40, s.shopMaxSellPercent);
        de.moinmorni.smp.shop.Ware wheat = s.shopWares.stream().filter(w -> w.key().equals("minecraft:wheat"))
                .findFirst().orElseThrow();
        assertEquals(150, wheat.buy());
        assertEquals(50, wheat.sell());
        assertEquals(256, wheat.daily());
        assertEquals("essen", wheat.category());
        assertTrue(s.shopWares.stream().anyMatch(w -> !w.months().isEmpty()), "Saison-Waren");
        // Preis-Faktoren je Abteilung (alle 1) und die Börse
        assertEquals(10, s.shopFactors.size());
        assertTrue(s.shopFactors.values().stream().allMatch(f -> f == 1.0));
        assertTrue(s.courseRules.on());
        assertEquals(4.0, s.courseRules.halveAfter());
        assertEquals(6.0, s.courseRules.recoveryHours());
        assertEquals(25, s.courseRules.minPercent());
        assertEquals(300, s.courseRules.maxPercent());
    }

    @Test
    void factorsAndExchangeAreReadAndBounded() throws Exception {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        YamlConfiguration user = new YamlConfiguration();
        user.loadFromString("haendler:\n  faktoren:\n    erze: 2.5\n    holz: 0\n    essen: 500\n"
                + "boerse:\n  an: false\n  min-prozent: 0\n  max-prozent: 5000\n  halbieren-nach: 2\n");
        user.setDefaults(defaults);
        user.options().copyDefaults(true);
        Settings s = Settings.load(user);
        assertEquals(2.5, s.shopFactors.get("erze"));
        assertEquals(100.0, s.shopFactors.get("essen"), "höchstens 100");
        assertTrue(!s.shopFactors.containsKey("holz") || s.shopFactors.get("holz") >= 0.05, "0 gilt nicht");
        assertTrue(!s.courseRules.on());
        assertEquals(1, s.courseRules.minPercent(), "mindestens 1 %");
        assertEquals(1000, s.courseRules.maxPercent(), "höchstens 1000 %");
        assertEquals(2.0, s.courseRules.halveAfter());
    }

    @Test
    void ownShopListReplacesTheBundledOne() throws Exception {
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                SettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        YamlConfiguration user = new YamlConfiguration();
        user.loadFromString("haendler:\n  waren:\n    essen:\n      wheat: {kauf: 2, verkauf: 0.25, tag: 64}\n");
        user.setDefaults(defaults);
        user.options().copyDefaults(true);
        Settings s = Settings.load(user);
        assertEquals(1, s.shopWares.size());
        assertEquals(200, s.shopWares.get(0).buy());
        assertEquals(64, s.shopWares.get(0).daily());
        assertEquals(10, s.shopCategories.size(), "Abteilungen aus der mitgelieferten Datei");
    }
}
