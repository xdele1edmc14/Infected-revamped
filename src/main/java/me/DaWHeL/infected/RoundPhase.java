package me.DaWHeL.infected;

public enum RoundPhase {
    LOBBY,
    COUNTDOWN,
    DEPLOYING,
    HEADSTART,
    ACTIVE,
    ENDING;

    public boolean isRunning() {
        return this != LOBBY;
    }

    public boolean queuesLateJoins() {
        return switch (this) {
            case COUNTDOWN, DEPLOYING, HEADSTART, ACTIVE -> true;
            case LOBBY, ENDING -> false;
        };
    }

    public boolean allowsParticipantRemoval() {
        return switch (this) {
            case COUNTDOWN, DEPLOYING, HEADSTART, ACTIVE -> true;
            case LOBBY, ENDING -> false;
        };
    }

    public boolean allowsZombieToggle() {
        return this == ACTIVE;
    }

    public boolean allowsConfigReload() {
        return this == LOBBY;
    }

    public boolean allowsAdminStop() {
        return switch (this) {
            case COUNTDOWN, DEPLOYING, HEADSTART, ACTIVE -> true;
            case LOBBY, ENDING -> false;
        };
    }

    public boolean canTransitionTo(RoundPhase next) {
        return switch (this) {
            case LOBBY -> next == COUNTDOWN;
            case COUNTDOWN -> next == DEPLOYING || next == ENDING;
            case DEPLOYING -> next == HEADSTART || next == ENDING;
            case HEADSTART -> next == ACTIVE || next == ENDING;
            case ACTIVE -> next == ENDING;
            case ENDING -> next == LOBBY;
        };
    }
}
