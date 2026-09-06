package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardBarTest {

    @Test
    void apportionsSegmentsAcrossTheActiveTeams() {
        assertEquals("BBBBBBBBRRRR", ScoreboardBar.build(12, 6, 12, "B", "R", "E"));
    }

    @Test
    void keepsBothActiveTeamsVisibleAtExtremeRatios() {
        assertEquals("BBBBBBBBBBBR", ScoreboardBar.build(99, 1, 12, "B", "R", "E"));
        assertEquals("BRRRRRRRRRRR", ScoreboardBar.build(1, 99, 12, "B", "R", "E"));
    }

    @Test
    void usesConfiguredEmptySegmentsWhenNobodyIsActive() {
        assertEquals("EEEE", ScoreboardBar.build(0, 0, 4, "B", "R", "E"));
    }

    @Test
    void rejectsNonPositiveBarLengths() {
        assertEquals("", ScoreboardBar.build(3, 2, 0, "B", "R", "E"));
    }
}
