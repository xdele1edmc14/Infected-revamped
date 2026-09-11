package me.DaWHeL.infected;

import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoundRulesTest {

    @Test
    void bundledConfigDefinesBothTrackingCompassPolicies() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        MemoryConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        RoundRules timed = RoundRules.from(config, RoundMode.TIME_LIMIT);
        RoundRules deathmatch = RoundRules.from(config, RoundMode.DEATHMATCH);

        assertTrue(timed.trackingCompassEnabled());
        assertEquals(300, timed.trackingCompassActivationSeconds());
        assertTrue(deathmatch.trackingCompassEnabled());
        assertEquals(10, deathmatch.trackingCompassDisableBelowInfected());
    }

    @Test
    void timeLimitUsesItsOwnStartingCountDurationAndExactlyOneLife() {
        MemoryConfiguration config = configuredModes();

        RoundRules rules = RoundRules.from(config, RoundMode.TIME_LIMIT);

        assertEquals(RoundMode.TIME_LIMIT, rules.mode());
        assertEquals(2, rules.startingInfected());
        assertEquals(1, rules.infectedLives());
        assertEquals(480, rules.timeLimitSeconds());
        assertTrue(rules.bossBarEnabled());
        assertEquals("&eRound ends in &f{time}", rules.bossBarTitle());
        assertEquals(BarColor.BLUE, rules.bossBarColor());
        assertEquals(BarStyle.SEGMENTED_10, rules.bossBarStyle());
        assertTrue(rules.trackingCompassEnabled());
        assertEquals(180, rules.trackingCompassActivationSeconds());
        assertTrue(rules.hasTimeLimit());
    }

    @Test
    void deathmatchUsesItsOwnStartingCountAndRespawnChancesWithoutATimer() {
        MemoryConfiguration config = configuredModes();

        RoundRules rules = RoundRules.from(config, RoundMode.DEATHMATCH);

        assertEquals(RoundMode.DEATHMATCH, rules.mode());
        assertEquals(4, rules.startingInfected());
        assertEquals(5, rules.infectedLives());
        assertEquals(0, rules.timeLimitSeconds());
        assertTrue(rules.trackingCompassEnabled());
        assertEquals(10, rules.trackingCompassDisableBelowInfected());
        assertFalse(rules.hasTimeLimit());
    }

    @Test
    void oldFlatSettingsRemainFallbacksForExistingServers() {
        MemoryConfiguration config = new MemoryConfiguration(configuredModes());
        config.set("settings.starting-zombies", 3);
        config.set("settings.infected-lives", 7);

        RoundRules timed = RoundRules.from(config, RoundMode.TIME_LIMIT);
        RoundRules deathmatch = RoundRules.from(config, RoundMode.DEATHMATCH);

        assertEquals(3, timed.startingInfected());
        assertEquals(600, timed.timeLimitSeconds());
        assertEquals(3, deathmatch.startingInfected());
        assertEquals(7, deathmatch.infectedLives());
    }

    @Test
    void clampsDangerousLifeAndTimerValuesButLeavesStartingCountForValidation() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("settings.modes.time-limit.starting-zombies", 0);
        config.set("settings.modes.time-limit.time-limit-seconds", -30);
        config.set("settings.modes.time-limit.boss-bar.color", "not-a-color");
        config.set("settings.modes.time-limit.boss-bar.style", "not-a-style");
        config.set("settings.modes.deathmatch.infected-lives", 1_000_000);

        RoundRules timed = RoundRules.from(config, RoundMode.TIME_LIMIT);
        RoundRules deathmatch = RoundRules.from(config, RoundMode.DEATHMATCH);

        assertEquals(0, timed.startingInfected());
        assertEquals(1, timed.timeLimitSeconds());
        assertEquals(BarColor.YELLOW, timed.bossBarColor());
        assertEquals(BarStyle.SOLID, timed.bossBarStyle());
        assertEquals(InfectedLifeTracker.MAX_LIVES, deathmatch.infectedLives());
    }

    @Test
    void modeParsingAndCyclingUseStableConfigNames() {
        assertEquals(RoundMode.TIME_LIMIT, RoundMode.parse("time-limit"));
        assertEquals(RoundMode.TIME_LIMIT, RoundMode.parse("TIME_LIMIT"));
        assertEquals(RoundMode.DEATHMATCH, RoundMode.parse("deathmatch"));
        assertEquals(RoundMode.DEATHMATCH, RoundMode.parse("unknown"));
        assertEquals(RoundMode.DEATHMATCH, RoundMode.parse(null));
        assertEquals(RoundMode.DEATHMATCH, RoundMode.TIME_LIMIT.next());
        assertEquals(RoundMode.TIME_LIMIT, RoundMode.DEATHMATCH.next());
    }

    private static MemoryConfiguration configuredModes() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("settings.starting-zombies", 1);
        config.set("settings.infected-lives", 3);
        config.set("settings.modes.time-limit.starting-zombies", 2);
        config.set("settings.modes.time-limit.time-limit-seconds", 480);
        config.set("settings.modes.time-limit.boss-bar.enabled", true);
        config.set("settings.modes.time-limit.boss-bar.title", "&eRound ends in &f{time}");
        config.set("settings.modes.time-limit.boss-bar.color", "BLUE");
        config.set("settings.modes.time-limit.boss-bar.style", "SEGMENTED_10");
        config.set("settings.modes.time-limit.tracking-compass.enabled", true);
        config.set("settings.modes.time-limit.tracking-compass.give-below-minutes", 3);
        config.set("settings.modes.deathmatch.starting-zombies", 4);
        config.set("settings.modes.deathmatch.infected-lives", 5);
        config.set("settings.modes.deathmatch.tracking-compass.enabled", true);
        config.set("settings.modes.deathmatch.tracking-compass.remove-below-zombies", 10);
        return config;
    }
}
