package me.DaWHeL.infected.loot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class WeaponLootRepository {
    private static final WeaponLootCatalog.Settings DEFAULTS =
            new WeaponLootCatalog.Settings(10, 25, 2_000_000L, 5_000, 100);
    private final File file;
    private final ItemSnapshotCodec codec;
    private BlockPoint point1;
    private BlockPoint point2;
    private WeaponLootCatalog.Settings settings = DEFAULTS;
    private final List<WeaponLootCatalog.GunEntry> guns = new ArrayList<>();
    private final List<WeaponLootCatalog.GrenadeEntry> grenades = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Set<UUID> removedGunIds = new HashSet<>();
    private final Set<UUID> removedGrenadeIds = new HashSet<>();
    private YamlConfiguration backing = new YamlConfiguration();
    private boolean loadBlocked;

    public WeaponLootRepository(File dataFolder, ItemSnapshotCodec codec) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        this.file = new File(dataFolder, "weapon-loot.yml");
        this.codec = Objects.requireNonNull(codec, "codec");
        reload();
    }

    public synchronized WeaponLootCatalog snapshot() {
        return new WeaponLootCatalog(point1, point2, settings, guns, grenades, errors);
    }

    public synchronized List<String> reload() {
        if (!file.isFile()) {
            point1 = null;
            point2 = null;
            settings = DEFAULTS;
            guns.clear();
            grenades.clear();
            errors.clear();
            removedGunIds.clear();
            removedGrenadeIds.clear();
            backing = new YamlConfiguration();
            loadBlocked = false;
            return List.of();
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            point1 = null;
            point2 = null;
            settings = DEFAULTS;
            guns.clear();
            grenades.clear();
            removedGunIds.clear();
            removedGrenadeIds.clear();
            backing = new YamlConfiguration();
            loadBlocked = true;
            errors.clear();
            errors.add("Could not load weapon-loot.yml: " + exception.getMessage());
            return List.copyOf(errors);
        }
        point1 = null;
        point2 = null;
        settings = DEFAULTS;
        guns.clear();
        grenades.clear();
        errors.clear();
        removedGunIds.clear();
        removedGrenadeIds.clear();
        backing = loaded;
        loadBlocked = false;
        YamlConfiguration yaml = loaded;
        point1 = readPoint(yaml, "region.point-1");
        point2 = readPoint(yaml, "region.point-2");
        try {
            settings = new WeaponLootCatalog.Settings(
                    yaml.getInt("settings.second-gun-chance", 10),
                    yaml.getInt("settings.grenade-chance", 25),
                    2_000_000L,
                    yaml.getInt("settings.max-chunks", 5_000),
                    yaml.getInt("settings.generated-chest-count", 100));
        } catch (IllegalArgumentException exception) {
            loadBlocked = true;
            errors.add("Invalid global loot settings: " + exception.getMessage());
        }
        readGuns(yaml.getConfigurationSection("guns"));
        readGrenades(yaml.getConfigurationSection("grenades"));
        return List.copyOf(errors);
    }

    public synchronized void setPoint(int number, BlockPoint point) {
        ensureWritable();
        if (number != 1 && number != 2) throw new IllegalArgumentException("Point number must be 1 or 2.");
        Objects.requireNonNull(point, "point");
        if (number == 1) {
            point1 = point;
            if (point2 != null && !point.world().equals(point2.world())) point2 = null;
        } else {
            point2 = point;
            if (point1 != null && !point.world().equals(point1.world())) point1 = null;
        }
        save();
    }

    public synchronized void setChances(int secondGunChance, int grenadeChance) {
        ensureWritable();
        settings = new WeaponLootCatalog.Settings(secondGunChance, grenadeChance,
                settings.maxVolume(), settings.maxChunks(), settings.generatedChestCount());
        save();
    }

    public synchronized void setMaxChunks(int maxChunks) {
        ensureWritable();
        settings = new WeaponLootCatalog.Settings(settings.secondGunChance(), settings.grenadeChance(),
                settings.maxVolume(), maxChunks, settings.generatedChestCount());
        save();
    }

    public synchronized void setGeneratedChestCount(int generatedChestCount) {
        ensureWritable();
        settings = new WeaponLootCatalog.Settings(settings.secondGunChance(), settings.grenadeChance(),
                settings.maxVolume(), settings.maxChunks(), generatedChestCount);
        save();
    }

    public synchronized UUID addGun(ItemStack gun) {
        ensureWritable();
        UUID id = UUID.randomUUID();
        guns.add(new WeaponLootCatalog.GunEntry(id, gun, LootRarity.COMMON, null, 1, 1));
        save();
        return id;
    }

    public synchronized void replaceGun(UUID id, ItemStack item) {
        ensureWritable();
        int index = gunIndex(id);
        WeaponLootCatalog.GunEntry old = guns.get(index);
        guns.set(index, new WeaponLootCatalog.GunEntry(id, item, old.rarity(), old.ammo(),
                old.minAmmoBundles(), old.maxAmmoBundles()));
        save();
    }

    public synchronized void setGunAmmo(UUID id, ItemStack ammo) {
        ensureWritable();
        int index = gunIndex(id);
        WeaponLootCatalog.GunEntry old = guns.get(index);
        guns.set(index, new WeaponLootCatalog.GunEntry(id, old.gun(), old.rarity(), ammo,
                old.minAmmoBundles(), old.maxAmmoBundles()));
        save();
    }

    public synchronized void updateGun(UUID id, LootRarity rarity, int minBundles, int maxBundles) {
        ensureWritable();
        int index = gunIndex(id);
        WeaponLootCatalog.GunEntry old = guns.get(index);
        guns.set(index, new WeaponLootCatalog.GunEntry(id, old.gun(), rarity, old.ammo(), minBundles, maxBundles));
        save();
    }

    public synchronized boolean removeGun(UUID id) {
        ensureWritable();
        boolean removed = guns.removeIf(entry -> entry.id().equals(id));
        if (removed) {
            removedGunIds.add(id);
            save();
        }
        return removed;
    }

    public synchronized UUID addGrenade(ItemStack item) {
        ensureWritable();
        UUID id = UUID.randomUUID();
        grenades.add(new WeaponLootCatalog.GrenadeEntry(id, item, LootRarity.COMMON, 1, 1));
        save();
        return id;
    }

    public synchronized void replaceGrenade(UUID id, ItemStack item) {
        ensureWritable();
        int index = grenadeIndex(id);
        WeaponLootCatalog.GrenadeEntry old = grenades.get(index);
        grenades.set(index, new WeaponLootCatalog.GrenadeEntry(id, item, old.rarity(),
                old.minQuantity(), old.maxQuantity()));
        save();
    }

    public synchronized void updateGrenade(UUID id, LootRarity rarity, int min, int max) {
        ensureWritable();
        int index = grenadeIndex(id);
        WeaponLootCatalog.GrenadeEntry old = grenades.get(index);
        grenades.set(index, new WeaponLootCatalog.GrenadeEntry(id, old.item(), rarity, min, max));
        save();
    }

    public synchronized boolean removeGrenade(UUID id) {
        ensureWritable();
        boolean removed = grenades.removeIf(entry -> entry.id().equals(id));
        if (removed) {
            removedGrenadeIds.add(id);
            save();
        }
        return removed;
    }

    private int gunIndex(UUID id) {
        for (int i = 0; i < guns.size(); i++) if (guns.get(i).id().equals(id)) return i;
        throw new IllegalArgumentException("That gun no longer exists.");
    }

    private void ensureWritable() {
        if (loadBlocked) {
            throw new IllegalStateException(
                    "weapon-loot.yml has invalid YAML and cannot be changed until the file is repaired and reloaded.");
        }
    }

    private int grenadeIndex(UUID id) {
        for (int i = 0; i < grenades.size(); i++) if (grenades.get(i).id().equals(id)) return i;
        throw new IllegalArgumentException("That grenade no longer exists.");
    }

    private void readGuns(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ItemStack gun = codec.decode(section.getString(key + ".item", ""));
                String ammoPayload = section.getString(key + ".ammo");
                ItemStack ammo = ammoPayload == null || ammoPayload.isBlank() ? null : codec.decode(ammoPayload);
                guns.add(new WeaponLootCatalog.GunEntry(id, gun,
                        LootRarity.valueOf(section.getString(key + ".rarity", "COMMON")), ammo,
                        section.getInt(key + ".min-ammo-bundles", 1),
                        section.getInt(key + ".max-ammo-bundles", 1)));
            } catch (RuntimeException exception) {
                errors.add("Invalid gun " + key + ": " + exception.getMessage());
            }
        }
    }

    private void readGrenades(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                grenades.add(new WeaponLootCatalog.GrenadeEntry(id,
                        codec.decode(section.getString(key + ".item", "")),
                        LootRarity.valueOf(section.getString(key + ".rarity", "COMMON")),
                        section.getInt(key + ".min-quantity", 1), section.getInt(key + ".max-quantity", 1)));
            } catch (RuntimeException exception) {
                errors.add("Invalid grenade " + key + ": " + exception.getMessage());
            }
        }
    }

    private static BlockPoint readPoint(YamlConfiguration yaml, String path) {
        String world = yaml.getString(path + ".world");
        if (world == null || world.isBlank()) return null;
        return new BlockPoint(world, yaml.getInt(path + ".x"), yaml.getInt(path + ".y"), yaml.getInt(path + ".z"));
    }

    private synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create plugin data folder.");
            }
            File temporary = new File(parent, file.getName() + ".tmp");
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(backing.saveToString());
            writePoint(yaml, "region.point-1", point1);
            writePoint(yaml, "region.point-2", point2);
            yaml.set("settings.second-gun-chance", settings.secondGunChance());
            yaml.set("settings.grenade-chance", settings.grenadeChance());
            yaml.set("settings.max-volume", null);
            yaml.set("settings.max-chunks", settings.maxChunks());
            yaml.set("settings.generated-chest-count", settings.generatedChestCount());
            removedGunIds.forEach(id -> yaml.set("guns." + id, null));
            removedGrenadeIds.forEach(id -> yaml.set("grenades." + id, null));
            for (WeaponLootCatalog.GunEntry gun : guns) {
                String path = "guns." + gun.id();
                yaml.set(path + ".item", codec.encode(gun.gun()));
                yaml.set(path + ".rarity", gun.rarity().name());
                yaml.set(path + ".ammo", gun.ammo() == null ? null : codec.encode(gun.ammo()));
                yaml.set(path + ".min-ammo-bundles", gun.minAmmoBundles());
                yaml.set(path + ".max-ammo-bundles", gun.maxAmmoBundles());
            }
            for (WeaponLootCatalog.GrenadeEntry grenade : grenades) {
                String path = "grenades." + grenade.id();
                yaml.set(path + ".item", codec.encode(grenade.item()));
                yaml.set(path + ".rarity", grenade.rarity().name());
                yaml.set(path + ".min-quantity", grenade.minQuantity());
                yaml.set(path + ".max-quantity", grenade.maxQuantity());
            }
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            reload();
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            reload();
            throw new IllegalStateException("Could not save weapon-loot.yml.", exception);
        }
    }

    private static void writePoint(YamlConfiguration yaml, String path, BlockPoint point) {
        if (point == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".world", point.world());
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
    }
}
