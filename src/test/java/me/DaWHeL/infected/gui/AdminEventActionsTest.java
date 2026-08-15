package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminEventActionsTest {
    private InfectedPlugin plugin;
    private Server server;
    private GameManager gameManager;
    private AdminSetupService setupService;
    private AdminEventActions actions;
    private Player admin;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        server = mock(Server.class);
        gameManager = mock(GameManager.class);
        setupService = mock(AdminSetupService.class);
        admin = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        actions = new AdminEventActions(plugin, gameManager, setupService);
    }

    @Test
    void startRejectsIncompleteSetupWithoutDispatchingLegacyCommand() {
        when(setupService.snapshot(0, 0)).thenReturn(snapshot(false, 0));

        AdminEventActions.ActionResult result = actions.start(admin);

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("incomplete"))
        );
        verifyNoInteractions(server);
    }

    @Test
    void startDispatchesExistingCommandAfterRevalidation() {
        when(setupService.snapshot(0, 0)).thenReturn(snapshot(true, 1));
        when(server.dispatchCommand(admin, "startinfected")).thenReturn(true);

        AdminEventActions.ActionResult result = actions.start(admin);

        assertTrue(result.success());
        verify(server).dispatchCommand(admin, "startinfected");
    }

    @Test
    void stopRejectsStoppedEventAndStopsRunningEvent() {
        when(gameManager.isGameRunning()).thenReturn(false, true);

        AdminEventActions.ActionResult stopped = actions.stop();
        AdminEventActions.ActionResult running = actions.stop();

        assertAll(
                () -> assertFalse(stopped.success()),
                () -> assertTrue(running.success())
        );
        verify(gameManager).stopGame();
    }

    private static AdminSetupService.SetupSnapshot snapshot(boolean spawn, int points) {
        return new AdminSetupService.SetupSnapshot(spawn, points, 0, 0, 5, 10, 5);
    }
}
