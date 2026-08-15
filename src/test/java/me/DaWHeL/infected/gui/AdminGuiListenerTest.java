package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AdminGuiListenerTest {
    private AdminGuiClickHandler clickHandler;
    private AdminGuiListener listener;
    private InfectedPlugin plugin;
    private BukkitScheduler scheduler;
    private Inventory topInventory;
    private InventoryView view;
    private AdminMenuHolder holder;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        clickHandler = mock(AdminGuiClickHandler.class);
        listener = new AdminGuiListener(plugin, clickHandler);
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
        when(player.getOpenInventory()).thenReturn(view);
        InventoryClickEvent event = clickEvent(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(clickHandler).handleClick(player, holder, 10, ClickType.LEFT);
    }

    @Test
    void cancelsBottomInventoryShiftClicksWithoutDispatchingActions() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        InventoryClickEvent event = clickEvent(player, 60, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verifyNoInteractions(clickHandler);
    }

    @Test
    void closesMenuWhenPermissionWasLost() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(false);
        when(player.getOpenInventory()).thenReturn(view);
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
    void unsupportedTransferClicksAreCancelOnly() {
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);

        for (ClickType clickType : new ClickType[]{
                ClickType.NUMBER_KEY,
                ClickType.SWAP_OFFHAND,
                ClickType.DROP,
                ClickType.CONTROL_DROP,
                ClickType.MIDDLE,
                ClickType.DOUBLE_CLICK
        }) {
            InventoryClickEvent event = clickEvent(player, 11, clickType, InventoryAction.HOTBAR_SWAP);
            listener.onInventoryClick(event);
            verify(event).setCancelled(true);
        }

        verifyNoInteractions(clickHandler);
        verify(scheduler, never()).runTask(any(), any(Runnable.class));
    }

    @Test
    void scheduledClickIsDiscardedAfterPlayerLeavesThatExactMenu() {
        doAnswer(invocation -> mock(BukkitTask.class))
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        Player player = mock(Player.class);
        when(player.hasPermission("infected.admin")).thenReturn(true);
        InventoryView otherView = mock(InventoryView.class);
        Inventory otherTop = mock(Inventory.class);
        when(otherView.getTopInventory()).thenReturn(otherTop);
        when(player.getOpenInventory()).thenReturn(otherView);
        InventoryClickEvent event = clickEvent(player, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), queued.capture());
        queued.getValue().run();
        verifyNoInteractions(clickHandler);
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
