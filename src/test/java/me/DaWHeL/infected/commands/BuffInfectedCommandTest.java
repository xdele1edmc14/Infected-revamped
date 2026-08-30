package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundActionResult;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuffInfectedCommandTest {

    @Test
    void delegatesTheEntireToggleToTheRoundManager() {
        GameManager manager = mock(GameManager.class);
        Player sender = mock(Player.class);
        Command command = mock(Command.class);
        when(manager.toggleInfectedBuff()).thenReturn(
                RoundActionResult.accepted("Infected buffs enabled."));

        new BuffInfectedCommand(manager).onCommand(sender, command, "buffinfected", new String[0]);

        verify(manager).toggleInfectedBuff();
        verify(sender).sendMessage(org.bukkit.ChatColor.GREEN + "Infected buffs enabled.");
    }

    @Test
    void reportsTheManagerPhaseRejectionWithoutMutatingPlayers() {
        GameManager manager = mock(GameManager.class);
        Player sender = mock(Player.class);
        Command command = mock(Command.class);
        when(manager.toggleInfectedBuff()).thenReturn(
                RoundActionResult.rejected("Infected buffs can only be changed during active play."));

        new BuffInfectedCommand(manager).onCommand(sender, command, "buffinfected", new String[0]);

        verify(sender).sendMessage(org.bukkit.ChatColor.RED
                + "Infected buffs can only be changed during active play.");
    }
}
