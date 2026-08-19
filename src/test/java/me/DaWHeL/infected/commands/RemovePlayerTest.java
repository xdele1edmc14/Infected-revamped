package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundActionResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemovePlayerTest {

    @Test
    void delegatesRemovalWithoutMutatingTheSurvivorListDirectly() {
        GameManager gameManager = mock(GameManager.class);
        CommandSender sender = mock(CommandSender.class);
        Player target = mock(Player.class);
        when(target.isOnline()).thenReturn(true);
        when(gameManager.removePlayer(target)).thenReturn(
                RoundActionResult.accepted("target was removed from the round."));

        RemovePlayer command = new RemovePlayer(gameManager, name -> target);
        command.onCommand(sender, mock(Command.class), "removeplayer", new String[]{"target"});

        verify(gameManager).removePlayer(target);
        verify(sender).sendMessage(contains("removed from the round"));
    }
}
