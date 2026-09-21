package dev.smpcristalix.mobbalance.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobBalanceSettingsTest {

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void defaultConfigKeepsApprovedFeatureSwitches() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertTrue(stream != null);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        MobBalanceSettings settings = MobBalanceSettings.from(yaml);
        assertTrue(settings.slimeEnabled());
        assertTrue(settings.witherSkeletonEnabled());
        assertTrue(settings.piglinCrossbowBuff());
        assertEquals(3, settings.pillagerQuickChargeLevel());
    }

    @Test
    void normalizesAndValidatesSpawnReasons() {
        YamlConfiguration valid = new YamlConfiguration();
        valid.set("general.spawn-reasons", List.of("natural"));
        assertTrue(MobBalanceSettings.from(valid).acceptsSpawnReason("NATURAL"));

        YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("general.spawn-reasons", List.of("not-a-reason"));
        assertThrows(IllegalArgumentException.class, () -> MobBalanceSettings.from(invalid));
    }

    @Test
    void rejectsNonFiniteNumbersAndHonorsDisabledTypes() {
        YamlConfiguration nonFinite = new YamlConfiguration();
        nonFinite.set("stats.health-bonus-max-percent", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> MobBalanceSettings.from(nonFinite));

        YamlConfiguration disabled = new YamlConfiguration();
        disabled.set("slime.enabled", false);
        disabled.set("wither-skeleton.enabled", false);
        disabled.set("piglin.crossbow-buff", false);
        MobBalanceSettings settings = MobBalanceSettings.from(disabled);
        assertFalse(settings.slimeEnabled());
        assertFalse(settings.witherSkeletonEnabled());
        assertFalse(settings.piglinCrossbowBuff());
    }
}
