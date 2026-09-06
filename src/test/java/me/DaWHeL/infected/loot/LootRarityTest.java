package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LootRarityTest {
    @Test
    void rarerPresetsCarryProgressivelyLowerSelectionWeights() {
        assertAll(
                () -> assertEquals(100, LootRarity.COMMON.weight()),
                () -> assertEquals(50, LootRarity.UNCOMMON.weight()),
                () -> assertEquals(20, LootRarity.RARE.weight()),
                () -> assertEquals(8, LootRarity.EPIC.weight()),
                () -> assertEquals(2, LootRarity.LEGENDARY.weight()),
                () -> assertEquals(LootRarity.UNCOMMON, LootRarity.COMMON.next()),
                () -> assertEquals(LootRarity.COMMON, LootRarity.LEGENDARY.next()),
                () -> assertEquals(LootRarity.LEGENDARY, LootRarity.COMMON.previous())
        );
    }
}
