package net.morni.smp;

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
        assertTrue(s.shopWares.size() >= 150 && s.shopWares.size() <= 260, "Waren: " + s.shopWares.size());
        assertEquals(10, s.shopCategories.size());
        assertEquals(4, s.buybackTiers.size());
        assertTrue(s.shopEnabled);
        assertEquals(40, s.shopMaxSellPercent);
        net.morni.smp.shop.Ware wheat = s.shopWares.stream().filter(w -> w.key().equals("minecraft:wheat"))
                .findFirst().orElseThrow();
        assertEquals(150, wheat.buy());
        assertEquals(50, wheat.sell());
        assertEquals(256, wheat.daily());
        assertEquals("essen", wheat.category());
        assertTrue(s.shopWares.stream().anyMatch(w -> !w.months().isEmpty()), "Saison-Waren");
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
