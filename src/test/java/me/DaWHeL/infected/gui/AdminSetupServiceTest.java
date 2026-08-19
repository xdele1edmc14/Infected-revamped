package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSetupServiceTest {
    private InfectedPlugin plugin;
    private YamlConfiguration config;
    private AdminSetupService service;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        service = new AdminSetupService(plugin, new SpawnRepository(plugin));
    }

    @Test
    void savesExactInfectedSpawn() {
        service.setInfectedSpawn(location("arena", 12.75, 64.5, -4.25, 90f, 5f));

        AdminSetupService.StoredLocation spawn = service.infectedSpawn().orElseThrow();
        assertAll(
                () -> assertEquals("arena", spawn.world()),
                () -> assertEquals(12.75, spawn.x()),
                () -> assertEquals(64.5, spawn.y()),
                () -> assertEquals(-4.25, spawn.z()),
                () -> assertEquals(90f, spawn.yaw()),
                () -> assertEquals(5f, spawn.pitch())
        );
        verify(plugin).saveConfig();
    }

    @Test
    void savesTeleportPointAsConfigDataOnly() {
        service.saveTeleportPoint(
                SpawnRole.INFECTED_RELEASE,
                "Alpha",
                location("arena", 2.25, 70.0, 9.75, 45f, 0f));

        AdminSetupService.TeleportPoint point = service.teleportPoints(SpawnRole.INFECTED_RELEASE).getFirst();
        assertAll(
                () -> assertEquals("Alpha", point.name()),
                () -> assertEquals("arena", point.location().world()),
                () -> assertEquals(2.25, point.location().x()),
                () -> assertEquals(70.0, point.location().y()),
                () -> assertEquals(9.75, point.location().z()),
                () -> assertTrue(service.teleportPoints(SpawnRole.SURVIVOR).isEmpty())
        );
        verify(plugin).saveConfig();
    }

    @Test
    void rejectsAmbiguousTeleportPointNames() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.saveTeleportPoint("", location("arena", 0, 64, 0, 0, 0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.saveTeleportPoint("north.spawn", location("arena", 0, 64, 0, 0, 0)))
        );
        verify(plugin, never()).saveConfig();
    }

    @Test
    void listsTeleportPointsInStableCaseInsensitiveOrder() {
        config.set("spawns.survivor.zulu.world", "arena");
        config.set("spawns.survivor.zulu.x", 3);
        config.set("spawns.survivor.Alpha.world", "arena");
        config.set("spawns.survivor.Alpha.x", 1);
        config.set("spawns.survivor.bravo.world", "arena");
        config.set("spawns.survivor.bravo.x", 2);

        List<String> names = service.teleportPoints().stream()
                .map(AdminSetupService.TeleportPoint::name)
                .toList();

        assertEquals(List.of("Alpha", "bravo", "zulu"), names);
    }

    @Test
    void reportsReadinessAndExistingSettings() {
        config.set("infected-spawn.world", "arena");
        config.set("spawns.survivor.mid.world", "arena");
        config.set("spawns.infected-release.mid.world", "arena");
        config.set("spawns.infected-respawn.mid.world", "arena");
        config.set("settings.starting-zombies", 4);
        config.set("settings.infected-teleport-delay", 12);
        config.set("settings.teleport-batch-size", 6);
        config.set("settings.teleport-delay", 40);

        AdminSetupService.SetupSnapshot snapshot = service.snapshot(8, 2);

        assertAll(
                () -> assertTrue(snapshot.ready()),
                () -> assertEquals(1, snapshot.teleportPointCount()),
                () -> assertEquals(1, snapshot.survivorSpawnCount()),
                () -> assertEquals(1, snapshot.infectedReleaseSpawnCount()),
                () -> assertEquals(1, snapshot.infectedRespawnSpawnCount()),
                () -> assertEquals(8, snapshot.survivors()),
                () -> assertEquals(2, snapshot.infected()),
                () -> assertEquals(4, snapshot.startingInfected()),
                () -> assertEquals(12, snapshot.infectedTeleportDelay()),
                () -> assertEquals(6, snapshot.teleportBatchSize()),
                () -> assertEquals(40, snapshot.teleportDelayTicks())
        );
    }

    @Test
    void readinessRequiresEverySpawnRoleAndValidPlayerCounts() {
        config.set("infected-spawn.world", "arena");
        config.set("spawns.survivor.mid.world", "arena");
        config.set("settings.starting-zombies", 2);
        config.set("settings.teleport-batch-size", 5);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);

        AdminSetupService.SetupSnapshot snapshot = service.snapshot(2, 0);

        assertAll(
                () -> assertFalse(snapshot.ready()),
                () -> assertEquals(0, snapshot.infectedReleaseSpawnCount()),
                () -> assertEquals(0, snapshot.infectedRespawnSpawnCount()),
                () -> assertEquals(List.of(
                        "Infected release spawns are missing or unavailable.",
                        "Infected respawn spawns are missing or unavailable.",
                        "Starting infected must be lower than the participant count."
                ), snapshot.validationErrors())
        );
    }

    @Test
    void clearingAndDeletingOnlyRemoveConfigurationEntries() {
        config.set("infected-spawn.world", "arena");
        config.set("spawns.infected-respawn.mid.world", "arena");

        assertTrue(service.clearInfectedSpawn());
        assertTrue(service.deleteTeleportPoint(SpawnRole.INFECTED_RESPAWN, "mid"));
        assertFalse(config.contains("infected-spawn"));
        assertFalse(config.contains("spawns.infected-respawn.mid"));
        verify(plugin, times(2)).saveConfig();
    }

    private static Location location(String worldName, double x, double y, double z, float yaw, float pitch) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
