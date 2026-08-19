package me.DaWHeL.infected;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class InfectedRespawnSelector {
    private static final Set<Material> HAZARDS = Collections.unmodifiableSet(EnumSet.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.CACTUS,
            Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW,
            Material.WITHER_ROSE
    ));
    private static final Set<Material> NON_GROUND = Collections.unmodifiableSet(EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.WATER,
            Material.BUBBLE_COLUMN
    ));

    private InfectedRespawnSelector() {
    }

    public static Optional<Location> select(List<Location> candidates, Random random) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(random, "random");
        List<Location> randomized = new ArrayList<>(candidates);
        Collections.shuffle(randomized, random);
        return randomized.stream()
                .filter(InfectedRespawnSelector::isSafe)
                .findFirst()
                .map(Location::clone);
    }

    public static boolean isSafe(Location location) {
        if (location == null) {
            return false;
        }
        try {
            return isSafeLoadedLocation(location);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSafeLoadedLocation(Location location) {
        World world = location.getWorld();
        if (world == null || !fitsInsideBorder(location)) {
            return false;
        }

        int feetY = location.getBlockY();
        if (feetY <= world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) {
            return false;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        Block ground = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        return isSafeGround(ground, location) && isClear(feet) && isClear(head);
    }

    private static boolean isSafeGround(Block block, Location location) {
        Material type = block.getType();
        if (block.isPassable() || NON_GROUND.contains(type) || HAZARDS.contains(type)) {
            return false;
        }

        BoundingBox support = block.getBoundingBox();
        double feetY = location.getY();
        double playerRadius = 0.3;
        double epsilon = 1.0e-6;
        return Math.abs(support.getMaxY() - feetY) <= epsilon
                && support.getMinX() <= location.getX() - playerRadius + epsilon
                && support.getMaxX() >= location.getX() + playerRadius - epsilon
                && support.getMinZ() <= location.getZ() - playerRadius + epsilon
                && support.getMaxZ() >= location.getZ() + playerRadius - epsilon;
    }

    private static boolean isClear(Block block) {
        return block.isPassable() && !HAZARDS.contains(block.getType());
    }

    private static boolean fitsInsideBorder(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)) {
            return false;
        }
        double radius = 0.31;
        return world.getWorldBorder().isInside(location.clone().add(radius, 0, radius))
                && world.getWorldBorder().isInside(location.clone().add(radius, 0, -radius))
                && world.getWorldBorder().isInside(location.clone().add(-radius, 0, radius))
                && world.getWorldBorder().isInside(location.clone().add(-radius, 0, -radius));
    }
}
