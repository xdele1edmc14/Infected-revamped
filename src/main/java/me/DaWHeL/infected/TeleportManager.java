package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class TeleportManager {
    static final int MAX_PLAYERS_PER_SPAWN = 25;
    private static final List<SlotOffset> SLOT_OFFSETS = buildSlotOffsets();
    private final SpawnRepository spawnRepository;
    private final PluginTaskScheduler scheduler;

    public TeleportManager(InfectedPlugin plugin) {
        this(plugin, new SpawnRepository(plugin), new BukkitPluginTaskScheduler(plugin));
    }

    public TeleportManager(InfectedPlugin plugin, SpawnRepository spawnRepository) {
        this(plugin, spawnRepository, new BukkitPluginTaskScheduler(plugin));
    }

    TeleportManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            PluginTaskScheduler scheduler
    ) {
        Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public boolean addTeleportPoint(Player player, String name) {
        return addTeleportPoint(player, SpawnRole.SURVIVOR, name);
    }

    public boolean addTeleportPoint(Player player, SpawnRole role, String name) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(role, "role");
        if (!isValidPointName(name)) {
            player.sendMessage("Teleport point names cannot be blank or contain periods.");
            return false;
        }
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            player.sendMessage("Teleport point could not be saved because its world is unavailable.");
            return false;
        }

        spawnRepository.savePoint(role, name, location);
        player.sendMessage(role.displayName() + " spawn " + name + " added!");
        return true;
    }

    public boolean removeTeleportPoint(String name) {
        return removeTeleportPoint(SpawnRole.SURVIVOR, name);
    }

    public boolean removeTeleportPoint(SpawnRole role, String name) {
        Objects.requireNonNull(role, "role");
        return isValidPointName(name) && spawnRepository.deletePoint(role, name);
    }

    public List<Location> getTeleportPoints() {
        return spawnRepository.loadedLocations(SpawnRole.SURVIVOR);
    }

    public void teleportPlayersBatch(
            List<Player> players,
            int batchSize,
            int delayTicks,
            Runnable finishAction
    ) {
        teleportPlayersBatch(SpawnRole.SURVIVOR, players, batchSize, delayTicks, result -> {
            if (result.success() && finishAction != null) {
                finishAction.run();
            }
        });
    }

    public BukkitTask teleportPlayersBatch(
            SpawnRole role,
            List<Player> players,
            int batchSize,
            long delayTicks,
            Consumer<TeleportBatchResult> completion
    ) {
        return teleportPlayersBatch(role, players, batchSize, delayTicks, player -> true, completion);
    }

    public BukkitTask teleportPlayersBatch(
            SpawnRole role,
            List<Location> platforms,
            List<Player> players,
            int batchSize,
            long delayTicks,
            Predicate<Player> eligibility,
            Consumer<TeleportBatchResult> completion
    ) {
        return teleportPlayersBatch(
                role, platforms, players, batchSize, delayTicks, eligibility,
                player -> {
                },
                (player, teleported) -> {
                },
                completion
        );
    }

    public BukkitTask teleportPlayersBatch(
            SpawnRole role,
            List<Player> players,
            int batchSize,
            long delayTicks,
            Predicate<Player> eligibility,
            Consumer<TeleportBatchResult> completion
    ) {
        return teleportPlayersBatch(
                role,
                players,
                batchSize,
                delayTicks,
                eligibility,
                player -> {
                },
                (player, teleported) -> {
                },
                completion
        );
    }

    public BukkitTask teleportPlayersBatch(
            SpawnRole role,
            List<Player> players,
            int batchSize,
            long delayTicks,
            Predicate<Player> eligibility,
            Consumer<Player> beforeTeleport,
            BiConsumer<Player, Boolean> afterTeleport,
            Consumer<TeleportBatchResult> completion
    ) {
        return teleportPlayersBatch(
                role,
                spawnRepository.loadedLocations(role),
                players,
                batchSize,
                delayTicks,
                eligibility,
                beforeTeleport,
                afterTeleport,
                completion
        );
    }

    public BukkitTask teleportPlayersBatch(
            SpawnRole role,
            List<Location> platforms,
            List<Player> players,
            int batchSize,
            long delayTicks,
            Predicate<Player> eligibility,
            Consumer<Player> beforeTeleport,
            BiConsumer<Player, Boolean> afterTeleport,
            Consumer<TeleportBatchResult> completion
    ) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(platforms, "platforms");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(beforeTeleport, "beforeTeleport");
        Objects.requireNonNull(afterTeleport, "afterTeleport");
        Objects.requireNonNull(completion, "completion");
        if (batchSize < 1) {
            throw new IllegalArgumentException("Teleport batch size must be at least 1.");
        }

        if (platforms.isEmpty()) {
            completion.accept(new TeleportBatchResult(
                    0,
                    0,
                    List.of(),
                    "No " + role.displayName().toLowerCase(Locale.ROOT) + " spawns are available."
            ));
            return null;
        }

        List<Player> queue = List.copyOf(players);
        if (queue.isEmpty()) {
            completion.accept(new TeleportBatchResult(0, 0, List.of(), null));
            return null;
        }
        long capacity = (long) platforms.size() * MAX_PLAYERS_PER_SPAWN;
        if (queue.size() > capacity) {
            completion.accept(new TeleportBatchResult(
                    0,
                    0,
                    List.of(),
                    "The " + role.displayName().toLowerCase(Locale.ROOT) + " spawn capacity is "
                            + capacity + " players, but " + queue.size() + " need a destination."
            ));
            return null;
        }

        int[] index = {0};
        int[] attempted = {0};
        int[] succeeded = {0};
        List<UUID> failed = new ArrayList<>();
        BukkitTask[] taskHandle = new BukkitTask[1];

        Runnable operation = () -> {
            int processed = 0;
            while (processed < batchSize && index[0] < queue.size()) {
                int distributionIndex = index[0];
                Player player = queue.get(index[0]++);
                processed++;
                if (!player.isOnline() || !eligibility.test(player)) {
                    continue;
                }

                attempted[0]++;
                Location platform = platforms.get(distributionIndex % platforms.size());
                Location destination = slotDestination(platform, distributionIndex / platforms.size());
                beforeTeleport.accept(player);
                boolean teleported = false;
                try {
                    teleported = player.teleport(destination);
                } finally {
                    afterTeleport.accept(player, teleported);
                }
                if (teleported) {
                    succeeded[0]++;
                } else {
                    failed.add(player.getUniqueId());
                }
            }

            if (index[0] >= queue.size()) {
                if (taskHandle[0] != null) {
                    taskHandle[0].cancel();
                }
                completion.accept(new TeleportBatchResult(
                        attempted[0], succeeded[0], failed, null));
            }
        };

        BukkitTask task = scheduler.runRepeating(operation, 0L, Math.max(1L, delayTicks));
        taskHandle[0] = task;
        return task;
    }

    static Location slotDestination(Location center, int slotIndex) {
        Objects.requireNonNull(center, "center");
        if (slotIndex < 0 || slotIndex >= SLOT_OFFSETS.size()) {
            throw new IllegalArgumentException(
                    "Spawn slots must be between 0 and " + (MAX_PLAYERS_PER_SPAWN - 1) + ".");
        }
        SlotOffset offset = SLOT_OFFSETS.get(slotIndex);
        Location destination = center.clone();
        destination.add(offset.x, 0, offset.z);
        return destination;
    }

    private static List<SlotOffset> buildSlotOffsets() {
        List<SlotOffset> offsets = new ArrayList<>(MAX_PLAYERS_PER_SPAWN);
        offsets.add(new SlotOffset(0, 0));
        for (int radius = 1; radius <= 2; radius++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) == radius) {
                        offsets.add(new SlotOffset(x, z));
                    }
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static boolean isValidPointName(String name) {
        return name != null && !name.isBlank() && !name.contains(".");
    }

    private record SlotOffset(int x, int z) {
    }
}
