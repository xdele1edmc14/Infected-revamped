package me.DaWHeL.infected;

import java.util.Objects;

public final class TrackingCompassPolicy {
    private TrackingCompassPolicy() {
    }

    public static boolean timeLimit(
            boolean configuredEnabled,
            TrackingCompassOverride override,
            int remainingSeconds,
            int activationSeconds
    ) {
        return switch (Objects.requireNonNull(override, "override")) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> configuredEnabled
                    && remainingSeconds > 0
                    && remainingSeconds < Math.max(0, activationSeconds);
        };
    }

    public static boolean deathmatch(
            boolean configuredEnabled,
            TrackingCompassOverride override,
            int infectedCount,
            int disableBelowInfected
    ) {
        return switch (Objects.requireNonNull(override, "override")) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> configuredEnabled
                    && infectedCount >= Math.max(1, disableBelowInfected);
        };
    }
}
