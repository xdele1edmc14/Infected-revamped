package me.DaWHeL.infected.Roles;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleHighlightingTest {

    @Test
    void survivorIsHighlightedAsTheInfectedTarget() {
        Player player = playerThatStopsAfterHighlighting();

        assertThrows(StopAfterHighlighting.class, () -> new Survivor(player));

        verify(player).setGlowing(true);
    }

    @Test
    void infectedIsNotHighlightedForSurvivors() {
        Player player = playerThatStopsAfterHighlighting();

        assertThrows(StopAfterHighlighting.class,
                () -> new Infected(mock(JavaPlugin.class), player, false));

        verify(player).setGlowing(false);
    }

    private static Player playerThatStopsAfterHighlighting() {
        Player player = mock(Player.class);
        when(player.getInventory()).thenThrow(new StopAfterHighlighting());
        return player;
    }

    private static final class StopAfterHighlighting extends RuntimeException {
    }
}
