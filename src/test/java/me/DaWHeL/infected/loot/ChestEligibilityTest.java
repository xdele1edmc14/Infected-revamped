package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChestEligibilityTest {
    private final NamespacedKey markerKey = new NamespacedKey("infected", "generated-weapon-chest");

    @Test
    void acceptsAPluginTaggedChestEvenWhenItsPlatformIsDamaged() {
        Chest chest = mock(Chest.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(chest.getPersistentDataContainer()).thenReturn(data);
        when(data.has(markerKey, PersistentDataType.STRING)).thenReturn(true);

        assertTrue(new ChestEligibility(markerKey).isEligible(chest));
    }

    @Test
    void acceptsAManualChestOnlyWhenAllNineBlocksUnderItAreGold() {
        Chest chest = chestWithFoundation(Material.GOLD_BLOCK, false);

        assertTrue(new ChestEligibility(markerKey).isEligible(chest));
    }

    @Test
    void rejectsAnOrdinaryChestAndAnIncompleteGoldFoundation() {
        Chest ordinary = chestWithFoundation(Material.GRASS_BLOCK, false);
        Chest incomplete = chestWithFoundation(Material.GOLD_BLOCK, true);

        assertFalse(new ChestEligibility(markerKey).isEligible(ordinary));
        assertFalse(new ChestEligibility(markerKey).isEligible(incomplete));
    }

    private static Chest chestWithFoundation(Material material, boolean missingCorner) {
        Chest chest = mock(Chest.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        Block chestBlock = mock(Block.class);
        when(chest.getPersistentDataContainer()).thenReturn(data);
        when(chest.getBlock()).thenReturn(chestBlock);
        when(chestBlock.getRelative(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            Block block = mock(Block.class);
            int dx = invocation.getArgument(0);
            int dz = invocation.getArgument(2);
            when(block.getType()).thenReturn(missingCorner && dx == 1 && dz == 1 ? Material.STONE : material);
            return block;
        });
        return chest;
    }
}
