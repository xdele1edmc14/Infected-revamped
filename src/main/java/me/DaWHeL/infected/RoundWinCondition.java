package me.DaWHeL.infected;

import java.util.Optional;

public final class RoundWinCondition {
    private RoundWinCondition() {
    }

    public static Optional<RoundWinner> determine(int survivorCount, int infectedCount) {
        if (survivorCount == 0) {
            return Optional.of(RoundWinner.INFECTED);
        }
        if (infectedCount == 0) {
            return Optional.of(RoundWinner.SURVIVORS);
        }
        return Optional.empty();
    }
}
