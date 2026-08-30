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
        Objects.requireNonNull(change, "change");
        return evaluate(phase, survivorCount, infectedCount);
    }

    public static RoundConclusion evaluate(
            RoundPhase phase,
            int survivorCount,
            int infectedCount
    ) {
        Objects.requireNonNull(phase, "phase");
        if (survivorCount < 0 || infectedCount < 0) {
            throw new IllegalArgumentException("Roster counts cannot be negative.");
        }

        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return RoundConclusion.NONE;
        }
        if (survivorCount == 0 && infectedCount == 0) {
            return RoundConclusion.ABANDONED;
        }
        if (phase == RoundPhase.ACTIVE) {
            if (survivorCount == 0) {
                return RoundConclusion.INFECTED_WIN;
            }
            if (infectedCount == 0) {
                return RoundConclusion.SURVIVORS_WIN;
            }
            return RoundConclusion.NONE;
        }
        if (phase == RoundPhase.COUNTDOWN
                || phase == RoundPhase.DEPLOYING
                || phase == RoundPhase.HEADSTART) {
            return survivorCount == 0 || infectedCount == 0
                    ? RoundConclusion.ABANDONED
                    : RoundConclusion.NONE;
        }
        return RoundConclusion.NONE;
    }
}
