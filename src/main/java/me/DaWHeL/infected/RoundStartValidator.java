package me.DaWHeL.infected;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoundStartValidator {

    public Result validate(Input input) {
        Objects.requireNonNull(input, "input");
        List<String> errors = new ArrayList<>();

        if (!input.holdingSpawnLoaded()) {
            errors.add("Infected holding spawn is missing or its world is not loaded.");
        }
        requireSpawn(input, SpawnRole.SURVIVOR, "Survivor spawns are missing or unavailable.", errors);
        requireSpawn(input, SpawnRole.INFECTED_RELEASE,
                "Infected release spawns are missing or unavailable.", errors);
        requireSpawn(input, SpawnRole.INFECTED_RESPAWN,
                "Infected respawn spawns are missing or unavailable.", errors);
        if (input.participantCount() < 2) {
            errors.add("At least 2 online lobby participants are required.");
        }
        if (input.startingInfected() < 1) {
            errors.add("Starting infected must be at least 1.");
        }
        if (input.startingInfected() >= input.participantCount()) {
            errors.add("Starting infected must be lower than the participant count.");
        }
        if (input.teleportBatchSize() < 1) {
            errors.add("Teleport batch size must be at least 1.");
        }
        if (input.teleportDelayTicks() < 0) {
            errors.add("Teleport delay cannot be negative.");
        }
        if (input.infectedReleaseDelaySeconds() < 0) {
            errors.add("Infected release delay cannot be negative.");
        }

        return new Result(errors);
    }

    private static void requireSpawn(Input input, SpawnRole role, String message, List<String> errors) {
        if (!input.loadedSpawnRoles().contains(role)) {
            errors.add(message);
        }
    }

    public record Input(
            boolean holdingSpawnLoaded,
            Set<SpawnRole> loadedSpawnRoles,
            int participantCount,
            int startingInfected,
            int teleportBatchSize,
            int teleportDelayTicks,
            int infectedReleaseDelaySeconds
    ) {
        public Input {
            loadedSpawnRoles = Set.copyOf(loadedSpawnRoles);
        }
    }

    public record Result(List<String> errors) {
        public Result {
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public String message() {
            return String.join(" ", errors);
        }
    }
}
