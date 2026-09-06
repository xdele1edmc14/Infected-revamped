package me.DaWHeL.infected.loot;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneratedChestServiceTest {
    @TempDir Path directory;

    @Test
    void generatePreviewAllowsGeneratedChunksToBeLoadedByTheStreamedOperation() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 100, 31),
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of());
        GeneratedChestService service = new GeneratedChestService(
                game, () -> catalog, new GeneratedChestRepository(directory.toFile()),
                mock(OutdoorChestSiteValidator.class), new NamespacedKey("infected", "generated-weapon-chest"),
                new ChestOperationGate(), name -> world, () -> 1L);

        GeneratedChestService.ActionPreview preview = service.preview(GeneratedChestService.ActionType.GENERATE);

        assertTrue(preview.success());
        assertTrue(preview.errors().isEmpty());
    }

    @Test
    void removePreviewAllowsRegisteredChunksToBeLoadedByTheStreamedOperation() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        World world = mock(World.class);
        when(world.getName()).thenReturn("arena");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        GeneratedChestRepository repository = new GeneratedChestRepository(directory.toFile());
        repository.replaceAll(List.of(new GeneratedChestPlacement(
                java.util.UUID.randomUUID(), "arena", 5, 65, 5,
                java.util.Collections.nCopies(9, "minecraft:grass_block"))));
        GeneratedChestService service = new GeneratedChestService(
                game, () -> new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of()),
                repository, mock(OutdoorChestSiteValidator.class),
                new NamespacedKey("infected", "generated-weapon-chest"),
                new ChestOperationGate(), name -> world, () -> 1L);

        GeneratedChestService.ActionPreview preview = service.preview(GeneratedChestService.ActionType.REMOVE);

        assertTrue(preview.success());
        assertTrue(preview.errors().isEmpty());
    }

    @Test
    void generatePreviewRejectsASelectionTooNarrowForAThreeByThreePlatform() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        World world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 1, 100, 1),
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of());
        GeneratedChestService service = new GeneratedChestService(
                game, () -> catalog, new GeneratedChestRepository(directory.toFile()),
                mock(OutdoorChestSiteValidator.class), new NamespacedKey("infected", "generated-weapon-chest"),
                new ChestOperationGate(), name -> world, () -> 1L);

        GeneratedChestService.ActionPreview preview = service.preview(GeneratedChestService.ActionType.GENERATE);

        assertFalse(preview.success());
        assertTrue(preview.errors().stream().anyMatch(error -> error.contains("3x3")));
    }
}
