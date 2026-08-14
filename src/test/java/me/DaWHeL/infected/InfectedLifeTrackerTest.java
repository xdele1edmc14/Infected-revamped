package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfectedLifeTrackerTest {

    @Test
    void eliminatesAnInfectedPlayerOnlyAfterTheirConfiguredLifeCount() {
        UUID playerId = UUID.randomUUID();
        InfectedLifeTracker tracker = new InfectedLifeTracker();
        tracker.register(playerId, 3);

        assertTrue(tracker.consumeLife(playerId));
        assertTrue(tracker.consumeLife(playerId));
        assertFalse(tracker.consumeLife(playerId));
        assertTrue(tracker.isEliminated(playerId));
    }

    @Test
    void oneConfiguredLifeEliminatesTheInfectedOnTheirFirstDeath() {
        UUID playerId = UUID.randomUUID();
        InfectedLifeTracker tracker = new InfectedLifeTracker();
        tracker.register(playerId, 1);

        assertFalse(tracker.consumeLife(playerId));
        assertTrue(tracker.isEliminated(playerId));
    }

    @Test
    void clearRemovesLifeAndEliminationState() {
        UUID playerId = UUID.randomUUID();
        InfectedLifeTracker tracker = new InfectedLifeTracker();
        tracker.register(playerId, 1);
        tracker.consumeLife(playerId);

        tracker.clear();

        assertFalse(tracker.isEliminated(playerId));
    }
}
