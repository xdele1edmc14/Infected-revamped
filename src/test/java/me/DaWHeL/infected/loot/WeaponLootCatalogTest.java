package me.DaWHeL.infected.loot;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WeaponLootCatalogTest {
    @Test
    void preservesTheCapturedGunStackAmount() {
        ItemStack captured = mock(ItemStack.class);
        ItemStack stored = mock(ItemStack.class);
        when(captured.clone()).thenReturn(stored);
        when(stored.clone()).thenReturn(stored);
        when(stored.getAmount()).thenReturn(3);

        WeaponLootCatalog catalog = new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(10, 25, 256),
                List.of(new WeaponLootCatalog.GunEntry(
                        UUID.randomUUID(), captured, LootRarity.COMMON, null, 1, 1)),
                List.of(), List.of());

        assertEquals(3, catalog.guns().getFirst().gun().getAmount());
        verify(stored, never()).setAmount(anyInt());
    }

    @Test
    void chunkLimitCannotExceedFiveThousand() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeaponLootCatalog.Settings(10, 25, 5_001));
    }

    @Test
    void generatedChestCountMustStayWithinSupportedRange() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WeaponLootCatalog.Settings(10, 25, 2_000_000L, 5_000, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WeaponLootCatalog.Settings(10, 25, 2_000_000L, 5_000, 1_001)),
                () -> assertEquals(1_000,
                        new WeaponLootCatalog.Settings(10, 25, 2_000_000L, 5_000, 1_000)
                                .generatedChestCount())
        );
    }

    @Test
    void lootStackRangesCannotExceedOneInventoryStack() {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WeaponLootCatalog.GunEntry(
                                UUID.randomUUID(), item, LootRarity.COMMON, item, 1, 65)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WeaponLootCatalog.GrenadeEntry(
                                UUID.randomUUID(), item, LootRarity.COMMON, 1, 65))
        );
    }
}
