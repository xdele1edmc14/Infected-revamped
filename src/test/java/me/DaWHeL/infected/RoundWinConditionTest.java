package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundWinConditionTest {

    @Test
    void selectsSurvivorsWhenNoActiveInfectedRemain() {
        assertEquals(Optional.of(RoundWinner.SURVIVORS), RoundWinCondition.determine(2, 0));
    }

    @Test
    void selectsInfectedWhenNoActiveSurvivorsRemain() {
        assertEquals(Optional.of(RoundWinner.INFECTED), RoundWinCondition.determine(0, 2));
    }

    @Test
    void hasNoWinnerWhileBothActiveTeamsRemain() {
        assertEquals(Optional.empty(), RoundWinCondition.determine(1, 1));
    }
}
