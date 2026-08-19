package me.DaWHeL.infected;

public enum RoundPhase {
    LOBBY,
    COUNTDOWN,
    HEADSTART,
    ACTIVE,
    ENDING;

    public boolean isRunning() {
        return this != LOBBY;
    }

    public boolean canTransitionTo(RoundPhase next) {
        return switch (this) {
            case LOBBY -> next == COUNTDOWN;
            case COUNTDOWN -> next == HEADSTART || next == ENDING;
            case HEADSTART -> next == ACTIVE || next == ENDING;
            case ACTIVE -> next == ENDING;
            case ENDING -> next == LOBBY;
        };
    }
}
