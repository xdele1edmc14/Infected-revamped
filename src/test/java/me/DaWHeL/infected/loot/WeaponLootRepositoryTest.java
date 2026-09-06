package me.DaWHeL.infected.loot;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeaponLootRepositoryTest {
    @TempDir Path directory;

    @Test
    void persistsRegionAndExactGunAmmoPayloadsAcrossReload() {
        ItemSnapshotCodec codec = mock(ItemSnapshotCodec.class);
        ItemStack gun = item("gun-bytes");
        ItemStack ammo = item("ammo-bytes");
        when(codec.encode(gun)).thenReturn("gun-bytes");
        when(codec.encode(ammo)).thenReturn("ammo-bytes");
        when(codec.decode("gun-bytes")).thenReturn(gun);
        when(codec.decode("ammo-bytes")).thenReturn(ammo);
        WeaponLootRepository repository = new WeaponLootRepository(directory.toFile(), codec);

        repository.setPoint(1, new BlockPoint("arena", 1, 2, 3));
        repository.setPoint(2, new BlockPoint("arena", 9, 8, 7));
        var id = repository.addGun(gun);
        repository.setGunAmmo(id, ammo);
        WeaponLootRepository reloaded = new WeaponLootRepository(directory.toFile(), codec);

        assertAll(
                () -> assertEquals(new BlockPoint("arena", 1, 2, 3), reloaded.snapshot().point1()),
                () -> assertEquals(new BlockPoint("arena", 9, 8, 7), reloaded.snapshot().point2()),
                () -> assertEquals(1, reloaded.snapshot().guns().size()),
                () -> assertNotNull(reloaded.snapshot().guns().getFirst().ammo()),
                () -> assertTrue(reloaded.snapshot().errors().isEmpty())
        );
    }

    @Test
    void chunkLimitDefaultsToFiveThousandAndPersistsGuiChanges() {
        WeaponLootRepository repository = new WeaponLootRepository(
                directory.toFile(), mock(ItemSnapshotCodec.class));

        assertEquals(5_000, repository.snapshot().settings().maxChunks());

        repository.setMaxChunks(3_500);
        WeaponLootRepository reloaded = new WeaponLootRepository(
                directory.toFile(), mock(ItemSnapshotCodec.class));

        assertEquals(3_500, reloaded.snapshot().settings().maxChunks());
    }

    @Test
    void unrelatedSavePreservesMalformedEntriesAndTheirValidationErrors() throws Exception {
        String invalidId = "00000000-0000-0000-0000-000000000001";
        Files.writeString(directory.resolve("weapon-loot.yml"), """
                guns:
                  %s:
                    item: not-valid-base64
                    rarity: COMMON
                    min-ammo-bundles: 1
                    max-ammo-bundles: 1
                """.formatted(invalidId));
        ItemSnapshotCodec codec = mock(ItemSnapshotCodec.class);
        when(codec.decode("not-valid-base64")).thenThrow(new IllegalArgumentException("bad payload"));
        WeaponLootRepository repository = new WeaponLootRepository(directory.toFile(), codec);

        repository.setPoint(1, new BlockPoint("arena", 1, 2, 3));

        String saved = Files.readString(directory.resolve("weapon-loot.yml"));
        assertAll(
                () -> assertTrue(saved.contains(invalidId)),
                () -> assertTrue(saved.contains("not-valid-base64")),
                () -> assertFalse(repository.snapshot().errors().isEmpty())
        );
    }

    @Test
    void invalidYamlCannotBeOverwrittenByAGuiEdit() throws Exception {
        Path file = directory.resolve("weapon-loot.yml");
        String malformed = "guns: [this is not closed";
        Files.writeString(file, malformed);
        WeaponLootRepository repository = new WeaponLootRepository(directory.toFile(), mock(ItemSnapshotCodec.class));

        assertThrows(IllegalStateException.class,
                () -> repository.setPoint(1, new BlockPoint("arena", 1, 2, 3)));

        assertEquals(malformed, Files.readString(file));
        assertNull(repository.snapshot().point1());
    }

    @Test
    void malformedReloadDoesNotKeepAPreviouslyLoadedRegionActive() throws Exception {
        Path file = directory.resolve("weapon-loot.yml");
        WeaponLootRepository repository = new WeaponLootRepository(
                directory.toFile(), mock(ItemSnapshotCodec.class));
        repository.setPoint(1, new BlockPoint("old-arena", 1, 2, 3));
        repository.setPoint(2, new BlockPoint("old-arena", 9, 8, 7));
        Files.writeString(file, "region: [this is not closed");

        repository.reload();

        assertAll(
                () -> assertNull(repository.snapshot().point1()),
                () -> assertNull(repository.snapshot().point2()),
                () -> assertFalse(repository.snapshot().errors().isEmpty())
        );
    }

    @Test
    void invalidGlobalSettingsCannotBeOverwrittenByAnUnrelatedEdit() throws Exception {
        Path file = directory.resolve("weapon-loot.yml");
        String invalid = """
                settings:
                  second-gun-chance: 150
                  grenade-chance: 25
                  max-volume: 2000000
                  max-chunks: 256
                """;
        Files.writeString(file, invalid);
        WeaponLootRepository repository = new WeaponLootRepository(directory.toFile(), mock(ItemSnapshotCodec.class));

        assertThrows(IllegalStateException.class,
                () -> repository.setPoint(1, new BlockPoint("arena", 1, 2, 3)));

        assertEquals(invalid, Files.readString(file));
        assertTrue(repository.snapshot().errors().stream().anyMatch(error -> error.contains("settings")));
    }

    private static ItemStack item(String payload) {
        ItemStack item = mock(ItemStack.class, payload);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
