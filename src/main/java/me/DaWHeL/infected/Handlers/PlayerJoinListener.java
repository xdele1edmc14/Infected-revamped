package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

public final class PlayerJoinListener implements Listener {
    private final GameManager gameManager;

    public PlayerJoinListener(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RoundPhase phase = gameManager.getPhase();
        if (phase == RoundPhase.LOBBY) {
            gameManager.registerLobbySurvivor(player);
            player.sendMessage(ChatColor.GREEN + "You joined as a survivor!");
            return;
        }
        if (phase.queuesLateJoins()) {
            if (!gameManager.queueLateJoin(player)) {
                player.sendMessage(ChatColor.RED
                        + "You could not be safely queued for the current Infected round. "
                        + "Please contact an administrator.");
            }
            return;
        }
        player.sendMessage(ChatColor.YELLOW
                + "The Infected round is resetting. You will join the lobby shortly.");
    }
}
