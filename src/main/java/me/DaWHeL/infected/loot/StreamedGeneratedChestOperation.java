package me.DaWHeL.infected.loot;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

final class StreamedGeneratedChestOperation {
    private final Plugin plugin;
    private final GeneratedChestService.ActionType action;
    private final World generationWorld;
    private final ChestRegion region;
    private final GeneratedChestRepository repository;
    private final OutdoorChestSiteValidator validator;
    private final NamespacedKey markerKey;
    private final Function<String, BlockData> blockDataParser;
    private final Function<String, World> worldLookup;
    private final BooleanSupplier operationStillAllowed;
    private final int targetCount;
    private final List<GeneratedChestPlacement> oldPlacements;
    private final PoissonDiscPlacement sampler;
    private final Map<LoadedChunkKey, RetainedChunk> retainedChunks = new LinkedHashMap<>();
    private List<ChestSite> newSites = List.of();
    private final List<GeneratedChestPlacement> newPlacements = new ArrayList<>();
    private PoissonDiscPlacement.Candidate pendingCandidate;
    private PendingFootprint pendingFootprint;
    private int oldLoadIndex;
    private Stage stage;
    private int index;
    private boolean mutationStarted;
    private GeneratedChestService.ActionResult result;

    StreamedGeneratedChestOperation(GeneratedChestService.ActionType action, World generationWorld,
                                    ChestRegion region, int targetCount, GeneratedChestRepository repository,
                                    OutdoorChestSiteValidator validator, NamespacedKey markerKey,
                                    Function<String, BlockData> blockDataParser, long seed,
                                    BooleanSupplier operationStillAllowed) {
        this(null, action, generationWorld, region, targetCount, repository, validator, markerKey,
                blockDataParser, name -> generationWorld != null && name.equals(generationWorld.getName())
                        ? generationWorld : null, seed, operationStillAllowed);
    }

    StreamedGeneratedChestOperation(GeneratedChestService.ActionType action, World generationWorld,
                                    ChestRegion region, int targetCount, GeneratedChestRepository repository,
                                    OutdoorChestSiteValidator validator, NamespacedKey markerKey,
                                    Function<String, BlockData> blockDataParser,
                                    Function<String, World> worldLookup, long seed,
                                    BooleanSupplier operationStillAllowed) {
        this(null, action, generationWorld, region, targetCount, repository, validator, markerKey,
                blockDataParser, worldLookup, seed, operationStillAllowed);
    }

    StreamedGeneratedChestOperation(Plugin plugin, GeneratedChestService.ActionType action,
                                    World generationWorld, ChestRegion region, int targetCount,
                                    GeneratedChestRepository repository,
                                    OutdoorChestSiteValidator validator, NamespacedKey markerKey,
                                    Function<String, BlockData> blockDataParser,
                                    Function<String, World> worldLookup, long seed,
                                    BooleanSupplier operationStillAllowed) {
        this.plugin = plugin;
        this.action = Objects.requireNonNull(action, "action");
        this.generationWorld = generationWorld;
        this.region = region;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.markerKey = Objects.requireNonNull(markerKey, "markerKey");
        this.blockDataParser = Objects.requireNonNull(blockDataParser, "blockDataParser");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.operationStillAllowed = Objects.requireNonNull(operationStillAllowed, "operationStillAllowed");
        this.targetCount = targetCount;
        oldPlacements = repository.snapshot();
        if (action == GeneratedChestService.ActionType.GENERATE) {
            Objects.requireNonNull(generationWorld, "generationWorld");
            Objects.requireNonNull(region, "region");
            sampler = new PoissonDiscPlacement(region.minX() + 1, region.maxX() - 1,
                    region.minZ() + 1, region.maxZ() - 1, targetCount, seed);
            stage = Stage.PLAN;
        } else {
            sampler = null;
            stage = Stage.LOAD_OLD;
        }
    }

