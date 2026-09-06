package me.DaWHeL.infected.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChestLootGeneratorTest {
    @Test
    void guaranteedSecondGunIsDifferentAndEveryGunBringsItsAmmo() {
        WeaponLootCatalog catalog = new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(100, 100, 2_000_000L, 256),
                List.of(
                        gun(Material.IRON_SWORD, Material.IRON_NUGGET),
                        gun(Material.DIAMOND_SWORD, Material.DIAMOND)
                ),
                List.of(new WeaponLootCatalog.GrenadeEntry(UUID.randomUUID(),
                        item(Material.SNOWBALL), LootRarity.COMMON, 2, 2)), List.of());

        ChestLootGenerator.PlanResult result = new ChestLootGenerator(new Random(4)).plan(catalog, 27);

        assertTrue(result.success(), () -> String.join(", ", result.errors()));
        List<Material> materials = result.contents().values().stream().map(ItemStack::getType).toList();
        assertAll(
                () -> assertEquals(1, materials.stream().filter(Material.IRON_SWORD::equals).count()),
                () -> assertEquals(1, materials.stream().filter(Material.DIAMOND_SWORD::equals).count()),
                () -> assertTrue(materials.contains(Material.IRON_NUGGET)),
                () -> assertTrue(materials.contains(Material.DIAMOND)),
                () -> assertEquals(2, result.contents().values().stream()
                        .filter(item -> item.getType() == Material.SNOWBALL).findFirst().orElseThrow().getAmount())
        );
    }

    @Test
    void oneGunIsStillGuaranteedWhenBonusChancesAreZero() {
        WeaponLootCatalog catalog = new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(0, 0, 2_000_000L, 256),
                List.of(gun(Material.IRON_SWORD, Material.IRON_NUGGET)), List.of(), List.of());

        ChestLootGenerator.PlanResult result = new ChestLootGenerator(new Random(1)).plan(catalog, 27);

        assertTrue(result.success());
        assertEquals(Set.of(Material.IRON_NUGGET, Material.IRON_SWORD),
                result.contents().values().stream().map(ItemStack::getType).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void ammoBundlesAreConsolidatedIntoTheFewestLegalStacks() {
        WeaponLootCatalog.GunEntry gun = new WeaponLootCatalog.GunEntry(
                UUID.randomUUID(), item(Material.IRON_SWORD), LootRarity.COMMON,
                item(Material.IRON_NUGGET), 10, 10);
        WeaponLootCatalog catalog = new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(0, 0, 2_000_000L, 256),
                List.of(gun), List.of(), List.of());

        ChestLootGenerator.PlanResult result = new ChestLootGenerator(new Random(7)).plan(catalog, 27);

        List<ItemStack> ammo = result.contents().values().stream()
                .filter(stack -> stack.getType() == Material.IRON_NUGGET)
                .toList();
        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(1, ammo.size()),
                () -> assertEquals(10, ammo.getFirst().getAmount())
        );
    }

    private static WeaponLootCatalog.GunEntry gun(Material gun, Material ammo) {
        return new WeaponLootCatalog.GunEntry(UUID.randomUUID(), item(gun), LootRarity.COMMON,
                item(ammo), 1, 1);
    }

    private static ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger amount = new AtomicInteger(1);
        when(item.clone()).thenReturn(item);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(ignored -> amount.get());
        when(item.getMaxStackSize()).thenReturn(64);
        doAnswer(invocation -> {
            amount.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(anyInt());
        return item;
    }
}
