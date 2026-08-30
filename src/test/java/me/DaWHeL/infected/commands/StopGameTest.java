package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StopGameTest {

    @Test
    void standaloneStopDelegatesToSharedAdminAction() {
        AdminActionService actions = mock(AdminActionService.class);
        CommandSender sender = mock(CommandSender.class);
        StopGame command = new StopGame(actions);

        command.onCommand(sender, mock(Command.class), "stopinfected", new String[0]);
        verify(actions).stop(sender);
    }
}
