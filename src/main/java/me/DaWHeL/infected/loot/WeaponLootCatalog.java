package me.DaWHeL.infected.loot;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WeaponLootCatalog(
        BlockPoint point1,
        BlockPoint point2,
        Settings settings,
        List<GunEntry> guns,
        List<GrenadeEntry> grenades,
        List<String> errors
) {
    public static final int MAX_STACK_RANGE = 64;

    public WeaponLootCatalog {
        settings = Objects.requireNonNull(settings, "settings");
        guns = guns.stream().map(GunEntry::copy).toList();
        grenades = grenades.stream().map(GrenadeEntry::copy).toList();
        errors = List.copyOf(errors);
    }

    @Override
    public List<GunEntry> guns() {
        return guns.stream().map(GunEntry::copy).toList();
    }

    @Override
    public List<GrenadeEntry> grenades() {
        return grenades.stream().map(GrenadeEntry::copy).toList();
    }

    public record Settings(int secondGunChance, int grenadeChance, long maxVolume, int maxChunks,
                           int generatedChestCount) {
        public static final int MAX_CONFIGURED_CHUNKS = 5_000;
        public static final int MAX_GENERATED_CHESTS = 1_000;

        public Settings(int secondGunChance, int grenadeChance, int maxChunks) {
            this(secondGunChance, grenadeChance, 2_000_000L, maxChunks, 100);
        }

        public Settings(int secondGunChance, int grenadeChance, long maxVolume, int maxChunks) {
            this(secondGunChance, grenadeChance, maxVolume, maxChunks, 100);
        }

        public Settings {
            if (maxChunks < 1 || maxChunks > MAX_CONFIGURED_CHUNKS) {
                throw new IllegalArgumentException("Chunk scan limit must be between 1 and 5,000.");
            }
            if (secondGunChance < 0 || secondGunChance > 100 || grenadeChance < 0 || grenadeChance > 100
                    || maxVolume < 1) {
                throw new IllegalArgumentException("Invalid weapon-loot settings.");
            }
            if (generatedChestCount < 1 || generatedChestCount > MAX_GENERATED_CHESTS) {
                throw new IllegalArgumentException("Generated chest count must be between 1 and 1,000.");
            }
        }
    }

    public record GunEntry(UUID id, ItemStack gun, LootRarity rarity, ItemStack ammo,
                           int minAmmoBundles, int maxAmmoBundles) {
        public GunEntry {
            Objects.requireNonNull(id, "id");
            gun = Objects.requireNonNull(gun, "gun").clone();
            Objects.requireNonNull(rarity, "rarity");
            ammo = ammo == null ? null : ammo.clone();
            if (minAmmoBundles < 1 || maxAmmoBundles < minAmmoBundles
                    || maxAmmoBundles > MAX_STACK_RANGE) {
                throw new IllegalArgumentException("Ammo bundle range must stay between 1 and 64.");
            }
        }

        @Override public ItemStack gun() { return gun.clone(); }
        @Override public ItemStack ammo() { return ammo == null ? null : ammo.clone(); }
        GunEntry copy() { return new GunEntry(id, gun, rarity, ammo, minAmmoBundles, maxAmmoBundles); }
    }

    public record GrenadeEntry(UUID id, ItemStack item, LootRarity rarity, int minQuantity, int maxQuantity) {
        public GrenadeEntry {
            Objects.requireNonNull(id, "id");
            item = Objects.requireNonNull(item, "item").clone();
            Objects.requireNonNull(rarity, "rarity");
            if (minQuantity < 1 || maxQuantity < minQuantity || maxQuantity > MAX_STACK_RANGE) {
                throw new IllegalArgumentException("Grenade quantity range must stay between 1 and 64.");
            }
        }

        @Override public ItemStack item() { return item.clone(); }
        GrenadeEntry copy() { return new GrenadeEntry(id, item, rarity, minQuantity, maxQuantity); }
    }
}
