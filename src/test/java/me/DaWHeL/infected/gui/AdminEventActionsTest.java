package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.StartResult;
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
    void startReturnsCentralValidationErrorsWithoutDispatchingLegacyCommand() {
        when(gameManager.startGame()).thenReturn(StartResult.rejected(List.of(
                "Infected release spawns are missing or unavailable.",
                "At least 2 online lobby participants are required."
        )));

        AdminEventActions.ActionResult result = actions.start(admin);

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("Infected release spawns")),
                () -> assertTrue(result.message().contains("At least 2 online"))
        );
        verify(gameManager).startGame();
        verifyNoInteractions(server);
    }

    @Test
    void startDelegatesToTheCentralRoundControllerExactlyOnce() {
        when(gameManager.startGame()).thenReturn(StartResult.started());

        AdminEventActions.ActionResult result = actions.start(admin);

        assertTrue(result.success());
        verify(gameManager).startGame();
        verifyNoInteractions(server);
    }

    @Test
    void stopRejectsStoppedEventAndStopsRunningEvent() {
        when(gameManager.stopGame()).thenReturn(false, true);

        AdminEventActions.ActionResult stopped = actions.stop();
        AdminEventActions.ActionResult running = actions.stop();

        assertAll(
                () -> assertFalse(stopped.success()),
                () -> assertTrue(running.success())
        );
        verify(gameManager, times(2)).stopGame();
    }
}
