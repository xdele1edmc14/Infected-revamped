package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeleportManagerTest {
    private InfectedPlugin plugin;
    private SpawnRepository spawnRepository;
    private PluginTaskScheduler scheduler;
    private BukkitTask task;
    private TeleportManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        spawnRepository = mock(SpawnRepository.class);
        scheduler = mock(PluginTaskScheduler.class);
        task = mock(BukkitTask.class);
        when(scheduler.runRepeating(org.mockito.ArgumentMatchers.any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);
        manager = new TeleportManager(plugin, spawnRepository, scheduler);
    }

    @Test
    void missingRoleSpawnsCompleteImmediatelyWithFailure() {
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RELEASE)).thenReturn(List.of());
        AtomicReference<TeleportBatchResult> completion = new AtomicReference<>();

        BukkitTask scheduled = manager.teleportPlayersBatch(
                SpawnRole.INFECTED_RELEASE, List.of(mock(Player.class)), 5, 40, completion::set);

        assertAll(
                () -> assertNull(scheduled),
                () -> assertFalse(completion.get().success()),
                () -> assertEquals("No infected release spawns are available.", completion.get().error())
        );
        verify(scheduler, never()).runRepeating(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong());
    }

    @Test
    void anEmptyPlayerQueueCompletesSuccessfullyWithoutScheduling() {
        when(spawnRepository.loadedLocations(SpawnRole.SURVIVOR)).thenReturn(List.of(location(0, 64, 0)));
        AtomicReference<TeleportBatchResult> completion = new AtomicReference<>();

        BukkitTask scheduled = manager.teleportPlayersBatch(
                SpawnRole.SURVIVOR, List.of(), 5, 0, completion::set);

        assertNull(scheduled);
        assertTrue(completion.get().success());
        verify(scheduler, never()).runRepeating(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong());
    }

    @Test
    void usesOnlyTheRequestedRoleAndReportsCancelledTeleports() {
        Location release = location(20, 70, 30);
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RELEASE)).thenReturn(List.of(release));
        Player first = player(true);
        Player second = player(false);
        AtomicReference<TeleportBatchResult> completion = new AtomicReference<>();

        manager.teleportPlayersBatch(
                SpawnRole.INFECTED_RELEASE, List.of(first, second), 2, 0, completion::set);

        ArgumentCaptor<Runnable> operation = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runRepeating(operation.capture(), org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq(1L));
        operation.getValue().run();

        ArgumentCaptor<Location> firstDestination = ArgumentCaptor.forClass(Location.class);
        verify(first).teleport(firstDestination.capture());
        assertAll(
                () -> assertEquals(18.5, firstDestination.getValue().getX()),
                () -> assertEquals(70.0, firstDestination.getValue().getY()),
                () -> assertEquals(28.5, firstDestination.getValue().getZ()),
                () -> assertEquals(2, completion.get().attempted()),
                () -> assertEquals(1, completion.get().succeeded()),
                () -> assertEquals(List.of(second.getUniqueId()), completion.get().failedPlayerIds()),
                () -> assertFalse(completion.get().success())
        );
        verify(spawnRepository, never()).loadedLocations(SpawnRole.SURVIVOR);
        verify(task).cancel();
    }

    @Test
    void skipsPlayersWhoAreNoLongerEligibleBeforeTheirBatchRuns() {
        when(spawnRepository.loadedLocations(SpawnRole.SURVIVOR))
                .thenReturn(List.of(location(0, 64, 0)));
        Player removed = player(true);
        AtomicReference<TeleportBatchResult> completion = new AtomicReference<>();

        manager.teleportPlayersBatch(
                SpawnRole.SURVIVOR,
                List.of(removed),
                1,
                0,
                player -> false,
                completion::set
        );

        ArgumentCaptor<Runnable> operation = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runRepeating(operation.capture(),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq(1L));
        operation.getValue().run();

        verify(removed, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
        assertAll(
                () -> assertTrue(completion.get().success()),
                () -> assertEquals(0, completion.get().attempted()),
                () -> assertEquals(0, completion.get().succeeded())
        );
    }

    @Test
    void scopesTeleportHooksToThePlayerWhoseBatchIsRunning() {
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RELEASE))
                .thenReturn(List.of(location(0, 64, 0)));
        Player first = player(true);
        Player second = player(true);
        Set<Player> teleporting = new HashSet<>();
        when(first.teleport(org.mockito.ArgumentMatchers.any(Location.class))).thenAnswer(invocation -> {
            assertEquals(Set.of(first), teleporting);
            return true;
        });

        manager.teleportPlayersBatch(
                SpawnRole.INFECTED_RELEASE,
                List.of(first, second),
                1,
                20,
                player -> true,
                teleporting::add,
                (player, teleported) -> teleporting.remove(player),
                result -> {
                }
        );

        ArgumentCaptor<Runnable> operation = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runRepeating(operation.capture(),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq(20L));
        operation.getValue().run();

        assertTrue(teleporting.isEmpty());
        verify(second, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class));
    }

    private static Player player(boolean teleportResult) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.teleport(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(teleportResult);
        return player;
    }

    private static Location location(double x, double y, double z) {
        return new Location(mock(World.class), x, y, z, 90, 5);
    }
}