    boolean step(int candidateBudget, int mutationBudget) {
        if (candidateBudget < 1 || mutationBudget < 1) {
            throw new IllegalArgumentException("Operation budgets must be positive.");
        }
        if (stage == Stage.DONE) return true;
        if (!mutationStarted && !operationStillAllowed.getAsBoolean()) {
            fail("Generated chests can only be changed while no round is running.");
            return true;
        }
        try {
            switch (stage) {
                case PLAN -> plan(candidateBudget);
                case LOAD_OLD -> loadOld(candidateBudget);
                case PRECHECK_OLD -> precheckOld(mutationBudget);
                case VERIFY_NEW -> verifyNew(candidateBudget);
                case REMOVE_OLD -> removeOld(mutationBudget);
                case PREPARE_NEW -> prepareNew(mutationBudget);
                case PERSIST_PENDING -> persistPending();
                case PLACE_NEW -> placeNew(mutationBudget);
                case PERSIST_ACTIVE -> persistActive();
                case DONE -> { }
            }
        } catch (RuntimeException exception) {
            fail("Generated chest operation failed: " + readable(exception));
        }
        return stage == Stage.DONE;
    }

    GeneratedChestService.ActionResult result() {
        if (stage != Stage.DONE) throw new IllegalStateException("Generated chest operation is still running.");
        return result;
    }

    private void plan(int budget) {
        for (int processed = 0; processed < budget && !sampler.finished(); processed++) {
            if (pendingCandidate == null) {
                pendingCandidate = sampler.nextCandidate();
                pendingFootprint = beginFootprint(
                        generationWorld, pendingCandidate.x(), pendingCandidate.z());
            }
            if (!pendingFootprint.ready()) return;
            List<LoadedChunkKey> acquired = retainFootprint(pendingFootprint);
            java.util.Optional<ChestSite> validated = validator.validate(
                    generationWorld, region, pendingCandidate.x(), pendingCandidate.z());
            boolean accepted = sampler.consider(pendingCandidate, validated);
            if (!accepted) releaseRetained(acquired);
            pendingCandidate = null;
            pendingFootprint = null;
        }
        if (!sampler.finished()) return;
        if (!sampler.complete()) {
            fail("Could not find " + targetCount + " valid outdoor chest sites after "
                    + sampler.maximumAttempts() + " attempts. Lower the count or widen the selected region.");
            return;
        }
        newSites = sampler.sites();
        index = 0;
        stage = Stage.LOAD_OLD;
    }

    private void loadOld(int budget) {
        for (int processed = 0; processed < budget && oldLoadIndex < oldPlacements.size(); processed++) {
            GeneratedChestPlacement placement = oldPlacements.get(oldLoadIndex);
            World world = worldLookup.apply(placement.world());
            if (world == null) {
                fail("Generated chest world is not loaded: " + placement.world());
                return;
            }
            if (pendingFootprint == null) {
                pendingFootprint = beginFootprint(world, placement.x(), placement.z());
            }
            if (!pendingFootprint.ready()) return;
            retainFootprint(pendingFootprint);
            pendingFootprint = null;
            oldLoadIndex++;
        }
        if (oldLoadIndex < oldPlacements.size()) return;
        index = 0;
        stage = Stage.PRECHECK_OLD;
    }

    private void precheckOld(int budget) {
        for (int processed = 0; processed < budget && index < oldPlacements.size(); processed++, index++) {
            GeneratedChestPlacement placement = oldPlacements.get(index);
            World world = worldLookup.apply(placement.world());
            if (world == null || !footprintLoaded(world, placement.x(), placement.z())) {
                fail("A registered generated chest is not loaded at " + placement.x() + ", " + placement.z() + '.');
                return;
            }
            Block chestBlock = world.getBlockAt(placement.x(), placement.y(), placement.z());
            if (!canRestore(world, chestBlock, placement)) {
                fail("Generated chest ownership conflict at " + placement.x() + ", " + placement.y() + ", "
                        + placement.z() + "; the replacement block was left untouched.");
                return;
            }
        }
        if (index < oldPlacements.size()) return;
        index = 0;
        stage = action == GeneratedChestService.ActionType.GENERATE ? Stage.VERIFY_NEW : Stage.REMOVE_OLD;
    }

