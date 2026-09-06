package me.DaWHeL.infected.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HelpInfectedCommandTest {
    @Test
    void helpDoesNotAdvertiseJumpFeather() {
        CommandSender sender = mock(CommandSender.class);

        new HelpInfectedCommand().onCommand(
                sender, mock(Command.class), "helpinfected", new String[0]);

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().stream()
                .noneMatch(message -> message.toLowerCase().contains("givefeather")
                        || message.toLowerCase().contains("jump feather")));
    }
}
