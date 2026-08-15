package me.DaWHeL.infected.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AdminGuiListenerTest {
    private AdminGuiClickHandler clickHandler;
    private AdminGuiListener listener;
    private Inventory topInventory;
    private InventoryView view;
    private AdminMenuHolder holder;

    @BeforeEach
    void setUp() {
        clickHandler = mock(AdminGuiClickHandler.class);
        listener = new AdminGuiListener(clickHandler);
        topInventory = mock(Inventory.class);
        view = mock(InventoryView.class);
        holder = AdminMenuHolder.root(AdminMenuHolder.MenuType.MAIN);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(topInventory.getHolder()).thenReturn(holder);
        when(topInventory.getSize()).thenReturn(54);
    }

    @Test
    void cancelsAndDelegatesAuthorizedTopInventoryClicks() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        InventoryClickEvent event = clickEvent(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(clickHandler).handleClick(player, holder, 10, ClickType.LEFT);
    }

    @Test
    void cancelsBottomInventoryShiftClicksWithoutDispatchingActions() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        InventoryClickEvent event = clickEvent(player, 60, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(clickHandler);
    }

    @Test
    void closesMenuWhenPermissionWasLost() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(false);
        InventoryClickEvent event = clickEvent(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(player).closeInventory();
        verify(player).sendMessage(contains("permission"));
        verifyNoInteractions(clickHandler);
    }

    @Test
    void cancelsEveryDragInsidePluginMenus() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void ignoresInventoriesNotOwnedByThePluginGui() {
        when(topInventory.getHolder()).thenReturn(null);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);

        listener.onInventoryClick(event);

        verify(event, never()).setCancelled(anyBoolean());
        verifyNoInteractions(clickHandler);
    }

    private InventoryClickEvent clickEvent(
            Player player,
            int rawSlot,
            ClickType clickType,
            InventoryAction action
    ) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(action);
        return event;
    }
}
