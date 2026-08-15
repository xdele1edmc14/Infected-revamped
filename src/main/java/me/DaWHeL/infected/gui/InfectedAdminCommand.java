package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class InfectedAdminCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "infected.admin";

    private final GameManager gameManager;
    private final AdminSetupService setupService;
    private final AdminGuiNavigator navigator;

    public InfectedAdminCommand(
            GameManager gameManager,
            AdminSetupService setupService,
            AdminGuiNavigator navigator
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.setupService = Objects.requireNonNull(setupService, "setupService");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            showConsoleStatus(sender);
            return true;
        }

        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use the Infected admin controls.");
            return true;
        }

        if (args.length == 0) {
            navigator.openMain(player);
            return true;
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")) {
            return addTeleportPoint(player, args[2]);
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /infected gui addteleport <name>");
        return true;
    }

    private boolean addTeleportPoint(Player player, String name) {
        try {
            AdminSetupService.validatePointName(name);
            setupService.saveTeleportPoint(name, player.getLocation());
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Saved teleport point '" + name + "'. No arena blocks were changed.");
        navigator.openTeleportPoints(player, 0);
        return true;
    }

    private void showConsoleStatus(CommandSender sender) {
        int survivors = gameManager.getSurvivors().size();
        int infected = gameManager.getInfected().size();
        AdminSetupService.SetupSnapshot snapshot = setupService.snapshot(survivors, infected);

        sender.sendMessage(ChatColor.GOLD + "Infected Event Control");
        sender.sendMessage(ChatColor.GRAY + "State: "
                + (gameManager.isGameRunning() ? ChatColor.GREEN + "Running" : ChatColor.YELLOW + "Stopped"));
        sender.sendMessage(ChatColor.GRAY + "Survivors: " + survivors + " | Infected: " + infected);
        sender.sendMessage(ChatColor.GRAY + "Setup: "
                + (snapshot.ready() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete"));
        sender.sendMessage(ChatColor.AQUA + "/infected" + ChatColor.GRAY
                + " opens the control desk in-game. Add points with /infected gui addteleport <name>.");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (sender instanceof Player player && !player.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching("gui", args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            return matching("addteleport", args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")) {
            return List.of("<name>");
        }
        return List.of();
    }

    private static List<String> matching(String candidate, String input) {
        return candidate.startsWith(input.toLowerCase(Locale.ROOT)) ? List.of(candidate) : List.of();
    }
}
