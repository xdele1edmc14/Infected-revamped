package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PoissonDiscPlacementTest {
    @Test
    void seededSamplingProducesTheRequestedOrganicNonOverlappingLayout() {
        PoissonDiscPlacement first = completed(12345L);
        PoissonDiscPlacement second = completed(12345L);

        assertEquals(40, first.sites().size());
        assertEquals(first.sites(), second.sites());
        assertPairwiseDistance(first.sites(), first.currentRadius());
    }

    @Test
    void stopsAfterABoundedNumberOfAttemptsWhenNoOutdoorSiteExists() {
        PoissonDiscPlacement placement = new PoissonDiscPlacement(0, 99, 0, 99, 10, 77L);

        while (!placement.finished()) {
            placement.step(250, ignored -> Optional.empty());
        }

        assertFalse(placement.complete());
        assertTrue(placement.attempts() <= placement.maximumAttempts());
        assertTrue(placement.sites().isEmpty());
    }

    private static PoissonDiscPlacement completed(long seed) {
        PoissonDiscPlacement placement = new PoissonDiscPlacement(0, 199, 0, 199, 40, seed);
        while (!placement.finished()) {
            placement.step(250, candidate -> Optional.of(new ChestSite(candidate.x(), 64, candidate.z())));
        }
        assertTrue(placement.complete());
        return placement;
    }

    private static void assertPairwiseDistance(List<ChestSite> sites, double minimum) {
        for (int left = 0; left < sites.size(); left++) {
            for (int right = left + 1; right < sites.size(); right++) {
                long dx = (long) sites.get(left).x() - sites.get(right).x();
                long dz = (long) sites.get(left).z() - sites.get(right).z();
                assertTrue(dx * dx + dz * dz >= minimum * minimum,
                        sites.get(left) + " overlaps " + sites.get(right));
            }
        }
    }
}
