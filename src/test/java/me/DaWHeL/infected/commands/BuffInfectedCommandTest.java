package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuffInfectedCommandTest {

    @Test
    void rejectsBuffChangesOutsideActivePlay() {
        GameManager gameManager = mock(GameManager.class);
        Player sender = mock(Player.class);
        when(gameManager.isGameRunning()).thenReturn(true);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ENDING);

        new BuffInfectedCommand(gameManager, mock(InfectedPlugin.class))
                .onCommand(sender, mock(Command.class), "buffinfected", new String[0]);

        verify(gameManager, never()).setBuffEnabled(org.mockito.ArgumentMatchers.anyBoolean());
    }
}
