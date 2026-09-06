package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedChestRepositoryTest {
    @TempDir Path directory;

    @Test
    void persistsStableOwnershipAndAllNineOriginalGroundBlocks() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000123");
        GeneratedChestPlacement placement = new GeneratedChestPlacement(
                id, "arena", 15, 71, -8,
                List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:stone",
                        "minecraft:grass_block", "minecraft:dirt", "minecraft:stone",
                        "minecraft:grass_block", "minecraft:dirt", "minecraft:stone"),
                GeneratedChestPlacement.State.PENDING);
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());

        repository.replaceAll(List.of(placement));
        GeneratedChestRepository reloaded = new GeneratedChestRepository(directory.toFile());

        assertEquals(List.of(placement), reloaded.snapshot());
    }
}
