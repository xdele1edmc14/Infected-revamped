package me.DaWHeL.infected;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RoundTaskRegistryTest {
    private RoundTaskRegistry registry;
    private BukkitTask gameplay;
    private BukkitTask cleanup;

    @BeforeEach
    void setUp() {
        registry = new RoundTaskRegistry();
        gameplay = mock(BukkitTask.class);
        cleanup = mock(BukkitTask.class);
    }

    @Test
    void endingCancelsGameplayButAllowsCleanupWork() {
        registry.trackGameplay(gameplay);

        registry.cancelGameplay();
        registry.trackCleanup(cleanup);

        verify(gameplay).cancel();
        verify(cleanup, never()).cancel();
        assertEquals(0, registry.gameplayTaskCount());
        assertEquals(1, registry.cleanupTaskCount());
    }

    @Test
    void cancelAllClosesBothScopesAndCancelsLateHandles() {
        BukkitTask lateGameplay = mock(BukkitTask.class);
        BukkitTask lateCleanup = mock(BukkitTask.class);
        registry.trackGameplay(gameplay);
        registry.trackCleanup(cleanup);

        registry.cancelAll();
        registry.trackGameplay(lateGameplay);
        registry.trackCleanup(lateCleanup);

        verify(gameplay).cancel();
        verify(cleanup).cancel();
        verify(lateGameplay).cancel();
        verify(lateCleanup).cancel();
        assertEquals(0, registry.gameplayTaskCount());
        assertEquals(0, registry.cleanupTaskCount());
    }

    @Test
    void forgettingCompletedTaskPreventsLaterCancellation() {
        registry.trackGameplay(gameplay);

        registry.forget(gameplay);
        registry.cancelGameplay();

        verify(gameplay, never()).cancel();
    }

    @Test
    void cancellationIsIdempotentAndResetStartsEmptyOpenScopes() {
        registry.trackGameplay(gameplay);
        registry.trackCleanup(cleanup);

        registry.cancelAll();
        registry.cancelAll();
        registry.resetForNewRound();
        BukkitTask nextRound = mock(BukkitTask.class);
        registry.trackGameplay(nextRound);

        verify(gameplay, times(1)).cancel();
        verify(cleanup, times(1)).cancel();
        verify(nextRound, never()).cancel();
        assertEquals(1, registry.gameplayTaskCount());
    }
}

