package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;

public class FriendlyFireListener implements Listener {

    private final GameManager gameManager;

    public FriendlyFireListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker))
            return;

        boolean attackerInfected = gameManager.getInfected().stream().anyMatch(i -> i.getPlayer().equals(attacker));
        boolean victimInfected = gameManager.getInfected().stream().anyMatch(i -> i.getPlayer().equals(victim));

        boolean attackerSurvivor = gameManager.getSurvivors().stream().anyMatch(s -> s.getPlayer().equals(attacker));
        boolean victimSurvivor = gameManager.getSurvivors().stream().anyMatch(s -> s.getPlayer().equals(victim));

        // Prevent same-team damage
        if ((attackerInfected && victimInfected) || (attackerSurvivor && victimSurvivor)) {
            event.setCancelled(true);
        }
    }
}
