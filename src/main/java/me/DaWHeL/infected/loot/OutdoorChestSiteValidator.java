package me.DaWHeL.infected.loot;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class OutdoorChestSiteValidator {
    private static final int SUPPORT_DEPTH = 4;
    private static final int CLEARANCE_HEIGHT = 2;
    private final Predicate<Block> solidSupport;

    public OutdoorChestSiteValidator() {
        this(block -> block.getType().isSolid() && !block.isLiquid());
    }

    OutdoorChestSiteValidator(Predicate<Block> solidSupport) {
        this.solidSupport = Objects.requireNonNull(solidSupport, "solidSupport");
    }

    public Optional<ChestSite> validate(World world, ChestRegion region, int x, int z) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(region, "region");
        if (!region.world().equals(world.getName())) return Optional.empty();
        if (x <= region.minX() || x >= region.maxX() || z <= region.minZ() || z >= region.maxZ()) {
            return Optional.empty();
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded(Math.floorDiv(x + dx, 16), Math.floorDiv(z + dz, 16))) {
                    return Optional.empty();
                }
            }
        }
        int groundY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
        if (groundY < region.minY() || groundY + CLEARANCE_HEIGHT > region.maxY()
                || groundY - SUPPORT_DEPTH + 1 < world.getMinHeight()) return Optional.empty();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int blockX = x + dx;
                int blockZ = z + dz;
                if (world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING) != groundY) {
                    return Optional.empty();
                }
                for (int depth = 0; depth < SUPPORT_DEPTH; depth++) {
                    Block support = world.getBlockAt(blockX, groundY - depth, blockZ);
                    if (!solidSupport.test(support)) return Optional.empty();
                    if (depth == 0 && support.getState(false) instanceof TileState) return Optional.empty();
                }
                for (int clearance = 1; clearance <= CLEARANCE_HEIGHT; clearance++) {
                    Material type = world.getBlockAt(blockX, groundY + clearance, blockZ).getType();
                    if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.of(new ChestSite(x, groundY, z));
    }
}
