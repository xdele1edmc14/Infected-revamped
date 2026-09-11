package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void rejectsAnIncompletePlacementInsteadOfDefaultingMissingCoordinatesToZero() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000123");
        java.nio.file.Files.writeString(directory.resolve("generated-weapon-chests.yml"), """
                placements:
                  %s:
                    world: arena
                    x: 15
                    y: 71
                    original-ground:
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                      - minecraft:stone
                    state: ACTIVE
                """.formatted(id));

        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());

        assertEquals(List.of(), repository.snapshot());
        assertFalse(repository.errors().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(repository.errors().getFirst().contains("missing z"));
        assertThrows(IllegalStateException.class, () -> repository.replaceAll(List.of()));
    }

    @Test
    void revisionChangesWheneverTheLayoutIsReplacedOrReloaded() {
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        long initial = repository.revision();

        repository.replaceAll(List.of());
        long afterReplace = repository.revision();
        repository.reload();

        assertTrue(afterReplace > initial);
        assertTrue(repository.revision() > afterReplace);
    }
}
