package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundStatsTrackerTest {

    @Test
    void recordsKillsAndInfectionsIndependentlyPerPlayer() {
        RoundStatsTracker tracker = new RoundStatsTracker();
        UUID survivor = UUID.randomUUID();
        UUID infected = UUID.randomUUID();

        tracker.recordKill(survivor);
        tracker.recordKill(survivor);
        tracker.recordInfection(infected);

        assertEquals(2, tracker.kills(survivor));
        assertEquals(0, tracker.infections(survivor));
        assertEquals(0, tracker.kills(infected));
        assertEquals(1, tracker.infections(infected));
    }

    @Test
    void clearingDropsEveryRoundCounter() {
        RoundStatsTracker tracker = new RoundStatsTracker();
        UUID player = UUID.randomUUID();
        tracker.recordKill(player);
        tracker.recordInfection(player);

        tracker.clear();

        assertEquals(0, tracker.kills(player));
        assertEquals(0, tracker.infections(player));
    }
}
