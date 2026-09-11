package me.DaWHeL.infected;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedRespawnSelectorTest {

    @Test
    void returnsEmptyWhenNoRespawnIsConfigured() {
        assertTrue(InfectedRespawnSelector.select(List.of(), new Random(1)).isEmpty());
    }

    @Test
    void trustsAConfiguredRespawnWithoutInspectingItsWorld() {
        Location configured = mock(Location.class);
        Location copy = mock(Location.class);
        when(configured.clone()).thenReturn(copy);
        when(configured.getWorld()).thenThrow(new AssertionError("Configured spawns must not be inspected."));

        Optional<Location> selected = InfectedRespawnSelector.select(List.of(configured), new Random(1));

        assertSame(copy, selected.orElseThrow());
        verify(configured, never()).getWorld();
    }
}
