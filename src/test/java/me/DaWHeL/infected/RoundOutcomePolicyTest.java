package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static me.DaWHeL.infected.RosterChange.INFECTION;
import static me.DaWHeL.infected.RosterChange.INFECTED_DEPARTURE;
import static me.DaWHeL.infected.RosterChange.INFECTED_ELIMINATION;
import static me.DaWHeL.infected.RosterChange.SURVIVOR_DEPARTURE;
import static me.DaWHeL.infected.RoundConclusion.CANCELLED;
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
                () -> assertEquals(CANCELLED,
                        evaluate(RoundPhase.ACTIVE, 0, 2, SURVIVOR_DEPARTURE)),
                () -> assertEquals(SURVIVORS_WIN,
                        evaluate(RoundPhase.ACTIVE, 2, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(SURVIVORS_WIN,
                        evaluate(RoundPhase.ACTIVE, 2, 0, INFECTED_ELIMINATION)),
                () -> assertEquals(CANCELLED,
                        evaluate(RoundPhase.ACTIVE, 0, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.ACTIVE, 2, 1, SURVIVOR_DEPARTURE))
        );
    }

    @Test
    void cancelsPreActiveRoundsWhenEitherTeamDisappears() {
        assertAll(
                () -> assertEquals(CANCELLED,
                        evaluate(RoundPhase.COUNTDOWN, 0, 2, SURVIVOR_DEPARTURE)),
                () -> assertEquals(CANCELLED,
                        evaluate(RoundPhase.HEADSTART, 2, 0, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.HEADSTART, 2, 1, INFECTED_DEPARTURE)),
                () -> assertEquals(NONE,
                        evaluate(RoundPhase.LOBBY, 0, 0, SURVIVOR_DEPARTURE))
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
