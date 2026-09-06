package me.DaWHeL.infected.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Function;

public final class PoissonDiscPlacement {
    private static final double MINIMUM_RADIUS = 4.0D;

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int targetCount;
    private final int maximumAttempts;
    private final int stallLimit;
    private final SplittableRandom random;
    private final List<ChestSite> sites = new ArrayList<>();
    private double currentRadius;
    private int attempts;
    private int stalledAttempts;

    public PoissonDiscPlacement(int minX, int maxX, int minZ, int maxZ, int targetCount, long seed) {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Invalid placement bounds.");
        if (targetCount < 1) throw new IllegalArgumentException("Target chest count must be positive.");
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.targetCount = targetCount;
        maximumAttempts = Math.max(2_000, Math.multiplyExact(targetCount, 500));
        stallLimit = Math.max(200, targetCount * 20);
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        currentRadius = Math.max(MINIMUM_RADIUS,
                Math.sqrt((double) width * (double) depth / targetCount) * 0.75D);
        random = new SplittableRandom(seed);
    }

    public void step(int budget, Function<Candidate, Optional<ChestSite>> validator) {
        if (budget < 1) throw new IllegalArgumentException("Placement budget must be positive.");
        Objects.requireNonNull(validator, "validator");
        for (int processed = 0; processed < budget && !finished(); processed++) {
            Candidate candidate = nextCandidate();
            consider(candidate, validator.apply(candidate));
        }
    }

    Candidate nextCandidate() {
        if (finished()) throw new IllegalStateException("Placement sampling is already finished.");
        attempts++;
        return new Candidate(random.nextInt(minX, maxX + 1), random.nextInt(minZ, maxZ + 1));
    }

    boolean consider(Candidate candidate, Optional<ChestSite> validated) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(validated, "validated");
        boolean accepted = validated.isPresent() && farEnough(validated.get());
        if (accepted) {
            sites.add(validated.get());
            stalledAttempts = 0;
            return true;
        }
        stalledAttempts++;
        if (stalledAttempts >= stallLimit && currentRadius > MINIMUM_RADIUS) {
            currentRadius = Math.max(MINIMUM_RADIUS, currentRadius * 0.9D);
            stalledAttempts = 0;
        }
        return false;
    }

    private boolean farEnough(ChestSite candidate) {
        double minimumSquared = currentRadius * currentRadius;
        for (ChestSite site : sites) {
            long dx = (long) candidate.x() - site.x();
            long dz = (long) candidate.z() - site.z();
            if ((double) dx * dx + (double) dz * dz < minimumSquared) return false;
        }
        return true;
    }

    public boolean complete() {
        return sites.size() == targetCount;
    }

    public boolean finished() {
        return complete() || attempts >= maximumAttempts;
    }

    public List<ChestSite> sites() {
        return List.copyOf(sites);
    }

    public int attempts() {
        return attempts;
    }

    public int maximumAttempts() {
        return maximumAttempts;
    }

    public double currentRadius() {
        return currentRadius;
    }

    public record Candidate(int x, int z) {
    }
}
