package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.StartResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartGameTest {

    @Test
    void reportsCentralValidationFailureWithoutStartingAnotherFlow() {
        GameManager gameManager = mock(GameManager.class);
        CommandSender sender = mock(CommandSender.class);
        when(gameManager.startGame()).thenReturn(StartResult.rejected(List.of(
                "Survivor spawns are missing or unavailable.",
                "Starting infected must be lower than the participant count."
        )));
        StartGame command = new StartGame(gameManager);

        command.onCommand(sender, mock(Command.class), "startinfected", new String[0]);

        verify(gameManager).startGame();
        verify(sender).sendMessage(contains("Survivor spawns are missing"));
        verify(sender).sendMessage(contains("Starting infected must be lower"));
    }

    @Test
    void delegatesAnAcceptedStartExactlyOnce() {
        GameManager gameManager = mock(GameManager.class);
        when(gameManager.startGame()).thenReturn(StartResult.started());
        StartGame command = new StartGame(gameManager);

        command.onCommand(mock(CommandSender.class), mock(Command.class), "startinfected", new String[0]);

        verify(gameManager).startGame();
    }
}
