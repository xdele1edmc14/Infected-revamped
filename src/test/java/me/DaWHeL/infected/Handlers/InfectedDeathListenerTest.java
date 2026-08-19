package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.Roles.Infected;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedDeathListenerTest {

    @Test
    void activeInfectedDeathConsumesExactlyOneLifeDecision() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        Infected infected = mock(Infected.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(infected.getPlayer()).thenReturn(player);
        when(gameManager.getInfected()).thenReturn(List.of(infected));

        new InfectedDeathListener(gameManager).onPlayerDeath(event);

        verify(gameManager).handleInfectedDeath(player);
        verify(event).setDroppedExp(0);
    }

    @Test
    void nonInfectedDeathDoesNotTouchFiniteLives() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.getInfected()).thenReturn(List.of());

        new InfectedDeathListener(gameManager).onPlayerDeath(event);

        verify(gameManager, never()).handleInfectedDeath(player);
    }
}
