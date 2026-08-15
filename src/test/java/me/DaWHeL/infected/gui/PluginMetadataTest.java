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
                () -> assertTrue(metadata.isConfigurationSection("commands.infected")),
                () -> assertEquals("infected.admin", metadata.getString("commands.infected.permission")),
                () -> assertEquals("/infected [gui addteleport <name>]",
                        metadata.getString("commands.infected.usage")),
                () -> assertTrue(metadata.isConfigurationSection("commands.startinfected")),
                () -> assertTrue(metadata.isConfigurationSection("commands.addteleport")),
                () -> assertTrue(metadata.isConfigurationSection("commands.stopinfected"))
        );
    }
}
