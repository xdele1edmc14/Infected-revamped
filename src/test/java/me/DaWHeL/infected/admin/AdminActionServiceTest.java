package me.DaWHeL.infected.admin;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.StartResult;
import me.DaWHeL.infected.gui.AdminSetupService;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminActionServiceTest {
    private InfectedPlugin plugin;
    private GameManager gameManager;
    private AdminSetupService setupService;
    private Runnable sessionCanceller;
    private AdminActionService actions;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        gameManager = mock(GameManager.class);
        setupService = mock(AdminSetupService.class);
        sessionCanceller = mock(Runnable.class);
        sender = mock(CommandSender.class);
        when(sender.hasPermission("infected.admin")).thenReturn(true);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        actions = new AdminActionService(plugin, gameManager, setupService, sessionCanceller);
    }

    @Test
    void permissionFailurePreventsEveryMutation() {
        when(sender.hasPermission("infected.admin")).thenReturn(false);

        assertFalse(actions.start(sender));
        assertFalse(actions.stop(sender));
        assertFalse(actions.reload(sender));

        verify(gameManager, never()).startGame();
        verify(gameManager, never()).stopGame();
        verify(plugin, never()).reloadConfig();
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(contains("permission"));
    }

    @Test
    void acceptedStartCancelsPendingSetupSessions() {
        when(gameManager.startGame()).thenReturn(StartResult.started());

        assertTrue(actions.start(sender));

        verify(sessionCanceller).run();
        verify(sender).sendMessage(contains("countdown started"));
    }

    @Test
    void rejectedStartReportsEveryValidationErrorWithoutCancellingSetup() {
        when(gameManager.startGame()).thenReturn(StartResult.rejected(List.of("Missing spawn", "Too few players")));

        assertFalse(actions.start(sender));

        verify(sender).sendMessage(contains("Missing spawn"));
        verify(sender).sendMessage(contains("Too few players"));
        verify(sessionCanceller, never()).run();
    }

    @Test
    void stopAndReloadRespectLifecyclePhases() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.LOBBY, RoundPhase.ACTIVE, RoundPhase.LOBBY);
        when(gameManager.stopGame()).thenReturn(true);

        assertFalse(actions.stop(sender));
        assertFalse(actions.reload(sender));
        assertTrue(actions.reload(sender));

        verify(gameManager, never()).stopGame();
        verify(plugin).reloadConfig();
        verify(sessionCanceller).run();
    }

    @Test
    void statusAndHelpSupportConsoleSenders() {
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);
        when(gameManager.getSurvivors()).thenReturn(List.of());
        when(gameManager.getInfected()).thenReturn(List.of());
        when(setupService.snapshot(0, 0)).thenReturn(
                new AdminSetupService.SetupSnapshot(true, 2, 2, 2, 6, 0, 2, 10, 5, 20));

        assertTrue(actions.status(sender));
        assertTrue(actions.help(sender));

        verify(sender).sendMessage(contains("HEADSTART"));
        verify(sender).sendMessage(contains("/infected start"));
        verify(sender).sendMessage(contains("/infected status"));
    }
}
