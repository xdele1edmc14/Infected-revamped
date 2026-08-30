package me.DaWHeL.infected.gui;

import me.DaWHeL.infected.SpawnRole;
import me.DaWHeL.infected.admin.AdminActionService;
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
    private final AdminActionService actions;
    private final AdminSetupService setupService;
    private final AdminGuiNavigator navigator;

    public InfectedAdminCommand(
            AdminActionService actions,
            AdminSetupService setupService,
            AdminGuiNavigator navigator
    ) {
        this.actions = Objects.requireNonNull(actions, "actions");
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
        if (!sender.hasPermission(AdminActionService.ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use the Infected admin controls.");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                navigator.openMain(player);
            } else {
                actions.status(sender);
                actions.help(sender);
            }
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                actions.start(sender);
                return true;
            }
            case "stop" -> {
                actions.stop(sender);
                return true;
            }
            case "reload" -> {
                actions.reload(sender);
                return true;
            }
            case "status" -> {
                actions.status(sender);
                return true;
            }
            case "help" -> {
                actions.help(sender);
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player player)) {
            actions.help(sender);
            return true;
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")) {
            return addTeleportPoint(player, SpawnRole.SURVIVOR, args[2], true);
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")) {
            return SpawnRole.fromCommandKey(args[2])
                    .map(role -> addTeleportPoint(player, role, args[3], false))
                    .orElseGet(() -> {
                        player.sendMessage(ChatColor.YELLOW
                                + "Usage: /infected gui addteleport <survivor|release|respawn> <name>");
                        return true;
                    });
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /infected <start|stop|reload|status|help>"
                + " or /infected gui addteleport [survivor|release|respawn] <name>");
        return true;
    }

    private boolean addTeleportPoint(Player player, SpawnRole role, String name, boolean survivorShorthand) {
        if (!actions.setupChangesAllowed()) {
            player.sendMessage(ChatColor.RED
                    + "Spawn setup can only be changed while the event is in the lobby.");
            return true;
        }
        try {
            AdminSetupService.validatePointName(name);
            if (survivorShorthand) {
                setupService.saveTeleportPoint(name, player.getLocation());
            } else {
                setupService.saveTeleportPoint(role, name, player.getLocation());
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + exception.getMessage());
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Saved " + role.displayName().toLowerCase(Locale.ROOT)
                + " spawn '" + name + "'. No arena blocks were changed.");
        if (survivorShorthand) {
            navigator.openTeleportPoints(player, 0);
        } else {
            navigator.openTeleportPoints(player, role, 0);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission(AdminActionService.ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("start", "stop", "reload", "status", "help", "gui").stream()
                    .filter(candidate -> candidate.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) {
            return matching("addteleport", args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")) {
            return List.of("survivor", "release", "respawn").stream()
                    .filter(candidate -> candidate.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("gui")
                && args[1].equalsIgnoreCase("addteleport")
                && SpawnRole.fromCommandKey(args[2]).isPresent()) {
            return List.of("<name>");
        }
        return List.of();
    }

    private static List<String> matching(String candidate, String input) {
        return candidate.startsWith(input.toLowerCase(Locale.ROOT)) ? List.of(candidate) : List.of();
    }
}
