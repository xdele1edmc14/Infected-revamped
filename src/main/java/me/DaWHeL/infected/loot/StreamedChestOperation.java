package me.DaWHeL.infected.loot;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

final class StreamedChestOperation {
    private final World world;
    private final ChestRegion region;
    private final WeaponLootCatalog catalog;
    private final ChestDiscoveryService discovery;
    private final ChestLootGenerator generator;
    private final WeaponChestService.ActionType action;
    private final BooleanSupplier operationStillAllowed;
    private final Iterator<ChestRegion.ChunkKey> chunks;
    private final Set<ChestRegion.ChunkKey> candidateChunks = new LinkedHashSet<>();
    private final Map<String, ChestTarget> targets = new LinkedHashMap<>();
    private final List<PendingChunk> pendingLoads = new ArrayList<>();
    private final List<Map<Integer, ItemStack>> plans = new ArrayList<>();
    private final List<AppliedMutation> appliedMutations = new ArrayList<>();
    private Stage stage = Stage.SCAN;
    private Iterator<ChestRegion.ChunkKey> stabilizationChunks;
    private List<ChestTarget> orderedTargets = List.of();
    private PendingWindow pendingWindow;
    private ChestRegion.ChunkKey currentStabilizationChunk;
    private ChestTarget currentApplyTarget;
    private int chestIndex;
    private int scannedChunks;
    private int stabilizedChunks;
    private final int totalChunks;
    private WeaponChestService.ActionResult result;

    StreamedChestOperation(Plugin plugin, World world, ChestRegion region,
                           WeaponLootCatalog catalog, ChestDiscoveryService discovery,
                           ChestLootGenerator generator, WeaponChestService.ActionType action,
                           BooleanSupplier operationStillAllowed) {
        Objects.requireNonNull(plugin, "plugin");
        this.world = Objects.requireNonNull(world, "world");
        this.region = Objects.requireNonNull(region, "region");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.action = Objects.requireNonNull(action, "action");
        this.operationStillAllowed = Objects.requireNonNull(operationStillAllowed, "operationStillAllowed");
        this.chunks = region.chunkKeys(catalog.settings().maxChunks()).iterator();
        this.totalChunks = Math.toIntExact(region.chunkCount());
    }

    boolean step(int chunkBudget, int chestBudget) {
        if (chunkBudget < 1 || chestBudget < 1) {
            throw new IllegalArgumentException("Operation budgets must be positive.");
        }
        if (stage == Stage.DONE) return true;
        if (stage != Stage.APPLY && !operationStillAllowed.getAsBoolean()) {
            fail("Chest loot can only be changed while no round is running.");
            return true;
        }
        try {
            switch (stage) {
                case SCAN -> scan(chunkBudget);
                case STABILIZE -> stabilize(chunkBudget);
                case PLAN -> plan(chestBudget);
                case APPLY -> apply(chestBudget);
                case DONE -> { }
            }
        } catch (RuntimeException exception) {
            fail("Chest operation failed: " + readableMessage(exception));
        }
        return stage == Stage.DONE;
    }

    WeaponChestService.ActionResult result() {
        if (stage != Stage.DONE) throw new IllegalStateException("Chest operation is still running.");
        return result;
    }

    void cancel(String reason) {
        if (stage != Stage.DONE) {
            fail(reason == null || reason.isBlank() ? "Chest operation was cancelled." : reason);
        }
    }

    ChestOperationProgress progress() {
        return switch (stage) {
            case SCAN -> new ChestOperationProgress("Scanning chunks", scannedChunks, totalChunks);
            case STABILIZE -> new ChestOperationProgress(
                    "Stabilizing chests", stabilizedChunks, candidateChunks.size());
            case PLAN -> new ChestOperationProgress("Planning loot", chestIndex, orderedTargets.size());
            case APPLY -> new ChestOperationProgress(
                    action == WeaponChestService.ActionType.FILL ? "Filling chests" : "Emptying chests",
                    chestIndex, orderedTargets.size());
            case DONE -> new ChestOperationProgress("Chest operation complete", 1, 1);
        };
    }

