package me.DaWHeL.infected.Handlers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import me.DaWHeL.infected.GameManager;

public class HitHandler implements Listener {
    private final GameManager gameManager;

    public HitHandler(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        gameManager.handleHit(attacker, victim);
    }
}
