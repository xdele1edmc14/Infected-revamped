package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.DamageAttackerResolver;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.localization.DeathTitleMessages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Objects;

public class InfectedDeathListener implements Listener {

    private final GameManager gameManager;
    private DeathTitleMessages deathTitles;
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
        this.deathTitles = Objects.requireNonNull(deathTitles, "deathTitles");
        this.attackerResolver = attackerResolver;
    }

    public void reloadDeathTitles(DeathTitleMessages deathTitles) {
        this.deathTitles = Objects.requireNonNull(deathTitles, "deathTitles");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Get the dead player
        var player = event.getEntity();

        if (gameManager.getPhase() != RoundPhase.ACTIVE) return;

        // Check if the player is infected
        boolean isInfected = gameManager.roleOf(player) == ParticipantRole.INFECTED;

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
        if (gameManager.getPhase() != RoundPhase.ACTIVE) return;
        deathTitles.show(player, hasRemainingLife);
    }
}
