package me.DaWHeL.infected;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RoundStatsTracker {
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> infections = new HashMap<>();

    public void recordKill(UUID playerId) {
        kills.merge(playerId, 1, Integer::sum);
    }

    public void recordInfection(UUID playerId) {
        infections.merge(playerId, 1, Integer::sum);
    }

    public int kills(UUID playerId) {
        return kills.getOrDefault(playerId, 0);
    }

    public int infections(UUID playerId) {
        return infections.getOrDefault(playerId, 0);
    }

    public void remove(UUID playerId) {
        kills.remove(playerId);
        infections.remove(playerId);
    }

    public void clear() {
        kills.clear();
        infections.clear();
    }
}
