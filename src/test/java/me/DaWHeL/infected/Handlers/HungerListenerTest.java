package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HungerListenerTest {
    private GameManager gameManager;
    private Player player;
    private HungerListener listener;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        player = mock(Player.class);
        listener = new HungerListener(gameManager);
    }

    @Test
    void cancelsHungerForCurrentRoundParticipant() {
        when(gameManager.isRoundParticipant(player)).thenReturn(true);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 18);

        listener.onHungerChange(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void leavesUnrelatedPlayerHungerUnchanged() {
        when(gameManager.isRoundParticipant(player)).thenReturn(false);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 18);

        listener.onHungerChange(event);

        assertFalse(event.isCancelled());
    }
}

