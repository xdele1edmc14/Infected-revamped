package me.DaWHeL.infected.loot;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GeneratedChestPlacement(UUID id, String world, int x, int y, int z,
                                      List<String> originalGround, State state) {
    public GeneratedChestPlacement(UUID id, String world, int x, int y, int z,
                                   List<String> originalGround) {
        this(id, world, x, y, z, originalGround, State.ACTIVE);
    }

    public GeneratedChestPlacement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(state, "state");
        originalGround = List.copyOf(originalGround);
        if (world.isBlank()) throw new IllegalArgumentException("Generated chest world cannot be blank.");
        if (originalGround.size() != 9 || originalGround.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("A generated chest must preserve exactly nine ground blocks.");
        }
    }

    public GeneratedChestPlacement withState(State nextState) {
        return new GeneratedChestPlacement(id, world, x, y, z, originalGround, nextState);
    }

    public enum State { PENDING, ACTIVE }
}
