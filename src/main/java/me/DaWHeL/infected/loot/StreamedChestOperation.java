package me.DaWHeL.infected.loot;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;

final class StreamedChestOperation {
    private final Plugin plugin;
    private final World world;
    private final ChestRegion region;
    private final WeaponLootCatalog catalog;
    private final ChestDiscoveryService discovery;
    private final ChestLootGenerator generator;
    private final WeaponChestService.ActionType action;
    private final BooleanSupplier operationStillAllowed;
    private final Iterator<ChestRegion.ChunkKey> chunks;
    private final Map<String, DiscoveredChest> chests = new LinkedHashMap<>();
    private final List<RetainedChunk> retainedChunks = new ArrayList<>();
    private final List<PendingChunk> pendingLoads = new ArrayList<>();
    private final List<Map<Integer, ItemStack>> plans = new ArrayList<>();
    private Stage stage = Stage.SCAN;
    private int chestIndex;
    private int retainedChunkIndex;
    private WeaponChestService.ActionResult result;

    StreamedChestOperation(Plugin plugin, World world, ChestRegion region,
                           WeaponLootCatalog catalog, ChestDiscoveryService discovery,
                           ChestLootGenerator generator, WeaponChestService.ActionType action,
                           BooleanSupplier operationStillAllowed) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.world = Objects.requireNonNull(world, "world");
        this.region = Objects.requireNonNull(region, "region");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.action = Objects.requireNonNull(action, "action");
        this.operationStillAllowed = Objects.requireNonNull(operationStillAllowed, "operationStillAllowed");
        this.chunks = region.chunkKeys(catalog.settings().maxChunks()).iterator();
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
                case RESCAN -> rescan(chunkBudget);
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
            Chunk chunk;
            try {
                chunk = load.future().join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                fail("Selected chunk " + load.key().x() + ", " + load.key().z()
                        + " could not be loaded: " + readableMessage(cause));
                return;
            }
            if (chunk == null) {
                fail("Selected chunk " + load.key().x() + ", " + load.key().z()
                        + " could not be loaded without generating terrain.");
                return;
            }
            boolean retained = false;
            try {
                List<DiscoveredChest> found = discovery.discover(region, chunk);
                found.forEach(chest -> chests.putIfAbsent(chest.key(), chest));
                if (!found.isEmpty()) {
                    boolean ticketAdded = chunk.addPluginChunkTicket(plugin);
                    retainedChunks.add(new RetainedChunk(chunk, load.loadedByOperation(), ticketAdded));
                    retained = true;
                } else if (load.loadedByOperation()) {
                    chunk.unload(false);
                }
            } catch (RuntimeException exception) {
                if (load.loadedByOperation() && !retained) chunk.unload(false);
                throw exception;
            }
            processed++;
        }
        if (chunks.hasNext() || !pendingLoads.isEmpty() || stage == Stage.DONE) return;
        if (chests.isEmpty()) {
            fail("No chests were found in the selected region.");
            return;
        }
        if (!operationStillAllowed.getAsBoolean()) {
            fail("Chest loot can only be changed while no round is running.");
            return;
        }
        chests.clear();
        retainedChunkIndex = 0;
        stage = Stage.RESCAN;
    }

    private void rescan(int budget) {
        for (int processed = 0; processed < budget && retainedChunkIndex < retainedChunks.size();
             processed++, retainedChunkIndex++) {
            discovery.discover(region, retainedChunks.get(retainedChunkIndex).chunk()).forEach(
                    chest -> chests.putIfAbsent(chest.key(), chest));
        }
        if (retainedChunkIndex < retainedChunks.size()) return;
        if (chests.isEmpty()) {
            fail("No chests were found in the selected region after loaded chunks were stabilized.");
            return;
        }
        stage = action == WeaponChestService.ActionType.FILL ? Stage.PLAN : Stage.APPLY;
        chestIndex = 0;
    }

    private void plan(int budget) {
        List<DiscoveredChest> discovered = new ArrayList<>(chests.values());
        for (int processed = 0; processed < budget && chestIndex < discovered.size(); processed++, chestIndex++) {
            ChestLootGenerator.PlanResult plan = generator.plan(catalog,
                    discovered.get(chestIndex).inventory().getSize());
            if (!plan.success()) {
                fail(String.join("; ", plan.errors()));
                return;
            }
            plans.add(plan.contents());
        }
        if (chestIndex < discovered.size() || stage == Stage.DONE) return;
        if (!operationStillAllowed.getAsBoolean()) {
            fail("Chest loot can only be changed while no round is running.");
            return;
        }
        stage = Stage.APPLY;
        chestIndex = 0;
    }

    private void apply(int budget) {
        List<DiscoveredChest> discovered = new ArrayList<>(chests.values());
        for (int processed = 0; processed < budget && chestIndex < discovered.size(); processed++, chestIndex++) {
            Inventory inventory = discovered.get(chestIndex).inventory();
            inventory.clear();
            if (action == WeaponChestService.ActionType.FILL) {
                plans.get(chestIndex).forEach((slot, item) -> inventory.setItem(slot, item.clone()));
            }
        }
        if (chestIndex < discovered.size()) return;
        complete(WeaponChestService.ActionResult.success(discovered.size()));
    }

    private void fail(String error) {
        complete(WeaponChestService.ActionResult.failure(error));
    }

    private void complete(WeaponChestService.ActionResult completed) {
        if (stage == Stage.DONE) return;
        stage = Stage.DONE;
        releasePendingLoads();
        Set<Chunk> released = new HashSet<>();
        List<String> cleanupErrors = new ArrayList<>();
        for (RetainedChunk retained : retainedChunks) {
            if (!released.add(retained.chunk())) continue;
            if (retained.ticketAdded()) {
                try {
                    retained.chunk().removePluginChunkTicket(plugin);
                } catch (RuntimeException exception) {
                    cleanupErrors.add("Could not release a chest chunk ticket: " + readableMessage(exception));
                }
            }
            if (retained.loadedByOperation()) {
                try {
                    retained.chunk().unload(false);
                } catch (RuntimeException exception) {
                    cleanupErrors.add("Could not unload a temporary chest chunk: " + readableMessage(exception));
                }
            }
        }
        retainedChunks.clear();
        if (cleanupErrors.isEmpty()) {
            result = completed;
        } else {
            List<String> errors = new ArrayList<>(completed.errors());
            errors.addAll(cleanupErrors);
            result = new WeaponChestService.ActionResult(false, completed.affectedChests(), errors);
        }
    }

    private void releasePendingLoads() {
        List<PendingChunk> pending = new ArrayList<>(pendingLoads);
        pendingLoads.clear();
        for (PendingChunk load : pending) {
            if (!load.loadedByOperation()) continue;
            load.future().thenAccept(chunk -> {
                if (chunk == null) return;
                try {
                    chunk.unload(false);
                } catch (RuntimeException ignored) {
                    // Best effort: a player or another system may retain the asynchronously loaded chunk.
                }
            });
        }
    }

    private static String readableMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private enum Stage { SCAN, RESCAN, PLAN, APPLY, DONE }
    private record PendingChunk(ChestRegion.ChunkKey key, CompletableFuture<Chunk> future,
                                boolean loadedByOperation) {}
    private record RetainedChunk(Chunk chunk, boolean loadedByOperation, boolean ticketAdded) {}
}
