package me.DaWHeL.infected.loot;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class ChestLootGenerator {
    private final RandomGenerator random;

    public ChestLootGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public PlanResult plan(WeaponLootCatalog catalog, int inventorySize) {
        Objects.requireNonNull(catalog, "catalog");
        if (!catalog.errors().isEmpty()) return PlanResult.failure(catalog.errors());
        List<WeaponLootCatalog.GunEntry> guns = catalog.guns();
        if (guns.isEmpty()) return PlanResult.failure(List.of("At least one gun is required."));
        if (guns.stream().anyMatch(gun -> gun.ammo() == null)) {
            return PlanResult.failure(List.of("Every gun must have linked ammo."));
        }

        List<WeaponLootCatalog.GunEntry> selected = new ArrayList<>();
        WeaponLootCatalog.GunEntry first = weighted(guns, WeaponLootCatalog.GunEntry::rarity);
        selected.add(first);
        if (guns.size() > 1 && roll(catalog.settings().secondGunChance())) {
            selected.add(weighted(guns.stream().filter(gun -> !gun.id().equals(first.id())).toList(),
                    WeaponLootCatalog.GunEntry::rarity));
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (WeaponLootCatalog.GunEntry gun : selected) {
            stacks.add(gun.gun());
            int bundles = between(gun.minAmmoBundles(), gun.maxAmmoBundles());
            ItemStack ammo = gun.ammo();
            split(stacks, ammo, Math.multiplyExact(ammo.getAmount(), bundles));
        }
        List<WeaponLootCatalog.GrenadeEntry> grenades = catalog.grenades();
        if (!grenades.isEmpty() && roll(catalog.settings().grenadeChance())) {
            WeaponLootCatalog.GrenadeEntry grenade = weighted(grenades, WeaponLootCatalog.GrenadeEntry::rarity);
            split(stacks, grenade.item(), between(grenade.minQuantity(), grenade.maxQuantity()));
        }
        if (stacks.size() > inventorySize) {
            return PlanResult.failure(List.of("Generated loot needs " + stacks.size()
                    + " slots but this chest has " + inventorySize + "."));
        }

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) slots.add(i);
        for (int i = slots.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int value = slots.get(i);
            slots.set(i, slots.get(swap));
            slots.set(swap, value);
        }
        Map<Integer, ItemStack> contents = new LinkedHashMap<>();
        for (int i = 0; i < stacks.size(); i++) contents.put(slots.get(i), stacks.get(i).clone());
        return PlanResult.success(contents);
    }

    private boolean roll(int percent) {
        return percent >= 100 || (percent > 0 && random.nextInt(100) < percent);
    }

    private int between(int min, int max) {
        return min == max ? min : random.nextInt(min, max + 1);
    }

    private static void split(List<ItemStack> destination, ItemStack template, int quantity) {
        int remaining = quantity;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            int amount = Math.min(remaining, stack.getMaxStackSize());
            stack.setAmount(amount);
            destination.add(stack);
            remaining -= amount;
        }
    }

    private <T> T weighted(List<T> entries, java.util.function.Function<T, LootRarity> rarity) {
        long total = entries.stream().mapToLong(entry -> rarity.apply(entry).weight()).sum();
        long roll = random.nextLong(total);
        for (T entry : entries) {
            roll -= rarity.apply(entry).weight();
            if (roll < 0) return entry;
        }
        throw new IllegalStateException("Weighted selection failed.");
    }

    public record PlanResult(Map<Integer, ItemStack> contents, List<String> errors) {
        public PlanResult {
            Map<Integer, ItemStack> copy = new LinkedHashMap<>();
            contents.forEach((slot, item) -> copy.put(slot, item.clone()));
            contents = Map.copyOf(copy);
            errors = List.copyOf(errors);
        }
        public static PlanResult success(Map<Integer, ItemStack> contents) { return new PlanResult(contents, List.of()); }
        public static PlanResult failure(List<String> errors) { return new PlanResult(Map.of(), errors); }
        public boolean success() { return errors.isEmpty(); }
        @Override public Map<Integer, ItemStack> contents() {
            Map<Integer, ItemStack> copy = new LinkedHashMap<>();
            contents.forEach((slot, item) -> copy.put(slot, item.clone()));
            return Map.copyOf(copy);
        }
    }
}
