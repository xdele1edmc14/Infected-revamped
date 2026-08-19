package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReloadTest {

    @Test
    void rejectsReloadOutsideTheLobby() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        CommandSender sender = mock(CommandSender.class);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);

        new Reload(plugin, gameManager).onCommand(
                sender, mock(Command.class), "reloadinfected", new String[0]);

        verify(plugin, never()).reloadConfig();
        verify(sender).sendMessage(contains("only be reloaded in the lobby"));
    }

    @Test
    void reloadsConfigurationInTheLobby() {
        InfectedPlugin plugin = mock(InfectedPlugin.class);
        GameManager gameManager = mock(GameManager.class);
        CommandSender sender = mock(CommandSender.class);
        when(gameManager.getPhase()).thenReturn(RoundPhase.LOBBY);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        new Reload(plugin, gameManager).onCommand(
                sender, mock(Command.class), "reloadinfected", new String[0]);

        verify(plugin).reloadConfig();
    }
}
