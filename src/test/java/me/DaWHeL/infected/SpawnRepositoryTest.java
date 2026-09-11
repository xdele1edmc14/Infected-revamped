package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnRepositoryTest {
    private InfectedPlugin plugin;
    private Server server;
    private YamlConfiguration config;
    private SpawnRepository repository;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        server = mock(Server.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        repository = new SpawnRepository(plugin);
    }

    @Test
    void migratesLegacyPointsIntoIndependentRolesWithoutOverwritingOrDeleting() {
        setStored("teleports.North", "arena", 4.5, 70, -2.25, 90, 5);
        setStored("spawns.infected-release.North", "release-world", 40, 80, 20, 0, 0);

        SpawnRepository.MigrationResult result = repository.migrateLegacyTeleports();

        assertAll(
                () -> assertTrue(result.migrated()),
                () -> assertEquals(2, result.copiedEntries()),
                () -> assertEquals("arena", repository.points(SpawnRole.SURVIVOR).getFirst().location().world()),
                () -> assertEquals("release-world",
                        repository.points(SpawnRole.INFECTED_RELEASE).getFirst().location().world()),
                () -> assertEquals("arena",
                        repository.points(SpawnRole.INFECTED_RESPAWN).getFirst().location().world()),
                () -> assertTrue(config.contains("teleports.North")),
                () -> assertTrue(config.getBoolean("migrations.role-spawns"))
        );
        verify(plugin).saveConfig();
    }

    @Test
    void migrationRetriesMalformedLegacyPathsAfterTheyAreRepaired() {
        config.set("teleports.Broken.x", 12);

        SpawnRepository.MigrationResult first = repository.migrateLegacyTeleports();
        boolean markedAfterFirst = config.getBoolean("migrations.role-spawns", false);
        setStored("teleports.Broken", "arena", 12, 70, 4, 0, 0);
        SpawnRepository.MigrationResult repaired = repository.migrateLegacyTeleports();
        SpawnRepository.MigrationResult complete = repository.migrateLegacyTeleports();

        assertAll(
                () -> assertTrue(first.migrated()),
                () -> assertEquals(List.of("teleports.Broken"), first.skippedPaths()),
                () -> assertFalse(markedAfterFirst),
                () -> assertTrue(repaired.migrated()),
                () -> assertEquals(3, repaired.copiedEntries()),
                () -> assertEquals(List.of(), repaired.skippedPaths()),
                () -> assertFalse(complete.migrated())
        );
        verify(plugin, times(1)).saveConfig();
    }

    @Test
    void savesAndDeletesOnlyTheRequestedRoleAtFullPrecision() {
        Location location = location("arena", 2.25, 70.5, 9.75, 45, 3);

        repository.savePoint(SpawnRole.INFECTED_RESPAWN, "Alpha", location);

        SpawnRepository.NamedSpawn point = repository.points(SpawnRole.INFECTED_RESPAWN).getFirst();
        assertAll(
                () -> assertEquals("Alpha", point.name()),
                () -> assertEquals("arena", point.location().world()),
                () -> assertEquals(2.25, point.location().x()),
                () -> assertEquals(70.5, point.location().y()),
                () -> assertEquals(9.75, point.location().z()),
                () -> assertTrue(repository.points(SpawnRole.SURVIVOR).isEmpty()),
                () -> assertTrue(repository.deletePoint(SpawnRole.INFECTED_RESPAWN, "Alpha")),
                () -> assertFalse(config.contains("spawns.infected-respawn.Alpha"))
        );
        verify(plugin, times(2)).saveConfig();
    }

    @Test
    void listsPointsInStableCaseInsensitiveOrder() {
        setStored("spawns.survivor.zulu", "arena", 3, 64, 0, 0, 0);
        setStored("spawns.survivor.Alpha", "arena", 1, 64, 0, 0, 0);
        setStored("spawns.survivor.bravo", "arena", 2, 64, 0, 0, 0);

        assertEquals(List.of("Alpha", "bravo", "zulu"), repository.points(SpawnRole.SURVIVOR).stream()
                .map(SpawnRepository.NamedSpawn::name)
                .toList());
        verify(plugin, never()).saveConfig();
    }

    @Test
    void resolvesOnlyLoadedWorldsForTheRequestedRole() {
        setStored("spawns.survivor.loaded", "arena", 1, 64, 2, 30, 4);
        setStored("spawns.survivor.missing", "missing", 9, 70, 9, 0, 0);
        setStored("spawns.infected-release.other", "release", 20, 80, 20, 0, 0);
        World arena = mock(World.class);
        when(server.getWorld("arena")).thenReturn(arena);

        List<Location> loaded = repository.loadedLocations(SpawnRole.SURVIVOR);
        Location selected = repository.randomLocation(SpawnRole.SURVIVOR, new Random(1)).orElseThrow();

        assertAll(
                () -> assertEquals(1, loaded.size()),
                () -> assertEquals(arena, loaded.getFirst().getWorld()),
                () -> assertEquals(1.0, loaded.getFirst().getX()),
                () -> assertEquals(30f, loaded.getFirst().getYaw()),
                () -> assertEquals(arena, selected.getWorld())
        );
    }

    @Test
    void readsTheExistingHoldingSpawnSeparatelyFromArenaRoles() {
        setStored("infected-spawn", "holding", 5, 75, 5, 180, 0);

        SpawnRepository.StoredSpawn holding = repository.holdingSpawn().orElseThrow();

        assertAll(
                () -> assertEquals("holding", holding.world()),
                () -> assertEquals(5.0, holding.x()),
                () -> assertTrue(repository.points(SpawnRole.INFECTED_RELEASE).isEmpty())
        );
    }

    @Test
    void rejectsSpawnRecordsWhenAnyNumericFieldIsMissing() {
        for (String field : List.of("x", "y", "z", "yaw", "pitch")) {
            config.set("spawns.survivor.test", null);
            setStored("spawns.survivor.test", "arena", 1, 64, 2, 30, 4);
            config.set("spawns.survivor.test." + field, null);

            assertTrue(repository.points(SpawnRole.SURVIVOR).isEmpty(),
                    () -> "Missing " + field + " must invalidate the spawn record.");
        }
    }

    @Test
    void rejectsNonNumericAndNonFiniteSpawnCoordinates() {
        setStored("spawns.survivor.text", "arena", 1, 64, 2, 30, 4);
        config.set("spawns.survivor.text.x", "1.0");
        setStored("spawns.survivor.infinite", "arena", 1, 64, 2, 30, 4);
        config.set("spawns.survivor.infinite.pitch", Double.POSITIVE_INFINITY);

        assertTrue(repository.points(SpawnRole.SURVIVOR).isEmpty());
    }

    @Test
    void refusesToSaveNonFiniteSpawnCoordinates() {
        Location location = location("arena", Double.NaN, 70, 9, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> repository.savePoint(SpawnRole.SURVIVOR, "broken", location));
        assertFalse(config.contains("spawns.survivor.broken"));
        verify(plugin, never()).saveConfig();
    }

    private void setStored(String path, String world, double x, double y, double z, double yaw, double pitch) {
        config.set(path + ".world", world);
        config.set(path + ".x", x);
        config.set(path + ".y", y);
        config.set(path + ".z", z);
        config.set(path + ".yaw", yaw);
        config.set(path + ".pitch", pitch);
    }

    private static Location location(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        return new Location(world, x, y, z, yaw, pitch);
    }
}
