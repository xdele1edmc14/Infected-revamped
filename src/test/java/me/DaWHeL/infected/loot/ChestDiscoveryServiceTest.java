package me.DaWHeL.infected.loot;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChestDiscoveryServiceTest {
    private final ChestRegion region = ChestRegion.between(
            new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15));

    @Test
    void rejectsAnUnavailableWorldWithoutLoadingChunks() {
        ChestDiscoveryService.DiscoveryResult result = new ChestDiscoveryService(name -> null).discover(region);
        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains("not loaded"));
    }

    @Test
    void discoversOnlyChestTileEntitiesInsideTheRegion() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Chest inside = chestAt(3, 60, 4);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(inside));

        ChestDiscoveryService.DiscoveryResult result = new ChestDiscoveryService(name -> world).discover(region);

        assertTrue(result.success());
        assertEquals(1, result.chests().size());
        assertSame(inside.getInventory(), result.chests().getFirst().inventory());
        verify(chunk).getTileEntities(any(), eq(false));
        verify(chunk, never()).getTileEntities();
    }

    @Test
    void refusesToFetchAnUnloadedChunk() {
        World world = mock(World.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);

        ChestDiscoveryService.DiscoveryResult result = new ChestDiscoveryService(name -> world).discover(region);

        assertFalse(result.success());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void deduplicatesBothHalvesOfADoubleChest() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Chest left = chestAt(3, 60, 4);
        Chest right = chestAt(4, 60, 4);
        Inventory combined = mock(Inventory.class);
        DoubleChest holder = mock(DoubleChest.class);
        when(left.getInventory()).thenReturn(combined);
        when(right.getInventory()).thenReturn(combined);
        when(combined.getHolder()).thenReturn(holder);
        when(holder.getLeftSide()).thenReturn(left);
        when(holder.getRightSide()).thenReturn(right);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(left, right));

        ChestDiscoveryService.DiscoveryResult result = new ChestDiscoveryService(name -> world).discover(region);

        assertTrue(result.success());
        assertEquals(1, result.chests().size());
        assertSame(combined, result.chests().getFirst().inventory());
    }

    @Test
    @SuppressWarnings("unchecked")
    void liveTileEntityFilterIncludesNormalAndTrappedChestsInsideTheCuboid() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of());

        new ChestDiscoveryService(name -> world).discover(region);

        var filter = org.mockito.ArgumentCaptor.forClass(Predicate.class);
        verify(chunk).getTileEntities(filter.capture(), eq(false));
        assertAll(
                () -> assertTrue(filter.getValue().test(blockAt(Material.CHEST, 3, 60, 4))),
                () -> assertTrue(filter.getValue().test(blockAt(Material.TRAPPED_CHEST, 4, 60, 5))),
                () -> assertFalse(filter.getValue().test(blockAt(Material.BARREL, 3, 60, 4))),
                () -> assertFalse(filter.getValue().test(blockAt(Material.CHEST, 20, 60, 4)))
        );
    }

    @Test
    void aDoubleChestCrossingTheSelectionBoundaryMutatesOnlyTheSelectedHalf() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Chest selected = chestAt(15, 60, 4);
        Chest outside = chestAt(16, 60, 4);
        Inventory combined = mock(Inventory.class);
        Inventory selectedHalf = mock(Inventory.class);
        DoubleChest holder = mock(DoubleChest.class);
        when(selected.getInventory()).thenReturn(combined);
        when(selected.getBlockInventory()).thenReturn(selectedHalf);
        when(combined.getHolder()).thenReturn(holder);
        when(holder.getLeftSide()).thenReturn(selected);
        when(holder.getRightSide()).thenReturn(outside);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(selected));

        ChestDiscoveryService.DiscoveryResult result = new ChestDiscoveryService(name -> world).discover(region);

        assertTrue(result.success());
        assertEquals(1, result.chests().size());
        assertSame(selectedHalf, result.chests().getFirst().inventory());
    }

    @Test
    void anIneligibleDoubleChestHalfIsNeverExposedThroughTheEligibleHalf() {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Chest eligible = chestAt(3, 60, 4);
        Chest manual = chestAt(4, 60, 4);
        Inventory combined = mock(Inventory.class);
        Inventory eligibleHalf = mock(Inventory.class);
        DoubleChest holder = mock(DoubleChest.class);
        when(eligible.getInventory()).thenReturn(combined);
        when(eligible.getBlockInventory()).thenReturn(eligibleHalf);
        when(manual.getInventory()).thenReturn(combined);
        when(combined.getHolder()).thenReturn(holder);
        when(holder.getLeftSide()).thenReturn(eligible);
        when(holder.getRightSide()).thenReturn(manual);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(world.getChunkAt(0, 0)).thenReturn(chunk);
        when(chunk.getTileEntities(any(), eq(false))).thenReturn(List.of(eligible, manual));

        ChestDiscoveryService service = new ChestDiscoveryService(name -> world, chest -> chest == eligible);
        ChestDiscoveryService.DiscoveryResult result = service.discover(region);

        assertTrue(result.success());
        assertEquals(1, result.chests().size());
        assertSame(eligibleHalf, result.chests().getFirst().inventory());
    }

    private static Chest chestAt(int x, int y, int z) {
        Chest chest = mock(Chest.class);
        Inventory inventory = mock(Inventory.class);
        Location location = mock(Location.class);
        when(location.getBlockX()).thenReturn(x);
        when(location.getBlockY()).thenReturn(y);
        when(location.getBlockZ()).thenReturn(z);
        when(location.getWorld()).thenReturn(null);
        when(chest.getLocation()).thenReturn(location);
        when(chest.getInventory()).thenReturn(inventory);
        return chest;
    }

    private static Block blockAt(Material material, int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }
}
