package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfectedRespawnSelectorTest {

    @Test
    void acceptsSolidGroundWithTwoBlocksOfClearHeadroomInsideTheBorder() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);

        assertTrue(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsLocationsOutsideTheWorldBorder() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, false);

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsFeetAtOrBelowTheWorldMinimumBuildHeight() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);
        when(location.getWorld().getMinHeight()).thenReturn(location.getBlockY());

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsHeadroomAtOrAboveTheWorldMaximumBuildHeight() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);
        when(location.getWorld().getMaxHeight()).thenReturn(location.getBlockY() + 1);

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsAPointWhoseWorldUnloadedBeforeValidation() {
        Location unloaded = mock(Location.class);
        when(unloaded.getWorld()).thenThrow(new IllegalArgumentException("World unloaded"));

        assertFalse(InfectedRespawnSelector.isSafe(unloaded));
    }

    @Test
    void rejectsBorderEdgePointsThatCannotFitThePlayersFullWidth() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);
        WorldBorder border = location.getWorld().getWorldBorder();
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenAnswer(invocation -> {
            Location checked = invocation.getArgument(0);
            return checked.getX() == location.getX() && checked.getZ() == location.getZ();
        });

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsNonSolidGroundAndBlockedHeadroom() {
        Location nonSolidGround = location(Material.WATER, Material.AIR, true, Material.AIR, true, true);
        Location blockedHead = location(Material.STONE, Material.AIR, true, Material.STONE, false, true);

        assertFalse(InfectedRespawnSelector.isSafe(nonSolidGround));
        assertFalse(InfectedRespawnSelector.isSafe(blockedHead));
    }

    @Test
    void rejectsGroundWhoseCollisionSurfaceIsBelowTheConfiguredFeetHeight() {
        Location location = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);
        Block ground = location.getWorld().getBlockAt(10, 63, 10);
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(10, 63, 10, 11, 63.5, 11));

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void rejectsGroundWhoseCollisionProtrudesIntoThePlayersFeet() {
        Location location = location(Material.OAK_FENCE, Material.AIR, true, Material.AIR, true, true);
        Block ground = location.getWorld().getBlockAt(10, 63, 10);
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(10.375, 63, 10.375, 10.625, 64.5, 10.625));

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "LAVA", "FIRE", "SOUL_FIRE", "POWDER_SNOW", "SWEET_BERRY_BUSH", "WITHER_ROSE"
    })
    void rejectsHazardsAtPlayerHeight(Material hazard) {
        Location location = location(Material.STONE, hazard, true, Material.AIR, true, true);

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {
            "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS"
    })
    void rejectsHazardousGround(Material hazard) {
        Location location = location(hazard, Material.AIR, true, Material.AIR, true, true);

        assertFalse(InfectedRespawnSelector.isSafe(location));
    }

    @Test
    void selectsASafeDedicatedPointWhenAnotherConfiguredPointIsUnsafe() {
        Location unsafe = location(Material.MAGMA_BLOCK, Material.AIR, true, Material.AIR, true, true);
        Location safe = location(Material.STONE, Material.AIR, true, Material.AIR, true, true);

        Optional<Location> selected = InfectedRespawnSelector.select(
                List.of(unsafe, safe), new Random(4));

        assertTrue(selected.isPresent());
        assertEquals(Material.STONE, selected.get().getWorld().getBlockAt(10, 63, 10).getType());
    }

    private static Location location(
            Material groundType,
            Material feetType,
            boolean feetPassable,
            Material headType,
            boolean headPassable,
            boolean insideBorder
    ) {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Block ground = block(groundType, false);
        Block feet = block(feetType, feetPassable);
        Block head = block(headType, headPassable);
        Location location = new Location(world, 10.5, 64, 10.5);

        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(insideBorder);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(10, 63, 10)).thenReturn(ground);
        when(world.getBlockAt(10, 64, 10)).thenReturn(feet);
        when(world.getBlockAt(10, 65, 10)).thenReturn(head);
        when(ground.getBoundingBox()).thenReturn(new BoundingBox(10, 63, 10, 11, 64, 11));
        return location;
    }

    private static Block block(Material type, boolean passable) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        when(block.isPassable()).thenReturn(passable);
        return block;
    }
}
