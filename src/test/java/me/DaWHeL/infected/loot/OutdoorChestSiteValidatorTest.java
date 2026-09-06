package me.DaWHeL.infected.loot;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutdoorChestSiteValidatorTest {
    private final ChestRegion region = new ChestRegion("arena", 0, 0, 0, 30, 100, 30);

    @Test
    void acceptsAFlatOpenSiteWithDeepNaturalSupport() {
        World world = world((x, z) -> 64, y -> y <= 64 ? Material.GRASS_BLOCK : Material.AIR);

        Optional<ChestSite> site = validator().validate(world, region, 15, 15);

        assertEquals(Optional.of(new ChestSite(15, 64, 15)), site);
        verify(world, atLeastOnce()).getHighestBlockYAt(15, 15, HeightMap.MOTION_BLOCKING);
        verify(world, never()).getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }

    @Test
    void rejectsASlopeAcrossTheThreeByThreeFootprint() {
        World world = world((x, z) -> x == 16 ? 65 : 64,
                y -> y <= 65 ? Material.STONE : Material.AIR);

        assertTrue(validator().validate(world, region, 15, 15).isEmpty());
    }

    @Test
    void rejectsAThinRoofEvenThoughItIsTheHighestOutdoorSurface() {
        World world = world((x, z) -> 64,
                y -> y == 64 ? Material.STONE : Material.AIR);

        assertTrue(validator().validate(world, region, 15, 15).isEmpty());
    }

    @Test
    void rejectsAnObstructedPlatform() {
        World world = world((x, z) -> 64,
                y -> y <= 64 || y == 65 ? Material.STONE : Material.AIR);

        assertTrue(validator().validate(world, region, 15, 15).isEmpty());
    }

    private static OutdoorChestSiteValidator validator() {
        return new OutdoorChestSiteValidator(block -> block.getType() != Material.AIR);
    }

    private static World world(Height height, java.util.function.IntFunction<Material> materialAtY) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt(), eq(HeightMap.MOTION_BLOCKING)))
                .thenAnswer(invocation -> height.at(invocation.getArgument(0), invocation.getArgument(1)));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(materialAtY.apply(y));
            when(block.getState(false)).thenReturn(mock(BlockState.class));
            return block;
        });
        return world;
    }

    @FunctionalInterface
    private interface Height {
        int at(int x, int z);
    }
}
