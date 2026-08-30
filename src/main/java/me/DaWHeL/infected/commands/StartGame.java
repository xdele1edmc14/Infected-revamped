package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class StartGame implements CommandExecutor {
    private final AdminActionService actions;

    public StartGame(AdminActionService actions) {
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        actions.start(sender);
        return true;
    }
}
