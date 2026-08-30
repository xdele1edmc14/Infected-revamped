package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StartGameTest {

    @Test
    void standaloneStartDelegatesToSharedAdminAction() {
        AdminActionService actions = mock(AdminActionService.class);
        CommandSender sender = mock(CommandSender.class);
        StartGame command = new StartGame(actions);

        command.onCommand(sender, mock(Command.class), "startinfected", new String[0]);

        verify(actions).start(sender);
    }
}
