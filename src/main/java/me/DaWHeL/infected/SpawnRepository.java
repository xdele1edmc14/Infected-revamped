package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class SpawnRepository {
    private static final String HOLDING_SPAWN = "infected-spawn";
    private static final String LEGACY_POINTS = "teleports";
    private static final String MIGRATION_MARKER = "migrations.role-spawns";

    private final InfectedPlugin plugin;

    public SpawnRepository(InfectedPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Optional<StoredSpawn> holdingSpawn() {
        return readLocation(HOLDING_SPAWN);
    }

    public Optional<Location> loadedHoldingSpawn() {
        return holdingSpawn().flatMap(this::resolve);
    }

    public List<NamedSpawn> points(SpawnRole role) {
        Objects.requireNonNull(role, "role");
        ConfigurationSection section = config().getConfigurationSection(rolePath(role));
        if (section == null) {
            return List.of();
        }

        return section.getKeys(false).stream()
                .map(name -> readLocation(rolePath(role) + "." + name)
                        .map(location -> new NamedSpawn(name, location)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(NamedSpawn::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(NamedSpawn::name))
                .toList();
    }

    public List<Location> loadedLocations(SpawnRole role) {
        return points(role).stream()
                .map(NamedSpawn::location)
                .map(this::resolve)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<Location> randomLocation(SpawnRole role, Random random) {
        Objects.requireNonNull(random, "random");
        List<Location> locations = loadedLocations(role);
        if (locations.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(locations.get(random.nextInt(locations.size())).clone());
    }

    public void savePoint(SpawnRole role, String name, Location location) {
        validateName(name);
        writeLocation(rolePath(Objects.requireNonNull(role, "role")) + "." + name,
                fromLocation(location));
        plugin.saveConfig();
    }

    public boolean deletePoint(SpawnRole role, String name) {
        validateName(name);
        String path = rolePath(Objects.requireNonNull(role, "role")) + "." + name;
        if (!config().contains(path)) {
            return false;
        }
        config().set(path, null);
        plugin.saveConfig();
        return true;
    }

    public MigrationResult migrateLegacyTeleports() {
        if (config().getBoolean(MIGRATION_MARKER, false)) {
            return new MigrationResult(false, 0, List.of());
        }

        int copiedEntries = 0;
        List<String> skippedPaths = new ArrayList<>();
        ConfigurationSection legacy = config().getConfigurationSection(LEGACY_POINTS);
        if (legacy != null) {
            List<String> names = legacy.getKeys(false).stream().sorted().toList();
            for (String name : names) {
                String legacyPath = LEGACY_POINTS + "." + name;
                Optional<StoredSpawn> stored = readLocation(legacyPath);
                if (stored.isEmpty()) {
                    skippedPaths.add(legacyPath);
                    continue;
                }
                for (SpawnRole role : SpawnRole.values()) {
                    String destination = rolePath(role) + "." + name;
                    if (!config().contains(destination)) {
                        writeLocation(destination, stored.get());
                        copiedEntries++;
                    }
                }
            }
        }

        config().set(MIGRATION_MARKER, true);
        plugin.saveConfig();
        return new MigrationResult(true, copiedEntries, List.copyOf(skippedPaths));
    }

    private Optional<StoredSpawn> readLocation(String path) {
        ConfigurationSection section = config().getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }
        String world = section.getString("world");
        if (world == null || world.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StoredSpawn(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        ));
    }

    private Optional<Location> resolve(StoredSpawn stored) {
        World world = plugin.getServer().getWorld(stored.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(
                world,
                stored.x(),
                stored.y(),
                stored.z(),
                stored.yaw(),
                stored.pitch()
        ));
    }

    private void writeLocation(String path, StoredSpawn stored) {
        FileConfiguration config = config();
        config.set(path + ".world", stored.world());
        config.set(path + ".x", stored.x());
        config.set(path + ".y", stored.y());
        config.set(path + ".z", stored.z());
        config.set(path + ".yaw", stored.yaw());
        config.set(path + ".pitch", stored.pitch());
    }

    private static StoredSpawn fromLocation(Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location must have a world.");
        }
        return new StoredSpawn(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.contains(".")) {
            throw new IllegalArgumentException("Spawn point names cannot be blank or contain periods.");
        }
    }

    private static String rolePath(SpawnRole role) {
        return "spawns." + role.configKey();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public record StoredSpawn(String world, double x, double y, double z, float yaw, float pitch) {
    }

    public record NamedSpawn(String name, StoredSpawn location) {
    }

    public record MigrationResult(boolean migrated, int copiedEntries, List<String> skippedPaths) {
        public MigrationResult {
            skippedPaths = List.copyOf(skippedPaths);
        }
    }
}
