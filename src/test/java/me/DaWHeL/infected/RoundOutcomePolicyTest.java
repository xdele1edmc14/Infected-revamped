package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static me.DaWHeL.infected.RosterChange.INFECTION;
import static me.DaWHeL.infected.RosterChange.INFECTED_DEPARTURE;
import static me.DaWHeL.infected.RosterChange.INFECTED_ELIMINATION;
import static me.DaWHeL.infected.RosterChange.SURVIVOR_DEPARTURE;
import static me.DaWHeL.infected.RoundConclusion.ABANDONED;
import static me.DaWHeL.infected.RoundConclusion.INFECTED_WIN;
import static me.DaWHeL.infected.RoundConclusion.NONE;
import static me.DaWHeL.infected.RoundConclusion.SURVIVORS_WIN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundOutcomePolicyTest {

    @Test
    void distinguishesEarnedWinsFromDeparturesAndAbandonment() {
        assertAll(
                () -> assertEquals(INFECTED_WIN,
                        evaluate(RoundPhase.ACTIVE, 0, 2, INFECTION)),
                () -> assertEquals(INFECTED_WIN,
                        evaluate(RoundPhase.ACTIVE, 0, 2, SURVIVOR_DEPARTURE)),
                () -> assertEquals(SURVIVORS_WIN,
                        evaluate(RoundPhase.ACTIVE, 2, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(SURVIVORS_WIN,
                        evaluate(RoundPhase.ACTIVE, 2, 0, INFECTED_ELIMINATION)),
                () -> assertEquals(ABANDONED,
                        evaluate(RoundPhase.ACTIVE, 0, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.ACTIVE, 2, 1, SURVIVOR_DEPARTURE))
        );
    }

    @Test
    void abandonsPreActiveRoundsWhenEitherTeamDisappears() {
        assertAll(
                () -> assertEquals(ABANDONED,
                        evaluate(RoundPhase.COUNTDOWN, 0, 2, SURVIVOR_DEPARTURE)),
                () -> assertEquals(ABANDONED,
                        evaluate(RoundPhase.DEPLOYING, 0, 2, SURVIVOR_DEPARTURE)),
                () -> assertEquals(ABANDONED,
                        evaluate(RoundPhase.HEADSTART, 2, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.HEADSTART, 2, 1, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.LOBBY, 0, 0, SURVIVOR_DEPARTURE))
        );
    }

    @Test
    void evaluatesACompleteRosterSnapshotWithoutInventingAChangeCause() {
        assertAll(
                () -> assertEquals(INFECTED_WIN,
                        RoundOutcomePolicy.evaluate(RoundPhase.ACTIVE, 0, 2)),
                () -> assertEquals(SURVIVORS_WIN,
                        RoundOutcomePolicy.evaluate(RoundPhase.ACTIVE, 2, 0)),
                () -> assertEquals(ABANDONED,
                        RoundOutcomePolicy.evaluate(RoundPhase.ACTIVE, 0, 0)),
                () -> assertEquals(NONE,
                        RoundOutcomePolicy.evaluate(RoundPhase.ACTIVE, 2, 1))
        );
    }

    private static RoundConclusion evaluate(
            RoundPhase phase,
            int survivors,
            int infected,
            RosterChange change
    ) {
        return RoundOutcomePolicy.evaluate(phase, survivors, infected, change);
    }
}
