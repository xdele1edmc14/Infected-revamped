package me.DaWHeL.infected.loot;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class GeneratedChestService {
    private static final int CANDIDATES_PER_TICK = 64;
    private static final int MUTATIONS_PER_TICK = 8;

    private final GameManager gameManager;
    private final Supplier<WeaponLootCatalog> catalogSupplier;
    private final GeneratedChestRepository repository;
    private final OutdoorChestSiteValidator validator;
    private final NamespacedKey markerKey;
    private final ChestOperationGate operationGate;
    private final Function<String, World> worldLookup;
    private final LongSupplier seedSupplier;

    public GeneratedChestService(GameManager gameManager, Supplier<WeaponLootCatalog> catalogSupplier,
                                 GeneratedChestRepository repository, OutdoorChestSiteValidator validator,
                                 NamespacedKey markerKey, ChestOperationGate operationGate) {
        this(gameManager, catalogSupplier, repository, validator, markerKey, operationGate,
                Bukkit::getWorld, System::nanoTime);
    }

    GeneratedChestService(GameManager gameManager, Supplier<WeaponLootCatalog> catalogSupplier,
                          GeneratedChestRepository repository, OutdoorChestSiteValidator validator,
                          NamespacedKey markerKey, ChestOperationGate operationGate,
                          Function<String, World> worldLookup, LongSupplier seedSupplier) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.catalogSupplier = Objects.requireNonNull(catalogSupplier, "catalogSupplier");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.markerKey = Objects.requireNonNull(markerKey, "markerKey");
        this.operationGate = Objects.requireNonNull(operationGate, "operationGate");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.seedSupplier = Objects.requireNonNull(seedSupplier, "seedSupplier");
    }

    public ActionPreview preview(ActionType action) {
        Validation validation = validate(action);
        return new ActionPreview(validation.errors().isEmpty(), repository.snapshot().size(), validation.errors());
    }

    public boolean isOperationActive() {
        return operationGate.isActive();
    }

    public void executeAsync(Plugin plugin, ActionType action, Consumer<ActionResult> completion) {
        executeAsync(plugin, action, ignored -> { }, completion);
    }

    public void executeAsync(Plugin plugin, ActionType action,
                             Consumer<ChestOperationProgress> progress,
                             Consumer<ActionResult> completion) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(completion, "completion");
        ChestOperationGate.Lease lease = operationGate.tryAcquire();
        if (lease == null) {
            completion.accept(ActionResult.failure("A chest operation is already running."));
            return;
        }
        Validation validation = validate(action);
        if (!validation.errors().isEmpty()) {
            lease.close();
            completion.accept(new ActionResult(false, 0, validation.errors()));
            return;
        }
        StreamedGeneratedChestOperation operation;
        try {
            operation = new StreamedGeneratedChestOperation(
                    plugin, action, validation.world(), validation.region(), validation.targetCount(), repository,
                    validator, markerKey, Bukkit::createBlockData, worldLookup, seedSupplier.getAsLong(),
                    () -> gameManager.getPhase() == RoundPhase.LOBBY);
        } catch (RuntimeException exception) {
            lease.close();
            completion.accept(ActionResult.failure("Could not start generated chest operation: "
                    + readable(exception)));
            return;
        }
        BukkitTask[] scheduled = new BukkitTask[1];
        Runnable tick = () -> {
            boolean finished = operation.step(CANDIDATES_PER_TICK, MUTATIONS_PER_TICK);
            progress.accept(operation.progress());
            if (!finished) return;
            scheduled[0].cancel();
            lease.close();
            completion.accept(operation.result());
        };
        try {
            progress.accept(operation.progress());
            scheduled[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, tick, 1L, 1L);
        } catch (RuntimeException exception) {
            lease.close();
            completion.accept(ActionResult.failure("Could not schedule generated chest operation: "
                    + readable(exception)));
        }
    }

    private Validation validate(ActionType action) {
        List<String> errors = new ArrayList<>();
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            errors.add("Generated chests can only be changed while no round is running.");
            return new Validation(null, null, 0, errors);
        }
        errors.addAll(repository.errors());
        WeaponLootCatalog catalog = catalogSupplier.get();
        if (action == ActionType.REMOVE) {
            for (GeneratedChestPlacement placement : repository.snapshot()) {
                World world = worldLookup.apply(placement.world());
                if (world == null) {
                    errors.add("Generated chest world is not loaded: " + placement.world());
                }
            }
            return new Validation(null, null, 0, errors);
        }
        if (catalog.point1() == null || catalog.point2() == null) {
            errors.add("Select both region points first.");
            return new Validation(null, null, 0, errors);
        }
        ChestRegion region;
        try {
            region = ChestRegion.between(catalog.point1(), catalog.point2());
            region.chunkKeys(catalog.settings().maxChunks());
        } catch (RuntimeException exception) {
            errors.add(readable(exception));
            return new Validation(null, null, 0, errors);
        }
        if ((long) region.maxX() - region.minX() + 1L < 3L
                || (long) region.maxZ() - region.minZ() + 1L < 3L) {
            errors.add("The selected region must be at least 3x3 blocks horizontally.");
        }
        World world = worldLookup.apply(region.world());
        if (world == null) {
            errors.add("Selected world is not loaded: " + region.world());
            return new Validation(null, region, 0, errors);
        }
        for (GeneratedChestPlacement placement : repository.snapshot()) {
            World oldWorld = worldLookup.apply(placement.world());
            if (oldWorld == null) {
                errors.add("Previous generated chest world is not loaded: " + placement.world());
                break;
            }
        }
        return new Validation(world, region, catalog.settings().generatedChestCount(), errors);
    }

    private static String readable(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public enum ActionType { GENERATE, REMOVE }

    public record ActionPreview(boolean success, int registeredChests, List<String> errors) {
        public ActionPreview { errors = List.copyOf(errors); }
    }

    public record ActionResult(boolean success, int affectedChests, List<String> errors) {
        public ActionResult { errors = List.copyOf(errors); }
        static ActionResult success(int count) { return new ActionResult(true, count, List.of()); }
        static ActionResult failure(String error) { return new ActionResult(false, 0, List.of(error)); }
    }

    private record Validation(World world, ChestRegion region, int targetCount, List<String> errors) {
        private Validation { errors = List.copyOf(errors); }
    }
}
