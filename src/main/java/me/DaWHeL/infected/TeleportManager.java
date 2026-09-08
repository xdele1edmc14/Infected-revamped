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
        Objects.requireNonNull(player, "player");
        if (!isValidPointName(name)) {
            player.sendMessage("Teleport point names cannot be blank or contain periods.");
            return false;
        }
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            player.sendMessage("Teleport point could not be saved because its world is unavailable.");
            return false;
        }

        spawnRepository.savePoint(SpawnRole.SURVIVOR, name, location);
        player.sendMessage("Survivor spawn " + name + " added!");
        return true;
    }

    public boolean removeTeleportPoint(String name) {
        return isValidPointName(name) && spawnRepository.deletePoint(SpawnRole.SURVIVOR, name);
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
        int half = 2;
        int row = slotIndex / 5;
        int column = slotIndex % 5;
        Location destination = center.clone();
        destination.setX(center.getX() - half + column + 0.5);
        destination.setZ(center.getZ() - half + row + 0.5);
        return destination;
    }

    private static boolean isValidPointName(String name) {
        return name != null && !name.isBlank() && !name.contains(".");
    }
}
