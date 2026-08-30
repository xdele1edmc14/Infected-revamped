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
                () -> assertTrue(RoundPhase.COUNTDOWN.canTransitionTo(RoundPhase.DEPLOYING)),
                () -> assertTrue(RoundPhase.COUNTDOWN.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.DEPLOYING.canTransitionTo(RoundPhase.HEADSTART)),
                () -> assertTrue(RoundPhase.DEPLOYING.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.HEADSTART.canTransitionTo(RoundPhase.ACTIVE)),
                () -> assertTrue(RoundPhase.HEADSTART.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.ACTIVE.canTransitionTo(RoundPhase.ENDING)),
                () -> assertTrue(RoundPhase.ENDING.canTransitionTo(RoundPhase.LOBBY)),
                () -> assertFalse(RoundPhase.LOBBY.canTransitionTo(RoundPhase.ACTIVE)),
                () -> assertFalse(RoundPhase.COUNTDOWN.canTransitionTo(RoundPhase.HEADSTART)),
                () -> assertFalse(RoundPhase.ACTIVE.canTransitionTo(RoundPhase.HEADSTART)),
                () -> assertFalse(RoundPhase.ENDING.canTransitionTo(RoundPhase.ACTIVE))
        );
    }

    @Test
    void reportsEveryNonLobbyPhaseAsRunningForLegacyCallers() {
        assertAll(
                () -> assertFalse(RoundPhase.LOBBY.isRunning()),
                () -> assertTrue(RoundPhase.COUNTDOWN.isRunning()),
                () -> assertTrue(RoundPhase.DEPLOYING.isRunning()),
                () -> assertTrue(RoundPhase.HEADSTART.isRunning()),
                () -> assertTrue(RoundPhase.ACTIVE.isRunning()),
                () -> assertTrue(RoundPhase.ENDING.isRunning())
        );
    }

    @Test
    void definesExactRoutingAndAdminCapabilitiesForEveryPhase() {
        assertAll(
                () -> assertFalse(RoundPhase.LOBBY.queuesLateJoins()),
                () -> assertTrue(RoundPhase.COUNTDOWN.queuesLateJoins()),
                () -> assertTrue(RoundPhase.DEPLOYING.queuesLateJoins()),
                () -> assertTrue(RoundPhase.HEADSTART.queuesLateJoins()),
                () -> assertTrue(RoundPhase.ACTIVE.queuesLateJoins()),
                () -> assertFalse(RoundPhase.ENDING.queuesLateJoins()),
                () -> assertFalse(RoundPhase.LOBBY.allowsParticipantRemoval()),
                () -> assertTrue(RoundPhase.COUNTDOWN.allowsParticipantRemoval()),
                () -> assertTrue(RoundPhase.DEPLOYING.allowsParticipantRemoval()),
                () -> assertTrue(RoundPhase.HEADSTART.allowsParticipantRemoval()),
                () -> assertTrue(RoundPhase.ACTIVE.allowsParticipantRemoval()),
                () -> assertFalse(RoundPhase.ENDING.allowsParticipantRemoval()),
                () -> assertFalse(RoundPhase.LOBBY.allowsZombieToggle()),
                () -> assertFalse(RoundPhase.COUNTDOWN.allowsZombieToggle()),
                () -> assertFalse(RoundPhase.DEPLOYING.allowsZombieToggle()),
                () -> assertFalse(RoundPhase.HEADSTART.allowsZombieToggle()),
                () -> assertTrue(RoundPhase.ACTIVE.allowsZombieToggle()),
                () -> assertFalse(RoundPhase.ENDING.allowsZombieToggle()),
                () -> assertTrue(RoundPhase.LOBBY.allowsConfigReload()),
                () -> assertFalse(RoundPhase.COUNTDOWN.allowsConfigReload()),
                () -> assertFalse(RoundPhase.DEPLOYING.allowsConfigReload()),
                () -> assertFalse(RoundPhase.HEADSTART.allowsConfigReload()),
                () -> assertFalse(RoundPhase.ACTIVE.allowsConfigReload()),
                () -> assertFalse(RoundPhase.ENDING.allowsConfigReload()),
                () -> assertFalse(RoundPhase.LOBBY.allowsAdminStop()),
                () -> assertTrue(RoundPhase.COUNTDOWN.allowsAdminStop()),
                () -> assertTrue(RoundPhase.DEPLOYING.allowsAdminStop()),
                () -> assertTrue(RoundPhase.HEADSTART.allowsAdminStop()),
                () -> assertTrue(RoundPhase.ACTIVE.allowsAdminStop()),
                () -> assertFalse(RoundPhase.ENDING.allowsAdminStop())
        );
    }
}
