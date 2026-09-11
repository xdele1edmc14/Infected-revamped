package me.DaWHeL.infected;

import org.bukkit.Location;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class InfectedRespawnSelector {
    private InfectedRespawnSelector() {
    }

    public static Optional<Location> select(List<Location> candidates, Random random) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(random, "random");
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())).clone());
    }
}
