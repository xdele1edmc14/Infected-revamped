package me.DaWHeL.infected;

import java.util.Objects;

public final class RoundOutcomePolicy {
    private RoundOutcomePolicy() {
    }

    public static RoundConclusion evaluate(
            RoundPhase phase,
            int survivorCount,
            int infectedCount,
            RosterChange change
    ) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(change, "change");
        if (survivorCount < 0 || infectedCount < 0) {
            throw new IllegalArgumentException("Roster counts cannot be negative.");
        }

        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return RoundConclusion.NONE;
        }
        if (survivorCount == 0 && infectedCount == 0) {
            return RoundConclusion.CANCELLED;
        }
        if (phase == RoundPhase.COUNTDOWN || phase == RoundPhase.HEADSTART) {
            return survivorCount == 0 || infectedCount == 0
                    ? RoundConclusion.CANCELLED
                    : RoundConclusion.NONE;
        }
        if (change == RosterChange.INFECTION && survivorCount == 0) {
            return RoundConclusion.INFECTED_WIN;
        }
        if ((change == RosterChange.INFECTED_ELIMINATION
                || change == RosterChange.INFECTED_DEPARTURE)
                && infectedCount == 0
                && survivorCount > 0) {
            return RoundConclusion.SURVIVORS_WIN;
        }
        if (change == RosterChange.SURVIVOR_DEPARTURE && survivorCount == 0) {
            return RoundConclusion.CANCELLED;
        }
        return RoundConclusion.NONE;
    }
}
