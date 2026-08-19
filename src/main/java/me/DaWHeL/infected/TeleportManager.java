package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
    private final InfectedPlugin plugin;
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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void addTeleportPoint(Player player, String name) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            player.sendMessage("Teleport point could not be saved because its world is unavailable.");
            return;
        }

        int half = 2;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                Material blockType = (x == 0 && z == 0) ? Material.IRON_BLOCK : Material.GOLD_BLOCK;
                world.getBlockAt(location.getBlockX() + x, location.getBlockY(), location.getBlockZ() + z)
                        .setType(blockType);
            }
        }

        spawnRepository.savePoint(SpawnRole.SURVIVOR, name, location);
        player.sendMessage("Survivor spawn " + name + " added!");
    }

    public void removeTeleportPoint(String name) {
        SpawnRepository.NamedSpawn stored = spawnRepository.points(SpawnRole.SURVIVOR).stream()
                .filter(point -> point.name().equals(name))
                .findFirst()
                .orElse(null);
        if (stored == null) {
            return;
        }

        World world = plugin.getServer().getWorld(stored.location().world());
        if (world != null) {
            int centerX = (int) Math.floor(stored.location().x());
            int centerY = (int) Math.floor(stored.location().y());
            int centerZ = (int) Math.floor(stored.location().z());
            int half = 2;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    world.getBlockAt(centerX + dx, centerY, centerZ + dz).setType(Material.AIR);
                }
            }
        }
        spawnRepository.deletePoint(SpawnRole.SURVIVOR, name);
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
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(eligibility, "eligibility");
        Objects.requireNonNull(beforeTeleport, "beforeTeleport");
        Objects.requireNonNull(afterTeleport, "afterTeleport");
        Objects.requireNonNull(completion, "completion");
        if (batchSize < 1) {
            throw new IllegalArgumentException("Teleport batch size must be at least 1.");
        }

        List<Location> platforms = spawnRepository.loadedLocations(role);
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
                Location destination = getNextSlot(platform, distributionIndex / platforms.size());
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

    private Location getNextSlot(Location center, int slotIndex) {
        int half = 2;
        int row = slotIndex / 5;
        int column = slotIndex % 5;
        double x = center.getX() - half + column;
        double z = center.getZ() - half + row;
        return new Location(
                center.getWorld(),
                x + 0.5,
                center.getY(),
                z + 0.5,
                center.getYaw(),
                center.getPitch()
        );
    }
}
