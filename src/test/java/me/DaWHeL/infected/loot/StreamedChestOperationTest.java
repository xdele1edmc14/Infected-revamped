package me.DaWHeL.infected.loot;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamedChestOperationTest {
    @Test
    void loadsGeneratedChunksWithoutGeneratingAndReleasesTemporaryChunksAfterClearing() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Inventory inventory = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        when(world.isChunkGenerated(0, 0)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(chunk));
        when(discovery.discover(region, chunk)).thenReturn(List.of(new DiscoveredChest("chest", inventory)));
        when(chunk.addPluginChunkTicket(plugin)).thenReturn(true);

        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        while (!operation.step(1, 1)) {
            // Run the same bounded work unit a scheduler would run on the following tick.
        }

        assertAll(
                () -> assertTrue(operation.result().success()),
                () -> assertEquals(1, operation.result().affectedChests())
        );
        verify(world).getChunkAtAsync(0, 0, false);
        verify(world, never()).getChunkAt(0, 0, false);
        verify(inventory).clear();
        verify(chunk).addPluginChunkTicket(plugin);
        verify(chunk).removePluginChunkTicket(plugin);
        verify(chunk).unload(false);
    }

    @Test
    void refusesToGenerateTerrainAndDoesNotMutateEarlierDiscoveredChests() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk firstChunk = mock(Chunk.class);
        Inventory inventory = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(firstChunk);
        when(discovery.discover(region, firstChunk)).thenReturn(
                List.of(new DiscoveredChest("chest", inventory)));
        when(world.isChunkLoaded(1, 0)).thenReturn(false);
        when(world.isChunkGenerated(1, 0)).thenReturn(false);

        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        while (!operation.step(2, 1)) {
            // Advance bounded scan/apply work.
        }

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().stream().anyMatch(error -> error.contains("not generated")));
        verify(world, never()).getChunkAt(1, 0, false);
        verify(inventory, never()).clear();
    }

    @Test
    void releasesATemporarilyLoadedChunkWhenChestInspectionFails() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        when(world.isChunkGenerated(0, 0)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(chunk));
        when(discovery.discover(region, chunk)).thenThrow(new IllegalStateException("tile scan failed"));

        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        assertTrue(operation.step(1, 1));

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().getFirst().contains("tile scan failed"));
        verify(chunk).unload(false);
    }

    @Test
    void stopsScanningAndDoesNotMutateWhenARoundStartsBetweenBatches() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk firstChunk = mock(Chunk.class);
        Inventory inventory = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        when(world.isChunkGenerated(0, 0)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(firstChunk));
        when(discovery.discover(region, firstChunk)).thenReturn(
                List.of(new DiscoveredChest("chest", inventory)));
        when(firstChunk.addPluginChunkTicket(plugin)).thenReturn(true);
        AtomicBoolean allowed = new AtomicBoolean(true);
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, allowed::get);

        assertFalse(operation.step(1, 1));
        allowed.set(false);
        assertTrue(operation.step(1, 1));

        assertFalse(operation.result().success());
        verify(world, never()).isChunkLoaded(1, 0);
        verify(inventory, never()).clear();
        verify(firstChunk).removePluginChunkTicket(plugin);
        verify(firstChunk).unload(false);
    }

    @Test
    void waitsForAsynchronousChunkIoWithoutBlockingTheServerTick() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        CompletableFuture<Chunk> loading = new CompletableFuture<>();
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        when(world.isChunkGenerated(0, 0)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(loading);
        when(discovery.discover(region, chunk)).thenReturn(List.of());
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        assertFalse(operation.step(8, 64));
        verifyNoInteractions(chunk);
        verifyNoInteractions(discovery);
        loading.complete(chunk);
        assertTrue(operation.step(8, 64));

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().getFirst().contains("No chests"));
        verify(chunk).unload(false);
    }

    @Test
    void aThreeThousandFourHundredSixChunkRegionStartsOnlyEightAsyncLoadsPerTick() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0),
                new BlockPoint("arena", 3_406 * 16 - 1, 255, 15));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(false))).thenAnswer(
                ignored -> new CompletableFuture<Chunk>());
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        assertFalse(operation.step(8, 64));

        verify(world, times(8)).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        verify(world, never()).getChunkAt(anyInt(), anyInt(), eq(false));
        verifyNoInteractions(discovery);
    }

    @Test
    void doesNotRemoveAPluginTicketThatTheOperationDidNotAcquire() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Inventory inventory = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(discovery.discover(region, chunk)).thenReturn(List.of(new DiscoveredChest("chest", inventory)));
        when(chunk.addPluginChunkTicket(plugin)).thenReturn(false);
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        while (!operation.step(1, 1)) { }

        assertTrue(operation.result().success());
        verify(chunk, never()).removePluginChunkTicket(plugin);
        verify(chunk, never()).unload(false);
    }

    @Test
    void finishesAnAlreadyStartedMutationBatchSoChestsAreNotLeftPartiallyCleared() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Inventory first = mock(Inventory.class);
        Inventory second = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(discovery.discover(region, chunk)).thenReturn(List.of(
                new DiscoveredChest("first", first), new DiscoveredChest("second", second)));
        AtomicBoolean allowed = new AtomicBoolean(true);
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, allowed::get);

        assertFalse(operation.step(1, 1));
        assertFalse(operation.step(1, 1));
        assertFalse(operation.step(1, 1));
        allowed.set(false);
        assertTrue(operation.step(1, 1));

        assertTrue(operation.result().success());
        verify(first).clear();
        verify(second).clear();
    }

    @Test
    void cleanupContinuesAcrossChunksAndReportsATicketReleaseFailure() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk firstChunk = mock(Chunk.class);
        Chunk secondChunk = mock(Chunk.class);
        Inventory firstInventory = mock(Inventory.class);
        Inventory secondInventory = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.isChunkLoaded(1, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(firstChunk);
        when(world.getChunkAt(1, 0)).thenReturn(secondChunk);
        when(discovery.discover(region, firstChunk)).thenReturn(
                List.of(new DiscoveredChest("first", firstInventory)));
        when(discovery.discover(region, secondChunk)).thenReturn(
                List.of(new DiscoveredChest("second", secondInventory)));
        when(firstChunk.addPluginChunkTicket(plugin)).thenReturn(true);
        when(secondChunk.addPluginChunkTicket(plugin)).thenReturn(true);
        when(firstChunk.removePluginChunkTicket(plugin)).thenThrow(new IllegalStateException("ticket failed"));
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        while (!operation.step(2, 2)) { }

        assertFalse(operation.result().success());
        assertEquals(2, operation.result().affectedChests());
        assertTrue(operation.result().errors().stream().anyMatch(error -> error.contains("ticket failed")));
        verify(secondChunk).removePluginChunkTicket(plugin);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rescansRetainedChestChunksSoACrossChunkDoubleChestIsMutatedOnlyOnce() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        Chunk firstChunk = mock(Chunk.class);
        Chunk secondChunk = mock(Chunk.class);
        Inventory temporarySingle = mock(Inventory.class);
        Inventory combined = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 255, 15));
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.isChunkLoaded(1, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(firstChunk);
        when(world.getChunkAt(1, 0)).thenReturn(secondChunk);
        when(discovery.discover(region, firstChunk)).thenReturn(
                List.of(new DiscoveredChest("left", temporarySingle)),
                List.of(new DiscoveredChest("left|right", combined)));
        when(discovery.discover(region, secondChunk)).thenReturn(
                List.of(new DiscoveredChest("left|right", combined)));
        when(firstChunk.addPluginChunkTicket(plugin)).thenReturn(true);
        when(secondChunk.addPluginChunkTicket(plugin)).thenReturn(true);
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, region,
                new WeaponLootCatalog(null, null,
                        new WeaponLootCatalog.Settings(0, 0, 5_000), List.of(), List.of(), List.of()),
                discovery, mock(ChestLootGenerator.class), WeaponChestService.ActionType.CLEAR, () -> true);

        while (!operation.step(1, 1)) { }

        assertTrue(operation.result().success());
        assertEquals(1, operation.result().affectedChests());
        verify(temporarySingle, never()).clear();
        verify(combined).clear();
    }
}
