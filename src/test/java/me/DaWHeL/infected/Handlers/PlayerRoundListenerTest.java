package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRoundListenerTest {

    @Test
    void lobbyJoinResetsAndRegistersOneSurvivor() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(gameManager.getPhase()).thenReturn(RoundPhase.LOBBY);

        new PlayerJoinListener(gameManager).onPlayerJoin(event);

        verify(gameManager).resetPlayerState(player);
        verify(gameManager).registerLobbySurvivor(player);
        verify(gameManager, never()).addLateJoinInfected(player);
    }

    @Test
    void endingJoinWaitsForFinalLobbyReconstruction() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ENDING);

        new PlayerJoinListener(gameManager).onPlayerJoin(event);

        verify(gameManager, never()).resetPlayerState(player);
        verify(gameManager, never()).registerLobbySurvivor(player);
        verify(gameManager, never()).addLateJoinInfected(player);
    }

    @Test
    void runningJoinQueuesThePlayerForTheNextRound() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);

        new PlayerJoinListener(gameManager).onPlayerJoin(event);

        verify(gameManager).queueLateJoin(player);
        verify(gameManager, never()).addLateJoinInfected(player);
    }

    @Test
    void quitDelegatesMembershipAndWinHandlingToTheRoundOwner() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        new PlayerQuitListener(gameManager).onPlayerQuit(event);

        verify(gameManager).handleQuit(player);
    }
}