    private void verifyNew(int budget) {
        for (int processed = 0; processed < budget && index < newSites.size(); processed++, index++) {
            ChestSite expected = newSites.get(index);
            if (!validator.validate(generationWorld, region, expected.x(), expected.z()).equals(
                    java.util.Optional.of(expected))) {
                fail("A planned outdoor chest site changed before placement. No blocks were changed.");
                return;
            }
        }
        if (index < newSites.size()) return;
        index = 0;
        stage = Stage.REMOVE_OLD;
    }

    private void removeOld(int budget) {
        mutationStarted = true;
        for (int processed = 0; processed < budget && index < oldPlacements.size(); processed++, index++) {
            restore(oldPlacements.get(index));
        }
        if (index < oldPlacements.size()) return;
        index = 0;
        if (action == GeneratedChestService.ActionType.REMOVE) {
            repository.replaceAll(List.of());
            complete(oldPlacements.size());
        } else {
            stage = Stage.PREPARE_NEW;
        }
    }

    private void prepareNew(int budget) {
        for (int processed = 0; processed < budget && index < newSites.size(); processed++, index++) {
            ChestSite site = newSites.get(index);
            if (!siteStillValid(site)) {
                fail("A planned outdoor chest site changed before it could be journaled. "
                        + "The previous generated layout was restored safely.");
                return;
            }
            List<String> originals = captureGround(site);
            newPlacements.add(new GeneratedChestPlacement(UUID.randomUUID(), generationWorld.getName(),
                    site.x(), site.chestY(), site.z(), originals, GeneratedChestPlacement.State.PENDING));
        }
        if (index < newSites.size()) return;
        index = 0;
        stage = Stage.PERSIST_PENDING;
    }

    private void persistPending() {
        repository.replaceAll(newPlacements);
        stage = Stage.PLACE_NEW;
    }

    private void placeNew(int budget) {
        mutationStarted = true;
        for (int processed = 0; processed < budget && index < newPlacements.size(); processed++, index++) {
            GeneratedChestPlacement placement = newPlacements.get(index);
            ChestSite site = new ChestSite(placement.x(), placement.y() - 1, placement.z());
            if (!siteStillValid(site) || !groundMatches(placement)) {
                fail("A planned outdoor chest site changed immediately before placement. "
                        + "The pending registry was kept so Remove Generated can recover safely.");
                return;
            }
            place(placement);
        }
        if (index >= newPlacements.size()) stage = Stage.PERSIST_ACTIVE;
    }

    private void persistActive() {
        List<GeneratedChestPlacement> active = newPlacements.stream()
                .map(placement -> placement.withState(GeneratedChestPlacement.State.ACTIVE))
                .toList();
        repository.replaceAll(active);
        complete(active.size());
    }

