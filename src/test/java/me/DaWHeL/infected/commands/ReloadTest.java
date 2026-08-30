package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReloadTest {

    @Test
    void standaloneReloadDelegatesToSharedAdminAction() {
        AdminActionService actions = mock(AdminActionService.class);
        CommandSender sender = mock(CommandSender.class);

        new Reload(actions).onCommand(
                sender, mock(Command.class), "reloadinfected", new String[0]);

        verify(actions).reload(sender);
    }
}
