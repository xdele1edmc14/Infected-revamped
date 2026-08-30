package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSetupServiceTest {
    private InfectedPlugin plugin;
    private Server server;
    private World world;
    private YamlConfiguration config;
    private AdminSetupService service;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        server = mock(Server.class);
        world = safeWorld();
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld("arena")).thenReturn(world);
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
                        () -> service.saveTeleportPoint("north.spawn", location("arena", 0, 64, 0, 0, 0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.saveTeleportPoint("close", location("arena", 0, 64, 0, 0, 0))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.saveTeleportPoint("this-name-is-more-than-thirty-two-characters",
                                location("arena", 0, 64, 0, 0, 0)))
        );
        verify(plugin, never()).saveConfig();
    }

    @Test
    void rejectsCaseInsensitiveDuplicatesWithinOnlyTheSameRole() {
        service.saveTeleportPoint(SpawnRole.SURVIVOR, "North",
                location("arena", 0, 64, 0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> service.saveTeleportPoint(
                SpawnRole.SURVIVOR, "north", location("arena", 1, 64, 1, 0, 0)));
        assertDoesNotThrow(() -> service.saveTeleportPoint(
                SpawnRole.INFECTED_RELEASE, "north", location("arena", 1, 64, 1, 0, 0)));
    }

    @Test
    void listsTeleportPointsInStableCaseInsensitiveOrder() {
        config.set("spawns.survivor.zulu.world", "arena");
        config.set("spawns.survivor.zulu.x", 3);
        config.set("spawns.survivor.zulu.y", 64);
        config.set("spawns.survivor.zulu.z", 0);
        config.set("spawns.survivor.Alpha.world", "arena");
        config.set("spawns.survivor.Alpha.x", 1);
        config.set("spawns.survivor.Alpha.y", 64);
        config.set("spawns.survivor.Alpha.z", 0);
        config.set("spawns.survivor.bravo.world", "arena");
        config.set("spawns.survivor.bravo.x", 2);
        config.set("spawns.survivor.bravo.y", 64);
        config.set("spawns.survivor.bravo.z", 0);

        List<String> names = service.teleportPoints().stream()
                .map(AdminSetupService.TeleportPoint::name)
                .toList();

        assertEquals(List.of("Alpha", "bravo", "zulu"), names);
    }

    @Test
    void reportsReadinessAndExistingSettings() {
        config.set("infected-spawn.world", "arena");
        setCoordinates("infected-spawn", 0, 64, 0);
        config.set("spawns.survivor.mid.world", "arena");
        setCoordinates("spawns.survivor.mid", 0, 64, 0);
        config.set("spawns.infected-release.mid.world", "arena");
        setCoordinates("spawns.infected-release.mid", 0, 64, 0);
        config.set("spawns.infected-respawn.mid.world", "arena");
        setCoordinates("spawns.infected-respawn.mid", 0, 64, 0);
        config.set("settings.starting-zombies", 4);
        config.set("settings.infected-teleport-delay", 12);
        config.set("settings.teleport-batch-size", 6);
        config.set("settings.teleport-delay", 40);
        config.set("settings.minimum-players", 6);
        config.set("settings.start-countdown-seconds", 7);
        config.set("settings.round-time-limit-seconds", 900);

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
                () -> assertEquals(40, snapshot.teleportDelayTicks()),
                () -> assertEquals(6, snapshot.minimumPlayers()),
                () -> assertEquals(7, snapshot.startCountdownSeconds()),
                () -> assertEquals(900, snapshot.roundTimeLimitSeconds())
        );
    }

    @Test
    void invalidLifecycleControlsMakeSetupNotReady() {
        config.set("infected-spawn.world", "arena");
        setCoordinates("infected-spawn", 0, 64, 0);
        config.set("spawns.survivor.mid.world", "arena");
        setCoordinates("spawns.survivor.mid", 0, 64, 0);
        config.set("spawns.infected-release.mid.world", "arena");
        setCoordinates("spawns.infected-release.mid", 0, 64, 0);
        config.set("spawns.infected-respawn.mid.world", "arena");
        setCoordinates("spawns.infected-respawn.mid", 0, 64, 0);
        config.set("settings.starting-zombies", 1);
        config.set("settings.teleport-batch-size", 5);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);
        config.set("settings.minimum-players", 1);
        config.set("settings.start-countdown-seconds", -1);
        config.set("settings.round-time-limit-seconds", -1);

        AdminSetupService.SetupSnapshot snapshot = service.snapshot(3, 0);

        assertFalse(snapshot.ready());
        assertEquals(List.of(
                "Minimum players must be at least 2.",
                "Start countdown cannot be negative.",
                "Round time limit cannot be negative."
        ), snapshot.validationErrors());
    }

    @Test
    void readinessRequiresEverySpawnRoleAndValidPlayerCounts() {
        config.set("infected-spawn.world", "arena");
        setCoordinates("infected-spawn", 0, 64, 0);
        config.set("spawns.survivor.mid.world", "arena");
        setCoordinates("spawns.survivor.mid", 0, 64, 0);
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
                        "Starting infected must be lower than the minimum player count.",
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

    @Test
    void readinessRejectsUnsafeInfectedRespawnPoints() {
        setStoredLocation("infected-spawn", 0, 64, 0);
        setStoredLocation("spawns.survivor.mid", 0, 64, 0);
        setStoredLocation("spawns.infected-release.mid", 0, 64, 0);
        setStoredLocation("spawns.infected-respawn.mid", 8, 64, 8);
        Block unsafeGround = mock(Block.class);
        when(world.getBlockAt(8, 63, 8)).thenReturn(unsafeGround);
        when(unsafeGround.getType()).thenReturn(Material.AIR);
        when(unsafeGround.isPassable()).thenReturn(true);
        config.set("settings.starting-zombies", 1);
        config.set("settings.minimum-players", 2);
        config.set("settings.teleport-batch-size", 5);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);

        AdminSetupService.SetupSnapshot snapshot = service.snapshot(2, 0);

        assertAll(
                () -> assertFalse(snapshot.ready()),
                () -> assertEquals(0, snapshot.infectedRespawnSpawnCount()),
                () -> assertTrue(snapshot.validationErrors().contains(
                        "Infected respawn spawns are missing or unavailable."))
        );
    }

    private void setStoredLocation(String path, double x, double y, double z) {
        config.set(path + ".world", "arena");
        setCoordinates(path, x, y, z);
    }

    private void setCoordinates(String path, double x, double y, double z) {
        config.set(path + ".x", x);
        config.set(path + ".y", y);
        config.set(path + ".z", z);
    }

    private static World safeWorld() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Block ground = mock(Block.class);
        Block air = mock(Block.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(anyInt(), eq(63), anyInt())).thenReturn(ground);
        when(world.getBlockAt(anyInt(), eq(64), anyInt())).thenReturn(air);
        when(world.getBlockAt(anyInt(), eq(65), anyInt())).thenReturn(air);
        when(ground.getType()).thenReturn(Material.STONE);
        when(ground.isPassable()).thenReturn(false);
        when(ground.getBoundingBox()).thenAnswer(invocation ->
                new org.bukkit.util.BoundingBox(-1000, 63, -1000, 1000, 64, 1000));
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        return world;
    }

    private static Location location(String worldName, double x, double y, double z, float yaw, float pitch) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
