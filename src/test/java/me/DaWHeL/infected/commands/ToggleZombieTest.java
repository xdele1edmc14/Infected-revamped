package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundActionResult;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToggleZombieTest {

    @Test
    void sendsTheManagersPhaseSafeResultToTheAdministrator() {
        GameManager gameManager = mock(GameManager.class);
        Player sender = mock(Player.class);
        when(gameManager.toggleZombieSafely(sender)).thenReturn(
                RoundActionResult.rejected("Zombie toggles are only allowed during active play."));

        new ToggleZombie(gameManager).onCommand(
                sender, mock(Command.class), "togglezombie", new String[0]);

        verify(gameManager).toggleZombieSafely(sender);
        verify(sender).sendMessage(contains("only allowed during active play"));
    }
}
