package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChestRegionTest {
    @Test
    void normalizesInclusiveBoundsAndVolume() {
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 5, 9, 7), new BlockPoint("arena", 3, 8, 4));

        assertAll(
                () -> assertTrue(region.contains(3, 8, 4)),
                () -> assertTrue(region.contains(5, 9, 7)),
                () -> assertFalse(region.contains(6, 9, 7)),
                () -> assertEquals(24L, region.volume())
        );
    }

    @Test
    void rejectsPointsFromDifferentWorlds() {
        assertThrows(IllegalArgumentException.class, () -> ChestRegion.between(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("lobby", 0, 0, 0)));
    }

    @Test
    void enumeratesNegativeChunksUsingFloorDivision() {
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", -17, 0, -1), new BlockPoint("arena", 16, 0, 0));

        assertEquals(Set.of(
                new ChestRegion.ChunkKey(-2, -1), new ChestRegion.ChunkKey(-1, -1),
                new ChestRegion.ChunkKey(0, -1), new ChestRegion.ChunkKey(1, -1),
                new ChestRegion.ChunkKey(-2, 0), new ChestRegion.ChunkKey(-1, 0),
                new ChestRegion.ChunkKey(0, 0), new ChestRegion.ChunkKey(1, 0)
        ), region.chunkKeys());
    }

    @Test
    void rejectsExcessiveChunkCountBeforeMaterializingKeys() {
        ChestRegion region = ChestRegion.between(
                new BlockPoint("arena", 0, 64, 0), new BlockPoint("arena", 1_999_999, 64, 0));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> region.chunkKeys(256));

        assertTrue(error.getMessage().contains("125000"));
    }
}
