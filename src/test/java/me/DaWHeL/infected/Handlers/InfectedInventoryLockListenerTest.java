package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.ParticipantRole;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedInventoryLockListenerTest {

    @Test
    void cancelledInfectedInventoryActionsDoNotForceAFullInventoryResend() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(click.getWhoClicked()).thenReturn(player);
        when(drag.getWhoClicked()).thenReturn(player);
        InfectedInventoryLockListener listener = new InfectedInventoryLockListener(gameManager);

        listener.onInventoryClick(click);
        listener.onInventoryDrag(drag);

        verify(click).setCancelled(true);
        verify(drag).setCancelled(true);
        verify(player, never()).updateInventory();
    }

    @Test
    void infectedCannotDropTheTrackingCompass() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(drop.getPlayer()).thenReturn(player);

        new InfectedInventoryLockListener(gameManager).onItemDrop(drop);

        verify(drop).setCancelled(true);
    }
}
