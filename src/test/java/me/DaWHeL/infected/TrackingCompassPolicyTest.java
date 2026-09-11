package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackingCompassPolicyTest {

    @Test
    void timeLimitTrackingStartsOnlyBelowTheConfiguredTime() {
        assertFalse(TrackingCompassPolicy.timeLimit(true, TrackingCompassOverride.AUTO, 301, 300));
        assertFalse(TrackingCompassPolicy.timeLimit(true, TrackingCompassOverride.AUTO, 300, 300));
        assertTrue(TrackingCompassPolicy.timeLimit(true, TrackingCompassOverride.AUTO, 299, 300));
        assertFalse(TrackingCompassPolicy.timeLimit(false, TrackingCompassOverride.AUTO, 299, 300));
    }

    @Test
    void deathmatchTrackingFollowsTheConfiguredZombieCutoffBothWays() {
        assertFalse(TrackingCompassPolicy.deathmatch(true, TrackingCompassOverride.AUTO, 9, 10));
        assertTrue(TrackingCompassPolicy.deathmatch(true, TrackingCompassOverride.AUTO, 10, 10));
        assertTrue(TrackingCompassPolicy.deathmatch(true, TrackingCompassOverride.AUTO, 11, 10));
        assertFalse(TrackingCompassPolicy.deathmatch(false, TrackingCompassOverride.AUTO, 11, 10));
    }

    @Test
    void adminOverrideWinsOverAutomaticThresholds() {
        assertTrue(TrackingCompassPolicy.timeLimit(false, TrackingCompassOverride.ON, 600, 300));
        assertFalse(TrackingCompassPolicy.timeLimit(true, TrackingCompassOverride.OFF, 1, 300));
        assertTrue(TrackingCompassPolicy.deathmatch(false, TrackingCompassOverride.ON, 1, 10));
        assertFalse(TrackingCompassPolicy.deathmatch(true, TrackingCompassOverride.OFF, 100, 10));
    }
}
