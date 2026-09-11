package me.DaWHeL.infected;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class RoundSpawnPool {
    private final Optional<Location> holdingSpawn;
    private final Map<SpawnRole, List<Location>> locations;
    private final Set<Chunk> ticketedChunks = ConcurrentHashMap.newKeySet();

    private RoundSpawnPool(Optional<Location> holdingSpawn, Map<SpawnRole, List<Location>> locations) {
        this.holdingSpawn = holdingSpawn.map(Location::clone);
        EnumMap<SpawnRole, List<Location>> copied = new EnumMap<>(SpawnRole.class);
        for (SpawnRole role : SpawnRole.values()) {
            copied.put(role, locations.getOrDefault(role, List.of()).stream()
                    .map(Location::clone)
                    .toList());
        }
        this.locations = Map.copyOf(copied);
    }

    public static CompletableFuture<RoundSpawnPool> preload(
            InfectedPlugin plugin,
            SpawnRepository repository,
            Map<SpawnRole, Integer> teleportCounts
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(teleportCounts, "teleportCounts");

        RoundSpawnPool pool;
        Map<ChunkCoordinate, Location> requiredChunks = new LinkedHashMap<>();
        try {
            Optional<Location> holding = repository.loadedHoldingSpawn();
            EnumMap<SpawnRole, List<Location>> byRole = new EnumMap<>(SpawnRole.class);
            for (SpawnRole role : SpawnRole.values()) {
                byRole.put(role, repository.loadedLocations(role));
            }
            pool = new RoundSpawnPool(holding, byRole);

            holding.ifPresent(location -> requiredChunks.put(ChunkCoordinate.of(location), location));
            byRole.values().stream().flatMap(List::stream)
                    .forEach(location -> requiredChunks.putIfAbsent(ChunkCoordinate.of(location), location));
            for (Map.Entry<SpawnRole, Integer> teleportCount : teleportCounts.entrySet()) {
                List<Location> platforms = byRole.getOrDefault(teleportCount.getKey(), List.of());
                int count = Math.max(0, teleportCount.getValue());
                long capacity = (long) platforms.size() * TeleportManager.MAX_PLAYERS_PER_SPAWN;
                if (count > capacity) {
                    throw new IllegalStateException(teleportCount.getKey().displayName()
                            + " spawn capacity is " + capacity + " players, but " + count
                            + " need a destination.");
                }
                for (int index = 0; index < count && !platforms.isEmpty(); index++) {
                    Location platform = platforms.get(index % platforms.size());
                    Location destination = TeleportManager.slotDestination(
                            platform, index / platforms.size());
                    requiredChunks.putIfAbsent(ChunkCoordinate.of(destination), destination);
                }
            }
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (Map.Entry<ChunkCoordinate, Location> required : requiredChunks.entrySet()) {
            ChunkCoordinate coordinate = required.getKey();
            CompletableFuture<Chunk> load;
            try {
                load = coordinate.world.getChunkAtAsync(coordinate.x, coordinate.z, false);
            } catch (RuntimeException exception) {
                load = CompletableFuture.failedFuture(exception);
            }
            if (load == null) {
                load = CompletableFuture.failedFuture(
                        new IllegalStateException("Chunk preload returned no future."));
            }
            loads.add(load.thenAccept(chunk -> {
                if (chunk == null) {
                    throw new CompletionException(
                            new IllegalStateException("A required spawn chunk is not generated."));
                }
                if (chunk.addPluginChunkTicket(plugin)) {
                    pool.ticketedChunks.add(chunk);
                }
            }));
        }

        CompletableFuture<RoundSpawnPool> result = CompletableFuture
                .allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    validateCapacity(pool, teleportCounts);
                    return pool;
                });
        return result.whenComplete((loaded, error) -> {
            if (error != null) {
                pool.releaseTickets(plugin);
            }
        });
    }

    private static void validateCapacity(
            RoundSpawnPool pool,
            Map<SpawnRole, Integer> teleportCounts
    ) {
        for (SpawnRole role : SpawnRole.values()) {
            List<Location> platforms = pool.locations(role);
            int count = Math.max(0, teleportCounts.getOrDefault(role, 0));
            long capacity = (long) platforms.size() * TeleportManager.MAX_PLAYERS_PER_SPAWN;
            if (count > capacity) {
                throw new IllegalStateException(role.displayName() + " spawn capacity is " + capacity
                        + " players, but " + count + " need a destination.");
            }
        }
    }

    public Optional<Location> holdingSpawn() {
        return holdingSpawn;
    }

    public List<Location> locations(SpawnRole role) {
        return locations.getOrDefault(Objects.requireNonNull(role, "role"), List.of());
    }

    public void releaseTickets(InfectedPlugin plugin) {
        for (Chunk chunk : List.copyOf(ticketedChunks)) {
            try {
                chunk.removePluginChunkTicket(plugin);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not release a retained spawn chunk ticket.", exception);
            }
        }
        ticketedChunks.clear();
    }

    private record ChunkCoordinate(World world, int x, int z) {
        private static ChunkCoordinate of(Location location) {
            World world = Objects.requireNonNull(location.getWorld(), "Spawn location world");
            return new ChunkCoordinate(world, location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }
}
