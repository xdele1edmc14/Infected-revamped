package me.DaWHeL.infected.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import me.DaWHeL.infected.admin.AdminActionService;

public class Reload implements CommandExecutor {

    private final AdminActionService actions;

    public Reload(AdminActionService actions) {
        this.actions = actions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        actions.reload(sender);
        return true;
    }
}
