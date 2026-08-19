package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedContainmentListenerTest {
    private GameManager gameManager;
    private InfectedContainmentListener listener;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        listener = new InfectedContainmentListener(gameManager);
        player = mock(Player.class);
        world = mock(World.class);
        when(gameManager.isContainedInfected(player)).thenReturn(true);
    }

    @Test
    void keepsAContainedInfectedInsideTheHoldingBlockWhilePreservingTheirView() {
        Location from = new Location(world, 10.2, 64, 5.2, 0, 0);
        Location to = new Location(world, 11.1, 64, 5.2, 90, 12);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);

        listener.onContainedInfectedMove(event);

        ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
        verify(event).setTo(destination.capture());
        assertEquals(from.getX(), destination.getValue().getX());
        assertEquals(from.getY(), destination.getValue().getY());
        assertEquals(from.getZ(), destination.getValue().getZ());
        assertEquals(to.getYaw(), destination.getValue().getYaw());
        assertEquals(to.getPitch(), destination.getValue().getPitch());
    }

    @Test
    void allowsViewRotationAndMovementInsideTheSameBlock() {
        PlayerMoveEvent event = event(
                new Location(world, 10.2, 64, 5.2, 0, 0),
                new Location(world, 10.8, 64.9, 5.8, 90, 12));

        listener.onContainedInfectedMove(event);

        verify(event, never()).setTo(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsRoundOwnedReleaseTeleports() {
        when(gameManager.isRoundTeleportBypass(player)).thenReturn(true);
        PlayerMoveEvent event = event(
                new Location(world, 10, 64, 5),
                new Location(world, 100, 70, 100));

        listener.onContainedInfectedMove(event);

        verify(event, never()).setTo(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresPlayersWhoAreNotContainedInfected() {
        when(gameManager.isContainedInfected(player)).thenReturn(false);
        PlayerMoveEvent event = event(
                new Location(world, 10, 64, 5),
                new Location(world, 11, 64, 5));

        listener.onContainedInfectedMove(event);

        verify(event, never()).setTo(org.mockito.ArgumentMatchers.any());
    }

    private PlayerMoveEvent event(Location from, Location to) {
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(from);
        when(event.getTo()).thenReturn(to);
        return event;
    }
}
