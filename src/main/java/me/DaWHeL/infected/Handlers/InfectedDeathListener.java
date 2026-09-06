package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.DamageAttackerResolver;
import me.DaWHeL.infected.localization.DeathTitleMessages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class InfectedDeathListener implements Listener {

    private final GameManager gameManager;
    private final DeathTitleMessages deathTitles;
    private final DamageAttackerResolver attackerResolver;

    public InfectedDeathListener(GameManager gameManager, DeathTitleMessages deathTitles) {
        this(gameManager, deathTitles, new DamageAttackerResolver());
    }

    InfectedDeathListener(
            GameManager gameManager,
            DeathTitleMessages deathTitles,
            DamageAttackerResolver attackerResolver
    ) {
        this.gameManager = gameManager;
        this.deathTitles = deathTitles;
        this.attackerResolver = attackerResolver;
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

        var lastDamage = player.getLastDamageCause();
        if (lastDamage != null) {
            attackerResolver.resolve(lastDamage).ifPresent(gameManager::recordSurvivorKill);
        }
        boolean hasRemainingLife = gameManager.handleInfectedDeath(player);
        deathTitles.show(player, hasRemainingLife);
    }
}
