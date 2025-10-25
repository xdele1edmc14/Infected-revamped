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
        // Get the dead player
        var player = event.getEntity();

        // Check if the player is infected
        boolean isInfected = gameManager.getInfected().stream()
                .anyMatch(i -> i.getPlayer().equals(player));

        if (!isInfected) return;

        // ✅ Clear all dropped items
        event.getDrops().clear();

        // (Optional) remove XP drop too
        event.setDroppedExp(0);
    }
}
