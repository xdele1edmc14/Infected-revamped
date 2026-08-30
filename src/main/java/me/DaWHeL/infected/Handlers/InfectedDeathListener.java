package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class InfectedDeathListener implements Listener {

    private final GameManager gameManager;

    public InfectedDeathListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        if (!gameManager.handlePlayerDeath(player)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }
}
