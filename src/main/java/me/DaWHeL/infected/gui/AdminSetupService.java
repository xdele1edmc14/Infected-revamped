package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundStartValidator;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

public final class AdminSetupService {
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
        return spawnRepository.holdingSpawn().map(AdminSetupService::storedLocation);
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
        return snapshot(survivors, infected, config().getInt("settings.starting-zombies", 5));
    }

    public SetupSnapshot snapshot(int survivors, int infected, int startingInfected) {
        return new SetupSnapshot(
                spawnRepository.holdingSpawn().isPresent(),
                spawnRepository.points(SpawnRole.SURVIVOR).size(),
                spawnRepository.points(SpawnRole.INFECTED_RELEASE).size(),
                spawnRepository.points(SpawnRole.INFECTED_RESPAWN).size(),
                survivors,
                infected,
                startingInfected,
                config().getInt("settings.infected-teleport-delay", 10),
                config().getInt("settings.teleport-batch-size", 10),
                config().getInt("settings.teleport-delay", 5)
        );
    }

    public void setInfectedSpawn(Location location) {
        spawnRepository.saveHoldingSpawn(location);
    }

    public boolean clearInfectedSpawn() {
        return spawnRepository.deleteHoldingSpawn();
    }

    public void saveTeleportPoint(String name, Location location) {
        saveTeleportPoint(SpawnRole.SURVIVOR, name, location);
    }

    public void saveTeleportPoint(SpawnRole role, String name, Location location) {
        validatePointName(name);
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
        if (name == null || name.isBlank() || name.contains(".")) {
            throw new IllegalArgumentException("Teleport point names cannot be blank or contain periods.");
        }
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
            int teleportDelayTicks
    ) {
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
                    infectedTeleportDelay
            )).errors();
        }
    }
}
