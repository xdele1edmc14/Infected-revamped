package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundPhaseTest {

    @Test
    void permitsOnlyTheDefinedRoundTransitions() {
        assertAll(
                () -> assertTrue(RoundPhase.LOBBY.canTransitionTo(RoundPhase.COUNTDOWN)),
                () -> assertTrue(RoundPhase.COUNTDOWN.canTransitionTo(RoundPhase.HEADSTART)),
                () -> assertTrue(RoundPhase.COUNTDOWN.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.HEADSTART.canTransitionTo(RoundPhase.ACTIVE)),
                () -> assertTrue(RoundPhase.HEADSTART.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.ACTIVE.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.ENDING.canTransitionTo(RoundPhase.LOBBY)),
                () -> assertFalse(RoundPhase.LOBBY.canTransitionTo(RoundPhase.ACTIVE)),
                () -> assertFalse(RoundPhase.ACTIVE.canTransitionTo(RoundPhase.HEADSTART)),
                () -> assertFalse(RoundPhase.ENDING.canTransitionTo(RoundPhase.ACTIVE))
        );
    }

    @Test
    void reportsEveryNonLobbyPhaseAsRunningForLegacyCallers() {
        assertAll(
                () -> assertFalse(RoundPhase.LOBBY.isRunning()),
                () -> assertTrue(RoundPhase.COUNTDOWN.isRunning()),
                () -> assertTrue(RoundPhase.HEADSTART.isRunning()),
                () -> assertTrue(RoundPhase.ACTIVE.isRunning()),
                () -> assertTrue(RoundPhase.ENDING.isRunning())
        );
    }
}
