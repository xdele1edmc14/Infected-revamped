package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedDeathListenerTest {

    @Test
    void roundOwnedDeathClearsDropsAndDelegatesOneDeathDecision() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);
        when(gameManager.handlePlayerDeath(player)).thenReturn(true);

        new InfectedDeathListener(gameManager).onPlayerDeath(event);

        verify(gameManager).handlePlayerDeath(player);
        assertTrue(drops.isEmpty());
        verify(event).setDroppedExp(0);
    }

    @Test
    void nonParticipantDeathKeepsVanillaDrops() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.handlePlayerDeath(player)).thenReturn(false);

        new InfectedDeathListener(gameManager).onPlayerDeath(event);

        verify(gameManager).handlePlayerDeath(player);
        verify(event, never()).getDrops();
        verify(event, never()).setDroppedExp(0);
    }

    @Test
    void preActiveInfectedDeathIsStillRoundOwned() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        List<ItemStack> drops = new ArrayList<>(List.of(mock(ItemStack.class)));
        when(event.getEntity()).thenReturn(player);
        when(event.getDrops()).thenReturn(drops);
        when(gameManager.handlePlayerDeath(player)).thenReturn(true);

        new InfectedDeathListener(gameManager).onPlayerDeath(event);

        verify(gameManager).handlePlayerDeath(player);
        assertTrue(drops.isEmpty());
        verify(event).setDroppedExp(0);
    }
}
