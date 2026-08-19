package me.DaWHeL.infected;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InfectedLifeTracker {
    private final Map<UUID, Integer> remainingLives = new HashMap<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();

    public void register(UUID playerId, int lives) {
        if (lives < 1) {
            throw new IllegalArgumentException("Infected lives must be at least 1");
        }

        remainingLives.put(playerId, lives);
        eliminatedPlayers.remove(playerId);
    }

    public boolean consumeLife(UUID playerId) {
        int remaining = remainingLives.getOrDefault(playerId, 0);
        if (remaining > 1) {
            remainingLives.put(playerId, remaining - 1);
            return true;
        }

        remainingLives.remove(playerId);
        eliminatedPlayers.add(playerId);
        return false;
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public void remove(UUID playerId) {
        remainingLives.remove(playerId);
        eliminatedPlayers.remove(playerId);
    }

    public void clear() {
        remainingLives.clear();
        eliminatedPlayers.clear();
    }
}