    private void restore(GeneratedChestPlacement placement) {
        World world = Objects.requireNonNull(worldLookup.apply(placement.world()), "Generated chest world unloaded");
        if (!footprintLoaded(world, placement.x(), placement.z())) {
            throw new IllegalStateException("A registered generated chest is not loaded at "
                    + placement.x() + ", " + placement.z() + '.');
        }
        Block chestBlock = world.getBlockAt(placement.x(), placement.y(), placement.z());
        if (!canRestore(world, chestBlock, placement)) {
            throw new IllegalStateException("Generated chest ownership conflict at " + placement.x() + ", "
                    + placement.y() + ", " + placement.z() + "; the replacement block was left untouched.");
        }
        if (!isAir(chestBlock.getType())) chestBlock.setType(Material.AIR, false);
        int originalIndex = 0;
        int groundY = placement.y() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block ground = world.getBlockAt(placement.x() + dx, groundY, placement.z() + dz);
                String original = placement.originalGround().get(originalIndex++);
                if (ground.getType() == Material.GOLD_BLOCK) {
                    ground.setBlockData(blockDataParser.apply(original), false);
                }
            }
        }
    }

    private List<String> captureGround(ChestSite site) {
        List<String> originals = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                originals.add(generationWorld.getBlockAt(site.x() + dx, site.groundY(), site.z() + dz)
                        .getBlockData().getAsString(false));
            }
        }
        return List.copyOf(originals);
    }

    private boolean groundMatches(GeneratedChestPlacement placement) {
        int originalIndex = 0;
        int groundY = placement.y() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String current = generationWorld.getBlockAt(
                        placement.x() + dx, groundY, placement.z() + dz)
                        .getBlockData().getAsString(false);
                if (!placement.originalGround().get(originalIndex++).equals(current)) return false;
            }
        }
        return true;
    }

    private boolean siteStillValid(ChestSite site) {
        return validator.validate(generationWorld, region, site.x(), site.z())
                .equals(java.util.Optional.of(site));
    }

    private void place(GeneratedChestPlacement placement) {
        ChestSite site = new ChestSite(placement.x(), placement.y() - 1, placement.z());
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    generationWorld.getBlockAt(site.x() + dx, site.groundY(), site.z() + dz)
                            .setType(Material.GOLD_BLOCK, false);
                }
            }
            Block chestBlock = generationWorld.getBlockAt(site.x(), site.chestY(), site.z());
            chestBlock.setType(Material.CHEST, false);
            if (!(chestBlock.getState(false) instanceof Chest chest)) {
                throw new IllegalStateException("Placed chest did not expose a chest tile state.");
            }
            chest.getPersistentDataContainer().set(
                    markerKey, PersistentDataType.STRING, placement.id().toString());
            if (!chest.update(true, false)) throw new IllegalStateException("Could not persist generated chest marker.");
        } catch (RuntimeException exception) {
            generationWorld.getBlockAt(site.x(), site.chestY(), site.z()).setType(Material.AIR, false);
            int originalIndex = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    generationWorld.getBlockAt(site.x() + dx, site.groundY(), site.z() + dz)
                            .setBlockData(blockDataParser.apply(
                                    placement.originalGround().get(originalIndex++)), false);
                }
            }
            throw exception;
        }
    }

    private boolean hasMatchingOwnership(Block block, UUID id) {
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return false;
        if (!(block.getState(false) instanceof Chest chest)) return false;
        return id.toString().equals(chest.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING));
    }

    private boolean canRestore(World world, Block chestBlock, GeneratedChestPlacement placement) {
        if (isAir(chestBlock.getType()) || hasMatchingOwnership(chestBlock, placement.id())) return true;
        return placement.state() == GeneratedChestPlacement.State.PENDING
                && (chestBlock.getType() == Material.CHEST || chestBlock.getType() == Material.TRAPPED_CHEST)
                && hasExactGoldFoundation(world, placement);
    }

    private static boolean hasExactGoldFoundation(World world, GeneratedChestPlacement placement) {
        int groundY = placement.y() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (world.getBlockAt(placement.x() + dx, groundY, placement.z() + dz).getType()
                        != Material.GOLD_BLOCK) return false;
            }
        }
        return true;
    }

    private PendingFootprint beginFootprint(World world, int x, int z) {
        Set<LoadedChunkKey> keys = new LinkedHashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                keys.add(new LoadedChunkKey(world.getName(),
                        Math.floorDiv(x + dx, 16), Math.floorDiv(z + dz, 16)));
            }
        }
        List<LoadRequest> requests = new ArrayList<>();
        for (LoadedChunkKey key : keys) {
            if (retainedChunks.containsKey(key)) continue;
            boolean loadedByOperation = !world.isChunkLoaded(key.x(), key.z());
            if (loadedByOperation && !world.isChunkGenerated(key.x(), key.z())) {
                throw new IllegalStateException("Chunk " + key.x() + ", " + key.z() + " in "
                        + key.world() + " is not generated; terrain generation was refused.");
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
            releaseUnretainedLoads(loads);
            throw exception;
        }
        return new PendingFootprint(loads);
    }

    private List<LoadedChunkKey> retainFootprint(PendingFootprint footprint) {
        List<LoadedChunkKey> acquired = new ArrayList<>();
        for (PendingChunk load : footprint.loads()) {
            if (retainedChunks.containsKey(load.key())) continue;
            Chunk chunk;
            try {
                chunk = load.future().join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException("Chunk " + load.key().x() + ", " + load.key().z()
                        + " in " + load.key().world() + " could not be loaded: " + readable(cause), cause);
            }
            if (chunk == null) {
                throw new IllegalStateException("Chunk " + load.key().x() + ", " + load.key().z()
                        + " in " + load.key().world() + " could not be loaded without generating terrain.");
            }
            boolean ticketAdded = plugin != null && chunk.addPluginChunkTicket(plugin);
            retainedChunks.put(load.key(), new RetainedChunk(chunk, load.loadedByOperation(), ticketAdded));
            acquired.add(load.key());
        }
        return acquired;
    }

    private void releaseRetained(List<LoadedChunkKey> keys) {
        for (LoadedChunkKey key : keys) {
            RetainedChunk retained = retainedChunks.get(key);
            if (retained == null) continue;
            if (retained.ticketAdded()) retained.chunk().removePluginChunkTicket(plugin);
            if (retained.loadedByOperation()) retained.chunk().unload(false);
            retainedChunks.remove(key);
        }
    }

    private static boolean footprintLoaded(World world, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(Math.floorDiv(x + dx, 16), Math.floorDiv(z + dz, 16))) return false;
            }
        }
        return true;
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private void complete(int affected) {
        complete(GeneratedChestService.ActionResult.success(affected));
    }

    private void fail(String error) {
        complete(GeneratedChestService.ActionResult.failure(error));
    }

    private void complete(GeneratedChestService.ActionResult completed) {
        if (stage == Stage.DONE) return;
        stage = Stage.DONE;
        releasePendingLoads();
        List<String> cleanupErrors = new ArrayList<>();
        List<RetainedChunk> retained = new ArrayList<>(retainedChunks.values());
        retainedChunks.clear();
        for (RetainedChunk chunk : retained) {
            if (chunk.ticketAdded()) {
                try {
                    chunk.chunk().removePluginChunkTicket(plugin);
                } catch (RuntimeException exception) {
                    cleanupErrors.add("Could not release a generated chest chunk ticket: " + readable(exception));
                }
            }
            if (chunk.loadedByOperation()) {
                try {
                    chunk.chunk().unload(true);
                } catch (RuntimeException exception) {
                    cleanupErrors.add("Could not unload a temporary generated chest chunk: "
                            + readable(exception));
                }
            }
        }
        if (cleanupErrors.isEmpty()) {
            result = completed;
        } else {
            List<String> errors = new ArrayList<>(completed.errors());
            errors.addAll(cleanupErrors);
            result = new GeneratedChestService.ActionResult(false, completed.affectedChests(), errors);
        }
    }

    private void releasePendingLoads() {
        PendingFootprint pending = pendingFootprint;
        pendingFootprint = null;
        pendingCandidate = null;
        if (pending == null) return;
        releaseUnretainedLoads(pending.loads());
    }

    private void releaseUnretainedLoads(List<PendingChunk> loads) {
        for (PendingChunk load : loads) {
            if (!load.loadedByOperation() || retainedChunks.containsKey(load.key())) continue;
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

    private static String readable(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private enum Stage {
        PLAN, LOAD_OLD, PRECHECK_OLD, VERIFY_NEW, REMOVE_OLD, PREPARE_NEW,
        PERSIST_PENDING, PLACE_NEW, PERSIST_ACTIVE, DONE
    }
    private record LoadedChunkKey(String world, int x, int z) {}
    private record LoadRequest(LoadedChunkKey key, boolean loadedByOperation) {}
    private record PendingChunk(LoadedChunkKey key, CompletableFuture<Chunk> future,
                                boolean loadedByOperation) {}
    private record PendingFootprint(List<PendingChunk> loads) {
        private PendingFootprint { loads = List.copyOf(loads); }
        boolean ready() { return loads.stream().allMatch(load -> load.future().isDone()); }
    }
    private record RetainedChunk(Chunk chunk, boolean loadedByOperation, boolean ticketAdded) {}
}
