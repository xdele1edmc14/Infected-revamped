package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemSnapshotCodecTest {
    @Test
    void usesBinaryItemSerializationAndClonesDecodedSnapshot() {
        ItemStack source = mock(ItemStack.class);
        ItemStack sourceClone = mock(ItemStack.class);
        ItemStack decoded = mock(ItemStack.class);
        ItemStack decodedClone = mock(ItemStack.class);
        when(source.getType()).thenReturn(Material.STICK);
        when(source.clone()).thenReturn(sourceClone);
        when(sourceClone.serializeAsBytes()).thenReturn(new byte[]{1, 2, 3, 4});
        when(decoded.getType()).thenReturn(Material.STICK);
        when(decoded.clone()).thenReturn(decodedClone);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(any(byte[].class))).thenReturn(decoded);
            ItemSnapshotCodec codec = new ItemSnapshotCodec();

            assertEquals(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}), codec.encode(source));
            assertSame(decodedClone, codec.decode(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4})));
        }
    }

    @Test
    void rejectsAirSnapshots() {
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        assertThrows(IllegalArgumentException.class, () -> new ItemSnapshotCodec().encode(air));
    }
}