    private void scan(int budget) {
        int requested = 0;
        while (requested < budget && pendingLoads.size() < budget && chunks.hasNext()) {
            ChestRegion.ChunkKey key = chunks.next();
            boolean loadedByOperation = !world.isChunkLoaded(key.x(), key.z());
            if (loadedByOperation && !world.isChunkGenerated(key.x(), key.z())) {
                fail("Selected chunk " + key.x() + ", " + key.z()
                        + " is not generated; terrain generation was refused.");
                return;
            }
            CompletableFuture<Chunk> future = loadedByOperation
                    ? world.getChunkAtAsync(key.x(), key.z(), false)
                    : CompletableFuture.completedFuture(world.getChunkAt(key.x(), key.z()));
            pendingLoads.add(new PendingChunk(key, future, loadedByOperation));
            requested++;
        }

        int processed = 0;
        Iterator<PendingChunk> pending = pendingLoads.iterator();
        while (pending.hasNext() && processed < budget) {
            PendingChunk load = pending.next();
            if (!load.future().isDone()) continue;
            pending.remove();
            Chunk chunk = loadedChunk(load);
            try {
                if (!discovery.discover(region, chunk).isEmpty()) {
                    candidateChunks.add(load.key());
                }
            } finally {
                if (load.loadedByOperation()) chunk.unload(false);
            }
            processed++;
            scannedChunks++;
        }
        if (chunks.hasNext() || !pendingLoads.isEmpty() || stage == Stage.DONE) return;
        if (candidateChunks.isEmpty()) {
            fail("No chests were found in the selected region.");
            return;
        }
        stabilizationChunks = candidateChunks.iterator();
        stage = Stage.STABILIZE;
    }

    private void stabilize(int budget) {
        int processed = 0;
        while (processed < budget) {
            if (pendingWindow == null) {
                if (!stabilizationChunks.hasNext()) break;
                currentStabilizationChunk = stabilizationChunks.next();
                pendingWindow = beginWindow(stabilizationWindow(currentStabilizationChunk));
            }
            if (!pendingWindow.ready()) return;
            PendingWindow completedWindow = pendingWindow;
            try {
                for (LoadedChunk loaded : loadedChunks(completedWindow)) {
                    for (DiscoveredChest chest : discovery.discover(region, loaded.chunk())) {
                        Set<ChestRegion.ChunkKey> required = chest.chunks().isEmpty()
                                ? Set.of(loaded.key()) : chest.chunks();
                        ChestTarget target = new ChestTarget(chest.key(), chest.inventory().getSize(), required);
                        ChestTarget existing = targets.putIfAbsent(chest.key(), target);
                        if (existing != null && existing.inventorySize() != target.inventorySize()) {
                            throw new IllegalStateException("Chest layout changed while it was being stabilized.");
                        }
                    }
                }
            } finally {
                releaseWindow(completedWindow, false);
                pendingWindow = null;
                currentStabilizationChunk = null;
            }
            stabilizedChunks++;
            processed++;
        }
        if (pendingWindow != null || stabilizationChunks.hasNext()) return;
        if (targets.isEmpty()) {
            fail("No chests were found in the selected region after loaded chunks were stabilized.");
            return;
        }
        orderedTargets = targets.values().stream()
                .sorted(Comparator.comparing(ChestTarget::key))
                .toList();
        chestIndex = 0;
        stage = action == WeaponChestService.ActionType.FILL ? Stage.PLAN : Stage.APPLY;
    }

