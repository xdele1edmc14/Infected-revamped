package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamedGeneratedChestOperationTest {
    @TempDir Path directory;
    private final NamespacedKey markerKey = new NamespacedKey("infected", "generated-weapon-chest");

    @Test
    void plansThenPlacesNineGoldBlocksAndATaggedChestAboveTheCenter() {
        GeneratedChestRepository repository = spy(new GeneratedChestRepository(directory.toFile()));
        WorldFixture fixture = new WorldFixture();
        OutdoorChestSiteValidator validator = mock(OutdoorChestSiteValidator.class);
        when(validator.validate(any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Optional.of(new ChestSite(5, 64, 5)));
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 10, 100, 10), 1,
                repository, validator, markerKey, ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Advance all planning and mutation stages.
        }

        assertTrue(operation.result().success());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                verify(fixture.block(5 + dx, 64, 5 + dz)).setType(Material.GOLD_BLOCK, false);
            }
        }
        verify(fixture.block(5, 65, 5)).setType(Material.CHEST, false);
        verify(fixture.data).set(eq(markerKey), eq(PersistentDataType.STRING), anyString());
        assertEquals(1, repository.snapshot().size());
        assertEquals(GeneratedChestPlacement.State.ACTIVE, repository.snapshot().getFirst().state());
        assertEquals(List.of("minecraft:stone", "minecraft:stone", "minecraft:stone",
                "minecraft:stone", "minecraft:stone", "minecraft:stone",
                "minecraft:stone", "minecraft:stone", "minecraft:stone"),
                repository.snapshot().getFirst().originalGround());
        InOrder ordering = inOrder(repository, fixture.block(5, 64, 5));
        ordering.verify(repository).replaceAll(argThat((List<GeneratedChestPlacement> placements) ->
                placements.size() == 1 && placements.getFirst().state() == GeneratedChestPlacement.State.PENDING));
        ordering.verify(fixture.block(5, 64, 5)).setType(Material.GOLD_BLOCK, false);
        ordering.verify(repository).replaceAll(argThat((List<GeneratedChestPlacement> placements) ->
                placements.size() == 1 && placements.getFirst().state() == GeneratedChestPlacement.State.ACTIVE));
    }

    @Test
    void asynchronouslyLoadsGeneratedCandidateChunksWithoutGeneratingTerrain() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        Chunk chunk = mock(Chunk.class);
        AtomicBoolean loaded = new AtomicBoolean();
        when(fixture.world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(ignored -> loaded.get());
        when(fixture.world.isChunkGenerated(0, 0)).thenReturn(true);
        when(fixture.world.getChunkAtAsync(0, 0, false)).thenAnswer(ignored -> {
            loaded.set(true);
            return CompletableFuture.completedFuture(chunk);
        });
        doAnswer(ignored -> {
            loaded.set(false);
            return true;
        }).when(chunk).unload(anyBoolean());
        OutdoorChestSiteValidator validator = mock(OutdoorChestSiteValidator.class);
        ChestSite site = new ChestSite(5, 64, 5);
        when(validator.validate(any(), any(), anyInt(), anyInt())).thenAnswer(
                ignored -> loaded.get() ? java.util.Optional.of(site) : java.util.Optional.empty());
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 10, 100, 10), 1,
                repository, validator, markerKey, ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Advance asynchronous loading, planning, and placement.
        }

        assertTrue(operation.result().success());
        verify(fixture.world, atLeast(2)).getChunkAtAsync(0, 0, false);
        verify(fixture.world, never()).getChunkAt(0, 0, false);
        verify(chunk, atLeastOnce()).unload(false);
        verify(chunk, atLeastOnce()).unload(true);
    }

    @Test
    void acceptedCandidateFootprintsAreReleasedBeforePlanningTheNextSite() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        Plugin plugin = mock(Plugin.class);
        Chunk chunk = mock(Chunk.class);
        when(fixture.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(fixture.world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        when(fixture.world.getChunkAtAsync(anyInt(), anyInt(), eq(false)))
                .thenReturn(CompletableFuture.completedFuture(chunk));
        when(chunk.addPluginChunkTicket(plugin)).thenReturn(true);
        OutdoorChestSiteValidator validator = mock(OutdoorChestSiteValidator.class);
        when(validator.validate(any(), any(), anyInt(), anyInt())).thenAnswer(invocation ->
                java.util.Optional.of(new ChestSite(invocation.getArgument(2), 64, invocation.getArgument(3))));
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                plugin, GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 100, 100, 100), 2,
                repository, validator, markerKey, ignored -> mock(BlockData.class),
                ignored -> fixture.world, 12L, () -> true);

        assertFalse(operation.step(1, 1));

        verify(chunk, atLeastOnce()).removePluginChunkTicket(plugin);
        verify(chunk, atLeastOnce()).unload(false);
        operation.cancel("Plugin disabled.");

        assertFalse(operation.result().success());
    }

    @Test
    void refusesToGenerateAnUnexploredCandidateChunk() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        when(fixture.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(fixture.world.isChunkGenerated(anyInt(), anyInt())).thenReturn(false);
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 10, 100, 10), 1,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // The first candidate should stop before terrain generation.
        }

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().getFirst().contains("not generated"));
        verify(fixture.world, never()).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void checksTheWholeFootprintBeforeStartingAnyAsynchronousLoads() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        when(fixture.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        when(fixture.world.isChunkGenerated(0, 0)).thenReturn(true);
        when(fixture.world.isChunkGenerated(1, 0)).thenReturn(false);
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 14, 0, 4, 16, 100, 6), 1,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        assertTrue(operation.step(32, 8));

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().getFirst().contains("not generated"));
        verify(fixture.world, never()).getChunkAtAsync(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void revalidatesCapturedTerrainImmediatelyBeforePlacementAndKeepsTheRecoveryJournalOnFailure() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        OutdoorChestSiteValidator validator = mock(OutdoorChestSiteValidator.class);
        ChestSite site = new ChestSite(5, 64, 5);
        when(validator.validate(any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Optional.of(site), java.util.Optional.of(site),
                        java.util.Optional.of(site), java.util.Optional.empty());
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 10, 100, 10), 1,
                repository, validator, markerKey, ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Advance until the mutation-time check rejects the changed site.
        }

        assertFalse(operation.result().success());
        assertEquals(GeneratedChestPlacement.State.PENDING, repository.snapshot().getFirst().state());
        verify(fixture.block(5, 64, 5), never()).setType(any(), anyBoolean());
        verify(fixture.block(5, 65, 5), never()).setType(any(), anyBoolean());
    }

    @Test
    void refusesAStillSolidSiteWhenItsCapturedSurfaceBlockDataChanged() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        WorldFixture fixture = new WorldFixture();
        BlockData centerData = fixture.block(5, 64, 5).getBlockData();
        when(centerData.getAsString(false)).thenReturn("minecraft:stone").thenReturn("minecraft:dirt");
        OutdoorChestSiteValidator validator = mock(OutdoorChestSiteValidator.class);
        ChestSite site = new ChestSite(5, 64, 5);
        when(validator.validate(any(), any(), anyInt(), anyInt()))
                .thenReturn(java.util.Optional.of(site));
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.GENERATE, fixture.world,
                new ChestRegion("arena", 0, 0, 0, 10, 100, 10), 1,
                repository, validator, markerKey, ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // The site stays geometrically valid, but its surface no longer matches the journal.
        }

        assertFalse(operation.result().success());
        assertEquals(GeneratedChestPlacement.State.PENDING, repository.snapshot().getFirst().state());
        verify(fixture.block(5, 64, 5), never()).setType(any(), anyBoolean());
    }

    @Test
    void refusesToRemoveARegisteredLocationWhoseChestWasReplacedManually() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        UUID id = UUID.randomUUID();
        repository.replaceAll(List.of(new GeneratedChestPlacement(id, "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"))));
        WorldFixture fixture = new WorldFixture();
        when(fixture.block(5, 65, 5).getType()).thenReturn(Material.CHEST);
        when(fixture.data.get(markerKey, PersistentDataType.STRING)).thenReturn(null);
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.REMOVE, fixture.world, null, 0,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Advance preflight; it must stop before mutation.
        }

        assertFalse(operation.result().success());
        assertTrue(operation.result().errors().stream().anyMatch(error -> error.contains("ownership")));
        verify(fixture.block(5, 65, 5), never()).setType(any(), anyBoolean());
        assertEquals(1, repository.snapshot().size());
    }

    @Test
    void removesOnlyTheMatchingTaggedChestAndRestoresItsRegisteredGoldFoundation() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        UUID id = UUID.randomUUID();
        repository.replaceAll(List.of(new GeneratedChestPlacement(id, "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"))));
        WorldFixture fixture = new WorldFixture();
        when(fixture.block(5, 65, 5).getType()).thenReturn(Material.CHEST);
        when(fixture.data.get(markerKey, PersistentDataType.STRING)).thenReturn(id.toString());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                when(fixture.block(5 + dx, 64, 5 + dz).getType()).thenReturn(Material.GOLD_BLOCK);
            }
        }
        BlockData restored = mock(BlockData.class);
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.REMOVE, fixture.world, null, 0,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> restored, 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Advance preflight and restoration.
        }

        assertTrue(operation.result().success());
        verify(fixture.block(5, 65, 5)).setType(Material.AIR, false);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                verify(fixture.block(5 + dx, 64, 5 + dz)).setBlockData(restored, false);
            }
        }
        assertTrue(repository.snapshot().isEmpty());
    }

    @Test
    void rechecksOwnershipAtTheExactRestorationTick() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        UUID id = UUID.randomUUID();
        repository.replaceAll(List.of(new GeneratedChestPlacement(id, "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"))));
        WorldFixture fixture = new WorldFixture();
        when(fixture.block(5, 65, 5).getType()).thenReturn(Material.CHEST);
        when(fixture.data.get(markerKey, PersistentDataType.STRING)).thenReturn(id.toString()).thenReturn(null);
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.REMOVE, fixture.world, null, 0,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Ownership changes between precheck and restoration.
        }

        assertFalse(operation.result().success());
        verify(fixture.block(5, 65, 5), never()).setType(any(), anyBoolean());
        assertEquals(1, repository.snapshot().size());
    }

    @Test
    void removesAnUntaggedCompletePendingStructureAfterAnInterruptedPlacement() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        UUID id = UUID.randomUUID();
        repository.replaceAll(List.of(new GeneratedChestPlacement(id, "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"),
                GeneratedChestPlacement.State.PENDING)));
        WorldFixture fixture = new WorldFixture();
        when(fixture.block(5, 65, 5).getType()).thenReturn(Material.CHEST);
        when(fixture.data.get(markerKey, PersistentDataType.STRING)).thenReturn(null);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                when(fixture.block(5 + dx, 64, 5 + dz).getType()).thenReturn(Material.GOLD_BLOCK);
            }
        }
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.REMOVE, fixture.world, null, 0,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Recover the write-ahead entry.
        }

        assertTrue(operation.result().success());
        verify(fixture.block(5, 65, 5)).setType(Material.AIR, false);
        assertTrue(repository.snapshot().isEmpty());
    }

    @Test
    void asynchronouslyLoadsARegisteredChunkBeforeRemovingItsGeneratedStructure() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        UUID id = UUID.randomUUID();
        repository.replaceAll(List.of(new GeneratedChestPlacement(id, "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"))));
        WorldFixture fixture = new WorldFixture();
        Chunk chunk = mock(Chunk.class);
        AtomicBoolean loaded = new AtomicBoolean();
        when(fixture.world.isChunkLoaded(anyInt(), anyInt())).thenAnswer(ignored -> loaded.get());
        when(fixture.world.isChunkGenerated(0, 0)).thenReturn(true);
        when(fixture.world.getChunkAtAsync(0, 0, false)).thenAnswer(ignored -> {
            loaded.set(true);
            return CompletableFuture.completedFuture(chunk);
        });
        doAnswer(ignored -> {
            loaded.set(false);
            return true;
        }).when(chunk).unload(anyBoolean());
        when(fixture.block(5, 65, 5).getType()).thenReturn(Material.CHEST);
        when(fixture.data.get(markerKey, PersistentDataType.STRING)).thenReturn(id.toString());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                when(fixture.block(5 + dx, 64, 5 + dz).getType()).thenReturn(Material.GOLD_BLOCK);
            }
        }
        StreamedGeneratedChestOperation operation = new StreamedGeneratedChestOperation(
                GeneratedChestService.ActionType.REMOVE, fixture.world, null, 0,
                repository, mock(OutdoorChestSiteValidator.class), markerKey,
                ignored -> mock(BlockData.class), 12L, () -> true);

        while (!operation.step(32, 8)) {
            // Load, retain, restore, and release the registered chunk.
        }

        assertTrue(operation.result().success());
        verify(fixture.world, atLeast(2)).getChunkAtAsync(0, 0, false);
        verify(fixture.block(5, 65, 5)).setType(Material.AIR, false);
        verify(chunk, atLeastOnce()).unload(false);
        verify(chunk, atLeastOnce()).unload(true);
    }

    private final class WorldFixture {
        final World world = mock(World.class);
        final Chunk loadedChunk = mock(Chunk.class);
        final Chest chest = mock(Chest.class);
        final PersistentDataContainer data = mock(PersistentDataContainer.class);
        final Map<String, Block> blocks = new HashMap<>();

        WorldFixture() {
            when(world.getName()).thenReturn("arena");
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.getChunkAt(anyInt(), anyInt())).thenReturn(loadedChunk);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> block(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            when(chest.getPersistentDataContainer()).thenReturn(data);
            when(chest.update(true, false)).thenReturn(true);
        }

        Block block(int x, int y, int z) {
            return blocks.computeIfAbsent(x + ":" + y + ":" + z, ignored -> {
                Block block = mock(Block.class);
                when(block.getType()).thenReturn(y == 64 ? Material.STONE : Material.AIR);
                BlockData blockData = mock(BlockData.class);
                when(blockData.getAsString(false)).thenReturn("minecraft:stone");
                when(block.getBlockData()).thenReturn(blockData);
                if (x == 5 && y == 65 && z == 5) when(block.getState(false)).thenReturn(chest);
                return block;
            });
        }
    }
}
