package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StopGameTest {

    @Test
    void rejectsLobbyAndEndingButStopsEveryLiveRoundPhase() {
        GameManager gameManager = mock(GameManager.class);
        CommandSender sender = mock(CommandSender.class);
        when(gameManager.getPhase()).thenReturn(
                RoundPhase.LOBBY,
                RoundPhase.ENDING,
                RoundPhase.COUNTDOWN,
                RoundPhase.HEADSTART,
                RoundPhase.ACTIVE
        );
        when(gameManager.stopGame()).thenReturn(true);
        StopGame command = new StopGame(gameManager);

        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        verify(sender).sendMessage(contains("No game"));

        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        verify(sender).sendMessage(contains("already cleaning up"));

        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        verify(gameManager, org.mockito.Mockito.times(3)).stopGame();
    }
}
