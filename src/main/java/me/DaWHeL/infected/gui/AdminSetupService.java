package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.InfectedRespawnSelector;
import me.DaWHeL.infected.RoundStartValidator;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AdminSetupService {
    private static final String INFECTED_SPAWN = "infected-spawn";
    private static final Pattern POINT_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private final InfectedPlugin plugin;
    private final SpawnRepository spawnRepository;

    public AdminSetupService(InfectedPlugin plugin) {
        this(plugin, new SpawnRepository(plugin));
    }

    public AdminSetupService(InfectedPlugin plugin, SpawnRepository spawnRepository) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
    }

    public Optional<StoredLocation> infectedSpawn() {
        return readLocation(INFECTED_SPAWN);
    }

    public List<TeleportPoint> teleportPoints() {
        return teleportPoints(SpawnRole.SURVIVOR);
    }

    public List<TeleportPoint> teleportPoints(SpawnRole role) {
        return spawnRepository.points(role).stream()
                .map(point -> new TeleportPoint(point.name(), storedLocation(point.location())))
                .toList();
    }

    public SetupSnapshot snapshot(int survivors, int infected) {
        int survivorSpawns = spawnRepository.loadedLocations(SpawnRole.SURVIVOR).size();
        int releaseSpawns = spawnRepository.loadedLocations(SpawnRole.INFECTED_RELEASE).size();
        int respawnSpawns = (int) spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN).stream()
                .filter(InfectedRespawnSelector::isSafe)
                .count();
        return new SetupSnapshot(
                spawnRepository.loadedHoldingSpawn().isPresent(),
                survivorSpawns,
                releaseSpawns,
                respawnSpawns,
                survivors,
                infected,
                config().getInt("settings.starting-zombies", 5),
                config().getInt("settings.infected-teleport-delay", 10),
                config().getInt("settings.teleport-batch-size", 5),
                config().getInt("settings.teleport-delay", 20),
                config().getInt("settings.minimum-players", 2),
                config().getInt("settings.start-countdown-seconds", 10),
                config().getInt("settings.round-time-limit-seconds", 0)
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
        saveTeleportPoint(SpawnRole.SURVIVOR, name, location);
    }

    public void saveTeleportPoint(SpawnRole role, String name, Location location) {
        validatePointName(name);
        if (spawnRepository.points(role).stream()
                .anyMatch(point -> point.name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("A spawn named '" + name
                    + "' already exists in this spawn group.");
        }
        spawnRepository.savePoint(role, name, location);
    }

    public boolean deleteTeleportPoint(String name) {
        return deleteTeleportPoint(SpawnRole.SURVIVOR, name);
    }

    public boolean deleteTeleportPoint(SpawnRole role, String name) {
        validatePointName(name);
        return spawnRepository.deletePoint(role, name);
    }

    public static void validatePointName(String name) {
        if (name == null || !POINT_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Spawn names must be 1-32 characters using only letters, numbers, _ or -.");
        }
        if (name.equalsIgnoreCase("close")) {
            throw new IllegalArgumentException("The name 'close' is reserved for cancelling spawn creation.");
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
        Optional<Double> x = finiteCoordinate(section, "x");
        Optional<Double> y = finiteCoordinate(section, "y");
        Optional<Double> z = finiteCoordinate(section, "z");
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StoredLocation(
                world,
                x.get(),
                y.get(),
                z.get(),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        ));
    }

    private static Optional<Double> finiteCoordinate(ConfigurationSection section, String key) {
        Object value = section.get(key);
        if (!(value instanceof Number number)) {
            return Optional.empty();
        }
        double coordinate = number.doubleValue();
        return Double.isFinite(coordinate) ? Optional.of(coordinate) : Optional.empty();
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

    private static StoredLocation storedLocation(SpawnRepository.StoredSpawn stored) {
        return new StoredLocation(
                stored.world(),
                stored.x(),
                stored.y(),
                stored.z(),
                stored.yaw(),
                stored.pitch()
        );
    }

    public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    }

    public record TeleportPoint(String name, StoredLocation location) {
    }

    public record SetupSnapshot(
            boolean infectedSpawnConfigured,
            int survivorSpawnCount,
            int infectedReleaseSpawnCount,
            int infectedRespawnSpawnCount,
            int survivors,
            int infected,
            int startingInfected,
            int infectedTeleportDelay,
            int teleportBatchSize,
            int teleportDelayTicks,
            int minimumPlayers,
            int startCountdownSeconds,
            int roundTimeLimitSeconds
    ) {
        public SetupSnapshot(
                boolean infectedSpawnConfigured,
                int survivorSpawnCount,
                int infectedReleaseSpawnCount,
                int infectedRespawnSpawnCount,
                int survivors,
                int infected,
                int startingInfected,
                int infectedTeleportDelay,
                int teleportBatchSize,
                int teleportDelayTicks
        ) {
            this(infectedSpawnConfigured, survivorSpawnCount, infectedReleaseSpawnCount,
                    infectedRespawnSpawnCount, survivors, infected, startingInfected,
                    infectedTeleportDelay, teleportBatchSize, teleportDelayTicks,
                    Math.max(2, startingInfected + 1), 10, 0);
        }

        public SetupSnapshot(
                boolean infectedSpawnConfigured,
                int teleportPointCount,
                int survivors,
                int infected,
                int startingInfected,
                int infectedTeleportDelay,
                int teleportBatchSize
        ) {
            this(
                    infectedSpawnConfigured,
                    teleportPointCount,
                    teleportPointCount,
                    teleportPointCount,
                    survivors,
                    infected,
                    startingInfected,
                    infectedTeleportDelay,
                    teleportBatchSize,
                    0,
                    Math.max(2, startingInfected + 1),
                    10,
                    0
            );
        }

        public int teleportPointCount() {
            return survivorSpawnCount;
        }

        public boolean ready() {
            return validationErrors().isEmpty();
        }

        public List<String> validationErrors() {
            EnumSet<SpawnRole> configuredRoles = EnumSet.noneOf(SpawnRole.class);
            if (survivorSpawnCount > 0) {
                configuredRoles.add(SpawnRole.SURVIVOR);
            }
            if (infectedReleaseSpawnCount > 0) {
                configuredRoles.add(SpawnRole.INFECTED_RELEASE);
            }
            if (infectedRespawnSpawnCount > 0) {
                configuredRoles.add(SpawnRole.INFECTED_RESPAWN);
            }
            return new RoundStartValidator().validate(new RoundStartValidator.Input(
                    infectedSpawnConfigured,
                    configuredRoles,
                    survivors + infected,
                    startingInfected,
                    teleportBatchSize,
                    teleportDelayTicks,
                    infectedTeleportDelay,
                    minimumPlayers,
                    startCountdownSeconds,
                    roundTimeLimitSeconds
            )).errors();
        }
    }
}
