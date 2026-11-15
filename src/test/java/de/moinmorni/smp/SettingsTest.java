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
        assertTrue(s.buyback.size() > 20, "Ankaufspreise: " + s.buyback.size());
        assertEquals(4, s.buybackTiers.size());
        assertEquals(50, s.buyback.stream().filter(o -> o.key().equals("minecraft:wheat")).findFirst().orElseThrow()
                .unitPrice());
    }
}
