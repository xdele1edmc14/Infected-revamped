package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartValidatorTest {
    private final RoundStartValidator validator = new RoundStartValidator();

    @Test
    void acceptsACompleteSetupWithAtLeastOneSurvivor() {
        RoundStartValidator.Result result = validator.validate(input(
                true, EnumSet.allOf(SpawnRole.class), 6, 2, 5, 40, 10));

        assertTrue(result.valid());
        assertEquals(List.of(), result.errors());
        assertEquals("", result.message());
    }

    @Test
    void reportsEveryInvalidSettingInStableOrder() {
        RoundStartValidator.Result result = validator.validate(input(
                false, EnumSet.of(SpawnRole.SURVIVOR), 2, 2, 0, -1, -1));

        assertFalse(result.valid());
        assertEquals(List.of(
                "Infected holding spawn is missing or its world is not loaded.",
                "Infected release spawns are missing or unavailable.",
                "Infected respawn spawns are missing or unavailable.",
                "Starting infected must be lower than the participant count.",
                "Teleport batch size must be at least 1.",
                "Teleport delay cannot be negative.",
                "Infected release delay cannot be negative."
        ), result.errors());
        assertEquals(String.join(" ", result.errors()), result.message());
    }

    @Test
    void rejectsTooFewPlayersAndNoStartingInfected() {
        RoundStartValidator.Result result = validator.validate(input(
                true, EnumSet.allOf(SpawnRole.class), 1, 0, 1, 0, 0));

        assertEquals(List.of(
                "At least 2 online lobby participants are required.",
                "Starting infected must be at least 1."
        ), result.errors());
    }

    @Test
    void namesEachMissingArenaSpawnRole() {
        RoundStartValidator.Result result = validator.validate(input(
                true, EnumSet.noneOf(SpawnRole.class), 4, 1, 1, 0, 0));

        assertEquals(List.of(
                "Survivor spawns are missing or unavailable.",
                "Infected release spawns are missing or unavailable.",
                "Infected respawn spawns are missing or unavailable."
        ), result.errors());
    }

    private static RoundStartValidator.Input input(
            boolean holdingSpawnLoaded,
            EnumSet<SpawnRole> loadedSpawnRoles,
            int participantCount,
            int startingInfected,
            int teleportBatchSize,
            int teleportDelayTicks,
            int infectedReleaseDelaySeconds
    ) {
        return new RoundStartValidator.Input(
                holdingSpawnLoaded,
                loadedSpawnRoles,
                participantCount,
                startingInfected,
                teleportBatchSize,
                teleportDelayTicks,
                infectedReleaseDelaySeconds
        );
    }
}
