package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final GameManager gameManager;

    public PlayerQuitListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove from survivors if present
        gameManager.getSurvivors().removeIf(s -> s.getPlayer().equals(player));

        // Remove from infected if present
        gameManager.getInfected().removeIf(i -> i.getPlayer().equals(player));
        gameManager.checkWin();
    }
}
