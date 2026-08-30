package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.PluginTaskScheduler;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpawnCreationSessionManagerTest {
    private AdminSetupService setupService;
    private PluginTaskScheduler scheduler;
    private AdminGuiNavigator navigator;
    private SpawnCreationSessionManager sessions;
    private Player player;
    private BukkitTask timeoutTask;
    private Runnable timeout;

    @BeforeEach
    void setUp() {
        setupService = mock(AdminSetupService.class);
        scheduler = mock(PluginTaskScheduler.class);
        navigator = mock(AdminGuiNavigator.class);
        timeoutTask = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runLater(timeoutCaptor.capture(), eq(1200L))).thenReturn(timeoutTask);
        sessions = new SpawnCreationSessionManager(setupService, scheduler);
        sessions.bindNavigator(navigator);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        sessions.begin(player, SpawnRole.INFECTED_RESPAWN, 2);
        timeout = timeoutCaptor.getValue();
    }

    @Test
    void beginClosesTheGuiAndPromptsForAHiddenChatName() {
        verify(player).closeInventory();
        verify(player).sendMessage(contains("60 seconds"));
        verify(player).sendMessage(contains("close"));
        assertTrue(sessions.hasSession(player));
    }

    @Test
    void validNameSavesTheCurrentLocationAndReopensTheSameRolePage() {
        Location current = mock(Location.class);
        when(player.getLocation()).thenReturn(current);
        when(setupService.teleportPoints(SpawnRole.INFECTED_RESPAWN)).thenReturn(List.of());

        assertTrue(sessions.capture(player, "north_2"));

        verify(setupService).saveTeleportPoint(SpawnRole.INFECTED_RESPAWN, "north_2", current);
        verify(timeoutTask).cancel();
        verify(navigator).openTeleportPoints(player, SpawnRole.INFECTED_RESPAWN, 2);
        assertFalse(sessions.hasSession(player));
    }

    @Test
    void closeCancelsWithoutSavingAndReturnsToTheSourcePage() {
        assertTrue(sessions.capture(player, "close"));

        verify(setupService, never()).saveTeleportPoint(any(), any(), any());
        verify(navigator).openTeleportPoints(player, SpawnRole.INFECTED_RESPAWN, 2);
        assertFalse(sessions.hasSession(player));
    }

    @Test
    void invalidAndDuplicateNamesKeepTheSessionOpen() {
        when(setupService.teleportPoints(SpawnRole.INFECTED_RESPAWN)).thenReturn(List.of(
                new AdminSetupService.TeleportPoint("North", mock(AdminSetupService.StoredLocation.class))));

        assertFalse(sessions.capture(player, "bad name"));
        assertFalse(sessions.capture(player, "north"));

        verify(setupService, never()).saveTeleportPoint(any(), any(), any());
        verify(navigator, never()).openTeleportPoints(any(), any(SpawnRole.class), any(Integer.class));
        assertTrue(sessions.hasSession(player));
    }

    @Test
    void timeoutAndGlobalCancellationDiscardPendingWrites() {
        timeout.run();

        assertFalse(sessions.hasSession(player));
        verify(player).sendMessage(contains("timed out"));

        sessions.begin(player, SpawnRole.SURVIVOR, 0);
        sessions.cancelAll();

        assertFalse(sessions.hasSession(player));
        verify(setupService, never()).saveTeleportPoint(any(), any(), any());
    }
}
