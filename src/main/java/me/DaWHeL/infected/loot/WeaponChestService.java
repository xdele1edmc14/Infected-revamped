package me.DaWHeL.infected.loot;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WeaponChestService {
    private static final int CHUNKS_PER_TICK = 8;
    private static final int CHESTS_PER_TICK = 64;
    private final GameManager gameManager;
    private final Supplier<WeaponLootCatalog> catalogSupplier;
    private final ChestDiscoveryService discovery;
    private final ChestLootGenerator generator;
    private final ChestOperationGate operationGate;

    public WeaponChestService(GameManager gameManager, Supplier<WeaponLootCatalog> catalogSupplier,
                              ChestDiscoveryService discovery, ChestLootGenerator generator) {
        this(gameManager, catalogSupplier, discovery, generator, new ChestOperationGate());
    }

    public WeaponChestService(GameManager gameManager, Supplier<WeaponLootCatalog> catalogSupplier,
                              ChestDiscoveryService discovery, ChestLootGenerator generator,
                              ChestOperationGate operationGate) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.catalogSupplier = Objects.requireNonNull(catalogSupplier, "catalogSupplier");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.operationGate = Objects.requireNonNull(operationGate, "operationGate");
    }

    public ActionPreview preview(ActionType action) {
        Configuration validation = validateConfiguration(action);
        return new ActionPreview(validation.errors.isEmpty(), -1, validation.errors);
    }

    public ActionResult fill() {
        return execute(ActionType.FILL);
    }

    public ActionResult clear() {
        return execute(ActionType.CLEAR);
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
        Configuration validation = validateConfiguration(action);
        if (!validation.errors.isEmpty()) {
            lease.close();
            completion.accept(new ActionResult(false, 0, validation.errors));
            return;
        }
        World world = discovery.world(validation.region.world());
        if (world == null) {
            lease.close();
            completion.accept(ActionResult.failure("Selected world is not loaded: " + validation.region.world()));
            return;
        }
        StreamedChestOperation operation = new StreamedChestOperation(
                plugin, world, validation.region, validation.catalog, discovery, generator, action,
                () -> gameManager.getPhase() == RoundPhase.LOBBY);
        BukkitTask[] scheduled = new BukkitTask[1];
        Runnable tick = () -> {
            boolean finished = operation.step(CHUNKS_PER_TICK, CHESTS_PER_TICK);
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
            completion.accept(ActionResult.failure("Could not schedule chest operation: " + exception.getMessage()));
        }
    }

    private ActionResult execute(ActionType action) {
        ChestOperationGate.Lease lease = operationGate.tryAcquire();
        if (lease == null) return ActionResult.failure("A chest operation is already running.");
        try (lease) {
            Configuration configuration = validateConfiguration(action);
            if (!configuration.errors.isEmpty()) return new ActionResult(false, 0, configuration.errors);
            ChestDiscoveryService.DiscoveryResult discovered = discovery.discover(configuration.region);
            if (!discovered.success()) return new ActionResult(false, 0, discovered.errors());
            if (discovered.chests().isEmpty()) return ActionResult.failure("No chests were found in the selected region.");
            if (action == ActionType.CLEAR) {
                discovered.chests().forEach(chest -> chest.inventory().clear());
                return ActionResult.success(discovered.chests().size());
            }
            List<Map<Integer, ItemStack>> plans = new ArrayList<>();
            for (DiscoveredChest chest : discovered.chests()) {
                ChestLootGenerator.PlanResult plan = generator.plan(configuration.catalog, chest.inventory().getSize());
                if (!plan.success()) return new ActionResult(false, 0, plan.errors());
                plans.add(plan.contents());
            }
            for (int i = 0; i < discovered.chests().size(); i++) {
                var inventory = discovered.chests().get(i).inventory();
                inventory.clear();
                plans.get(i).forEach((slot, item) -> inventory.setItem(slot, item.clone()));
            }
            return ActionResult.success(discovered.chests().size());
        }
    }

    private Configuration validateConfiguration(ActionType action) {
        List<String> errors = new ArrayList<>();
        if (gameManager.getPhase() != RoundPhase.LOBBY) {
            errors.add("Chest loot can only be changed while no round is running.");
            return new Configuration(null, null, errors);
        }
        WeaponLootCatalog catalog = catalogSupplier.get();
        if (action == ActionType.FILL && !catalog.errors().isEmpty()) errors.addAll(catalog.errors());
        if (catalog.point1() == null || catalog.point2() == null) {
            errors.add("Select both region points first.");
            return new Configuration(catalog, null, errors);
        }
        ChestRegion region;
        try {
            region = ChestRegion.between(catalog.point1(), catalog.point2());
            region.chunkKeys(catalog.settings().maxChunks());
        } catch (RuntimeException exception) {
            errors.add(exception.getMessage());
            return new Configuration(catalog, null, errors);
        }
        if (action == ActionType.FILL) {
            if (catalog.guns().isEmpty()) errors.add("Add at least one gun first.");
            if (catalog.guns().stream().anyMatch(gun -> gun.ammo() == null)) {
                errors.add("Every gun must have corresponding ammo.");
            }
        }
        return new Configuration(catalog, region, errors);
    }

    public enum ActionType { FILL, CLEAR }
    public record ActionPreview(boolean success, int chestCount, List<String> errors) {
        public ActionPreview { errors = List.copyOf(errors); }
    }
    public record ActionResult(boolean success, int affectedChests, List<String> errors) {
        public ActionResult { errors = List.copyOf(errors); }
        static ActionResult success(int count) { return new ActionResult(true, count, List.of()); }
        static ActionResult failure(String error) { return new ActionResult(false, 0, List.of(error)); }
    }
    private record Configuration(WeaponLootCatalog catalog, ChestRegion region, List<String> errors) {}
}
