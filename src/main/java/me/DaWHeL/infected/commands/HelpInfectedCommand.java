package me.DaWHeL.infected.commands;

import me.DaWHeL.infected.admin.AdminActionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpInfectedCommand implements CommandExecutor {
    private final AdminActionService actions;

    public HelpInfectedCommand(AdminActionService actions) {
        this.actions = actions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        actions.help(sender);
        return true;
    }
}
