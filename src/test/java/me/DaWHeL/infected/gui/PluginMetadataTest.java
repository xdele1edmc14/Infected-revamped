package me.DaWHeL.infected.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginMetadataTest {
    @Test
    void declaresAdminGuiCommandWithoutRemovingLegacyCommands() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(stream);
        YamlConfiguration metadata = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        assertAll(
                () -> assertEquals("2.0.1", metadata.getString("version")),
                () -> assertTrue(metadata.isConfigurationSection("commands.infected")),
                () -> assertEquals("infected.admin", metadata.getString("commands.infected.permission")),
                () -> assertEquals("/infected [start|stop|reload|status|help|gui addteleport "
                                + "[survivor|release|respawn] <name>]",
                        metadata.getString("commands.infected.usage")),
                () -> assertTrue(metadata.isConfigurationSection("commands.startinfected")),
                () -> assertTrue(metadata.isConfigurationSection("commands.addteleport")),
                () -> assertTrue(metadata.isConfigurationSection("commands.stopinfected")),
                () -> assertFalse(metadata.isConfigurationSection("commands.givefeather"))
        );
    }

    @Test
    void defaultLifecycleNumbersCanStartAMinimumSizedRound() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        int minimumPlayers = config.getInt("settings.minimum-players");
        int startingZombies = config.getInt("settings.starting-zombies");

        assertAll(
                () -> assertTrue(minimumPlayers >= 2),
                () -> assertTrue(startingZombies >= 1),
                () -> assertTrue(startingZombies < minimumPlayers)
        );
    }
}
