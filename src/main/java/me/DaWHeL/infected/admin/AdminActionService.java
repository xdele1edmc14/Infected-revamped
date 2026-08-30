package me.DaWHeL.infected.admin;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.StartResult;
import me.DaWHeL.infected.gui.AdminSetupService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class AdminActionService {
    public static final String ADMIN_PERMISSION = "infected.admin";

    private final InfectedPlugin plugin;
    private final GameManager gameManager;
    private final AdminSetupService setupService;
    private final Runnable setupSessionCanceller;

    public AdminActionService(
            InfectedPlugin plugin,
            GameManager gameManager,
            AdminSetupService setupService,
            Runnable setupSessionCanceller
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.setupService = Objects.requireNonNull(setupService, "setupService");
        this.setupSessionCanceller = Objects.requireNonNull(setupSessionCanceller, "setupSessionCanceller");
    }

    public boolean start(CommandSender sender) {
        if (!allowed(sender)) {
            return false;
        }
        StartResult result = gameManager.startGame();
        if (!result.success()) {
            result.errors().forEach(error -> sender.sendMessage(ChatColor.RED + error));
            return false;
        }
        setupSessionCanceller.run();
        sender.sendMessage(ChatColor.GREEN + "Infected round countdown started.");
        return true;
    }

    public boolean stop(CommandSender sender) {
        if (!allowed(sender)) {
            return false;
        }
        RoundPhase phase = gameManager.getPhase();
        if (!phase.allowsAdminStop()) {
            sender.sendMessage(phase == RoundPhase.ENDING
                    ? ChatColor.YELLOW + "The Infected game is already cleaning up."
                    : ChatColor.YELLOW + "No Infected game is running.");
            return false;
        }
        if (!gameManager.stopGame()) {
            sender.sendMessage(ChatColor.RED
                    + "The Infected game could not be stopped because its phase changed.");
            return false;
        }
        sender.sendMessage(ChatColor.GREEN + "Infected game has been stopped.");
        return true;
    }

    public boolean reload(CommandSender sender) {
        if (!allowed(sender)) {
            return false;
        }
        if (!gameManager.getPhase().allowsConfigReload()) {
            sender.sendMessage(ChatColor.RED
                    + "Infected configuration can only be reloaded in the lobby.");
            return false;
        }
        setupSessionCanceller.run();
        plugin.reloadConfig();
        String message = plugin.getConfig().getString(
                "messages.config-reloaded",
                "&aInfected plugin configuration reloaded!"
        );
        sender.sendMessage(color(message));
        return true;
    }

    public boolean status(CommandSender sender) {
        if (!allowed(sender)) {
            return false;
        }
        int survivors = gameManager.getSurvivors().size();
        int infected = gameManager.getInfected().size();
        AdminSetupService.SetupSnapshot snapshot = setupService.snapshot(survivors, infected);
        sender.sendMessage(ChatColor.GOLD + "Infected Event Status");
        sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.YELLOW + gameManager.getPhase().name());
        sender.sendMessage(ChatColor.GRAY + "Survivors: " + survivors + " | Infected: " + infected);
        sender.sendMessage(ChatColor.GRAY + "Setup: "
                + (snapshot.ready() ? ChatColor.GREEN + "Ready" : ChatColor.RED + "Incomplete"));
        return true;
    }

    public boolean setupChangesAllowed() {
        return gameManager.getPhase() == RoundPhase.LOBBY;
    }

    public boolean help(CommandSender sender) {
        if (!allowed(sender)) {
            return false;
        }
        sender.sendMessage(ChatColor.GOLD + "---------- " + ChatColor.RED
                + "Infected Admin Help" + ChatColor.GOLD + " ----------");
        sender.sendMessage(ChatColor.YELLOW + "/infected start" + ChatColor.GRAY
                + " or /startinfected - start the round countdown");
        sender.sendMessage(ChatColor.YELLOW + "/infected stop" + ChatColor.GRAY
                + " or /stopinfected - stop and clean up the round");
        sender.sendMessage(ChatColor.YELLOW + "/infected reload" + ChatColor.GRAY
                + " or /reloadinfected - reload configuration in the lobby");
        sender.sendMessage(ChatColor.YELLOW + "/infected status" + ChatColor.GRAY
                + " - show phase, teams, and setup readiness");
        sender.sendMessage(ChatColor.YELLOW + "/infected help" + ChatColor.GRAY
                + " or /helpinfected - show this help");
        sender.sendMessage(ChatColor.GRAY
                + "Use /infected with no arguments in-game to open the admin GUI.");
        return true;
    }

    private boolean allowed(CommandSender sender) {
        Objects.requireNonNull(sender, "sender");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED
                + "You do not have permission to use the Infected admin controls.");
        return false;
    }

    private static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
