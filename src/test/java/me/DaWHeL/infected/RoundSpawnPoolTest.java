package me.DaWHeL.infected;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoundSpawnPoolTest {

    @Test
    void deduplicatesChunkRequestsForTwoHundredFiftyDistributedPlayers() {
        InfectedPlugin plugin = plugin();
        SpawnRepository repository = mock(SpawnRepository.class);
        World world = mock(World.class);
        Location spawn = new Location(world, 8.5, 64, 8.5);
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(spawn));
        for (SpawnRole role : SpawnRole.values()) {
            when(repository.loadedLocations(role)).thenReturn(List.of(spawn));
        }

        Map<String, Chunk> chunks = new LinkedHashMap<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0) + ":" + invocation.getArgument(1);
            Chunk chunk = chunks.computeIfAbsent(key, ignored -> {
                Chunk created = mock(Chunk.class);
                when(created.addPluginChunkTicket(plugin)).thenReturn(true);
                return created;
            });
            return CompletableFuture.completedFuture(chunk);
        });

        RoundSpawnPool pool = RoundSpawnPool.preload(plugin, repository, Map.of(
                SpawnRole.SURVIVOR, 125,
                SpawnRole.INFECTED_RELEASE, 125
        )).join();

        assertEquals(2, chunks.size());
        pool.releaseTickets(plugin);
        chunks.values().forEach(chunk -> verify(chunk).removePluginChunkTicket(plugin));
    }

    @Test
    void reportsAnUnavailableWorldThroughTheFutureInsteadOfThrowingFromStart() {
        InfectedPlugin plugin = plugin();
        SpawnRepository repository = mock(SpawnRepository.class);
        Location unavailable = mock(Location.class);
        when(unavailable.clone()).thenReturn(unavailable);
        when(unavailable.getWorld()).thenThrow(new IllegalArgumentException("World unloaded"));
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(unavailable));
        for (SpawnRole role : SpawnRole.values()) {
            when(repository.loadedLocations(role)).thenReturn(List.of(unavailable));
        }

        CompletableFuture<RoundSpawnPool> preload = assertDoesNotThrow(() ->
                RoundSpawnPool.preload(plugin, repository, Map.of()));

        assertThrows(CompletionException.class, preload::join);
    }

    @Test
    void releasesEveryTicketEvenWhenOneChunkRejectsRemoval() {
        InfectedPlugin plugin = plugin();
        SpawnRepository repository = mock(SpawnRepository.class);
        World world = mock(World.class);
        Location firstLocation = new Location(world, 8.5, 64, 8.5);
        Location secondLocation = new Location(world, 24.5, 64, 8.5);
        when(repository.loadedHoldingSpawn()).thenReturn(Optional.of(firstLocation));
        EnumMap<SpawnRole, List<Location>> locations = new EnumMap<>(SpawnRole.class);
        locations.put(SpawnRole.SURVIVOR, List.of(firstLocation, secondLocation));
        for (SpawnRole role : SpawnRole.values()) {
            when(repository.loadedLocations(role)).thenReturn(locations.getOrDefault(role, List.of()));
        }

        Chunk first = mock(Chunk.class);
        Chunk second = mock(Chunk.class);
        when(first.addPluginChunkTicket(plugin)).thenReturn(true);
        when(second.addPluginChunkTicket(plugin)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(first));
        when(world.getChunkAtAsync(1, 0, false)).thenReturn(CompletableFuture.completedFuture(second));
        doThrow(new IllegalStateException("ticket removal failed"))
                .when(first).removePluginChunkTicket(plugin);

        RoundSpawnPool pool = RoundSpawnPool.preload(plugin, repository, Map.of()).join();

        assertDoesNotThrow(() -> pool.releaseTickets(plugin));
        verify(first).removePluginChunkTicket(plugin);
        verify(second).removePluginChunkTicket(plugin);
    }

    private static InfectedPlugin plugin() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        return plugin;
    }
}
