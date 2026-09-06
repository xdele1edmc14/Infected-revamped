package me.DaWHeL.infected.loot;

import java.util.Objects;

public record BlockPoint(String world, int x, int y, int z) {
    public BlockPoint {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("World cannot be blank.");
        }
    }
}
