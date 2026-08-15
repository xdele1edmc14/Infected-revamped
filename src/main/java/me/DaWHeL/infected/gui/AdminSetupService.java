package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AdminSetupService {
    private static final String INFECTED_SPAWN = "infected-spawn";
    private static final String TELEPORTS = "teleports";

    private final InfectedPlugin plugin;

    public AdminSetupService(InfectedPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Optional<StoredLocation> infectedSpawn() {
        return readLocation(INFECTED_SPAWN);
    }

    public List<TeleportPoint> teleportPoints() {
        ConfigurationSection section = config().getConfigurationSection(TELEPORTS);
        if (section == null) {
            return List.of();
        }

        return section.getKeys(false).stream()
                .map(name -> readLocation(TELEPORTS + "." + name)
                        .map(location -> new TeleportPoint(name, location)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(TeleportPoint::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(TeleportPoint::name))
                .toList();
    }

    public SetupSnapshot snapshot(int survivors, int infected) {
        return new SetupSnapshot(
                infectedSpawn().isPresent(),
                teleportPoints().size(),
                survivors,
                infected,
                config().getInt("settings.starting-zombies", 5),
                config().getInt("settings.infected-teleport-delay", 10),
                config().getInt("settings.teleport-batch-size", 5)
        );
    }

    public void setInfectedSpawn(Location location) {
        writeLocation(INFECTED_SPAWN, location);
        plugin.saveConfig();
    }

    public boolean clearInfectedSpawn() {
        if (!config().contains(INFECTED_SPAWN)) {
            return false;
        }
        config().set(INFECTED_SPAWN, null);
        plugin.saveConfig();
        return true;
    }

    public void saveTeleportPoint(String name, Location location) {
        validatePointName(name);
        writeLocation(TELEPORTS + "." + name, location);
        plugin.saveConfig();
    }

    public boolean deleteTeleportPoint(String name) {
        validatePointName(name);
        String path = TELEPORTS + "." + name;
        if (!config().contains(path)) {
            return false;
        }
        config().set(path, null);
        plugin.saveConfig();
        return true;
    }

    public static void validatePointName(String name) {
        if (name == null || name.isBlank() || name.contains(".")) {
            throw new IllegalArgumentException("Teleport point names cannot be blank or contain periods.");
        }
    }

    private Optional<StoredLocation> readLocation(String path) {
        ConfigurationSection section = config().getConfigurationSection(path);
        if (section == null) {
            return Optional.empty();
        }
        String world = section.getString("world");
        if (world == null || world.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new StoredLocation(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        ));
    }

    private void writeLocation(String path, Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location must have a world.");
        }
        FileConfiguration config = config();
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    }

    public record TeleportPoint(String name, StoredLocation location) {
    }

    public record SetupSnapshot(
            boolean infectedSpawnConfigured,
            int teleportPointCount,
            int survivors,
            int infected,
            int startingInfected,
            int infectedTeleportDelay,
            int teleportBatchSize
    ) {
        public boolean ready() {
            return infectedSpawnConfigured && teleportPointCount > 0;
        }
    }
}