    private Set<ChestRegion.ChunkKey> stabilizationWindow(ChestRegion.ChunkKey center) {
        Set<ChestRegion.ChunkKey> window = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChestRegion.ChunkKey candidate = new ChestRegion.ChunkKey(center.x() + dx, center.z() + dz);
                if (candidateChunks.contains(candidate)) window.add(candidate);
            }
        }
        return window;
    }

    private void plan(int budget) {
        for (int processed = 0; processed < budget && chestIndex < orderedTargets.size();
             processed++, chestIndex++) {
            ChestLootGenerator.PlanResult plan = generator.plan(catalog,
                    orderedTargets.get(chestIndex).inventorySize());
            if (!plan.success()) {
                fail(String.join("; ", plan.errors()));
                return;
            }
            plans.add(plan.contents());
        }
        if (chestIndex < orderedTargets.size() || stage == Stage.DONE) return;
        if (!operationStillAllowed.getAsBoolean()) {
            fail("Chest loot can only be changed while no round is running.");
            return;
        }
        stage = Stage.APPLY;
        chestIndex = 0;
    }

    private void apply(int budget) {
        int processed = 0;
        while (processed < budget && chestIndex < orderedTargets.size()) {
            if (pendingWindow == null) {
                currentApplyTarget = orderedTargets.get(chestIndex);
                pendingWindow = beginWindow(currentApplyTarget.chunks());
            }
            if (!pendingWindow.ready()) return;
            PendingWindow completedWindow = pendingWindow;
            boolean windowMutated = false;
            try {
                Map<String, DiscoveredChest> current = new LinkedHashMap<>();
                for (LoadedChunk loaded : loadedChunks(completedWindow)) {
                    discovery.discover(region, loaded.chunk()).forEach(
                            chest -> current.putIfAbsent(chest.key(), chest));
                }
                DiscoveredChest chest = current.get(currentApplyTarget.key());
                if (chest == null || chest.inventory().getSize() != currentApplyTarget.inventorySize()) {
                    throw new IllegalStateException("Chest " + currentApplyTarget.key()
                            + " changed or is no longer eligible before mutation.");
                }
                Inventory inventory = chest.inventory();
                appliedMutations.add(new AppliedMutation(
                        currentApplyTarget, copyContents(inventory.getContents())));
                windowMutated = true;
                inventory.clear();
                if (action == WeaponChestService.ActionType.FILL) {
                    plans.get(chestIndex).forEach((slot, item) -> inventory.setItem(slot, item.clone()));
                }
            } finally {
                releaseWindow(completedWindow, windowMutated);
                pendingWindow = null;
                currentApplyTarget = null;
            }
            chestIndex++;
            processed++;
        }
        if (chestIndex >= orderedTargets.size()) {
            complete(WeaponChestService.ActionResult.success(orderedTargets.size()));
        }
    }

    private PendingWindow beginWindow(Set<ChestRegion.ChunkKey> keys) {
        List<LoadRequest> requests = new ArrayList<>();
        for (ChestRegion.ChunkKey key : keys) {
            boolean loadedByOperation = !world.isChunkLoaded(key.x(), key.z());
            if (loadedByOperation && !world.isChunkGenerated(key.x(), key.z())) {
                throw new IllegalStateException("Selected chunk " + key.x() + ", " + key.z()
                        + " is not generated; terrain generation was refused.");
            }
            requests.add(new LoadRequest(key, loadedByOperation));
        }
        List<PendingChunk> loads = new ArrayList<>();
        try {
            for (LoadRequest request : requests) {
                CompletableFuture<Chunk> future = request.loadedByOperation()
                        ? world.getChunkAtAsync(request.key().x(), request.key().z(), false)
                        : CompletableFuture.completedFuture(
                                world.getChunkAt(request.key().x(), request.key().z()));
                loads.add(new PendingChunk(request.key(), future, request.loadedByOperation()));
            }
        } catch (RuntimeException exception) {
            releaseLoads(loads, false);
            throw exception;
        }
        return new PendingWindow(loads);
    }

    private List<LoadedChunk> loadedChunks(PendingWindow window) {
        List<LoadedChunk> loaded = new ArrayList<>(window.loads().size());
        for (PendingChunk load : window.loads()) {
            loaded.add(new LoadedChunk(load.key(), loadedChunk(load)));
        }
        return loaded;
    }

    private Chunk loadedChunk(PendingChunk load) {
        try {
            Chunk chunk = load.future().join();
            if (chunk == null) {
                throw new IllegalStateException("Selected chunk " + load.key().x() + ", " + load.key().z()
                        + " could not be loaded without generating terrain.");
            }
            return chunk;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Selected chunk " + load.key().x() + ", " + load.key().z()
                    + " could not be loaded: " + readableMessage(cause), cause);
        }
    }

    private void releaseWindow(PendingWindow window, boolean save) {
        releaseLoads(window.loads(), save);
    }

    private void releaseLoads(List<PendingChunk> loads, boolean save) {
        Set<Chunk> released = new HashSet<>();
        for (PendingChunk load : loads) {
            if (!load.loadedByOperation()) continue;
            Chunk chunk;
            try {
                chunk = load.future().getNow(null);
            } catch (CompletionException exception) {
                continue;
            }
            if (chunk != null && released.add(chunk)) chunk.unload(save);
        }
    }

    private void fail(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        errors.addAll(rollbackAppliedMutations());
        complete(new WeaponChestService.ActionResult(false, 0, errors));
    }

    private List<String> rollbackAppliedMutations() {
        List<String> errors = new ArrayList<>();
        for (int index = appliedMutations.size() - 1; index >= 0; index--) {
            AppliedMutation mutation = appliedMutations.get(index);
            List<SyncLoadedChunk> loaded = new ArrayList<>();
            try {
                for (ChestRegion.ChunkKey key : mutation.target().chunks()) {
                    boolean loadedByOperation = !world.isChunkLoaded(key.x(), key.z());
                    Chunk chunk = world.getChunkAt(key.x(), key.z());
                    if (chunk == null) throw new IllegalStateException("Chunk could not be loaded for rollback.");
                    loaded.add(new SyncLoadedChunk(chunk, loadedByOperation));
                }
                Map<String, DiscoveredChest> current = new LinkedHashMap<>();
                for (SyncLoadedChunk loadedChunk : loaded) {
                    discovery.discover(region, loadedChunk.chunk()).forEach(
                            chest -> current.putIfAbsent(chest.key(), chest));
                }
                DiscoveredChest chest = current.get(mutation.target().key());
                if (chest == null) {
                    throw new IllegalStateException("Chest " + mutation.target().key()
                            + " could not be found for rollback.");
                }
                chest.inventory().setContents(copyContents(mutation.contents()));
            } catch (RuntimeException exception) {
                errors.add("Could not roll back changed chest " + mutation.target().key() + ": "
                        + readableMessage(exception));
            } finally {
                Set<Chunk> released = new HashSet<>();
                for (SyncLoadedChunk loadedChunk : loaded) {
                    if (loadedChunk.loadedByOperation() && released.add(loadedChunk.chunk())) {
                        try {
                            loadedChunk.chunk().unload(true);
                        } catch (RuntimeException exception) {
                            errors.add("Could not unload a rollback chunk: " + readableMessage(exception));
                        }
                    }
                }
            }
        }
        appliedMutations.clear();
        return errors;
    }

    private void complete(WeaponChestService.ActionResult completed) {
        if (stage == Stage.DONE) return;
        stage = Stage.DONE;
        releasePendingLoads();
        appliedMutations.clear();
        result = completed;
    }

    private void releasePendingLoads() {
        List<PendingChunk> scanning = new ArrayList<>(pendingLoads);
        pendingLoads.clear();
        releaseLoadsWhenReady(scanning);
        PendingWindow window = pendingWindow;
        pendingWindow = null;
        currentApplyTarget = null;
        currentStabilizationChunk = null;
        if (window != null) releaseLoadsWhenReady(window.loads());
    }

    private void releaseLoadsWhenReady(List<PendingChunk> loads) {
        for (PendingChunk load : loads) {
            if (!load.loadedByOperation()) continue;
            load.future().thenAccept(chunk -> {
                if (chunk == null) return;
                try {
                    chunk.unload(false);
                } catch (RuntimeException ignored) {
                    // Best effort: another system may retain an asynchronously loaded chunk.
                }
            });
        }
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        if (contents == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }

    private static String readableMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private enum Stage { SCAN, STABILIZE, PLAN, APPLY, DONE }

    private record LoadRequest(ChestRegion.ChunkKey key, boolean loadedByOperation) {}
    private record PendingChunk(ChestRegion.ChunkKey key, CompletableFuture<Chunk> future,
                                boolean loadedByOperation) {}
    private record PendingWindow(List<PendingChunk> loads) {
        private PendingWindow { loads = List.copyOf(loads); }
        boolean ready() { return loads.stream().allMatch(load -> load.future().isDone()); }
    }
    private record LoadedChunk(ChestRegion.ChunkKey key, Chunk chunk) {}
    private record SyncLoadedChunk(Chunk chunk, boolean loadedByOperation) {}
    private record ChestTarget(String key, int inventorySize, Set<ChestRegion.ChunkKey> chunks) {
        private ChestTarget { chunks = Set.copyOf(chunks); }
    }
    private record AppliedMutation(ChestTarget target, ItemStack[] contents) {}
}
