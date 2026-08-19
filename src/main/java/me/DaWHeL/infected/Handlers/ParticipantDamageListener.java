package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.CombatPolicy;
import me.DaWHeL.infected.DamageAttackerResolver;
import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.ParticipantRole;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Objects;
import java.util.Optional;

public final class ParticipantDamageListener implements Listener {
    private final GameManager gameManager;
    private final DamageAttackerResolver attackerResolver;
    private final CombatPolicy combatPolicy;

    public ParticipantDamageListener(GameManager gameManager) {
        this(gameManager, new DamageAttackerResolver(), new CombatPolicy());
    }

    ParticipantDamageListener(
            GameManager gameManager,
            DamageAttackerResolver attackerResolver,
            CombatPolicy combatPolicy
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.attackerResolver = Objects.requireNonNull(attackerResolver, "attackerResolver");
        this.combatPolicy = Objects.requireNonNull(combatPolicy, "combatPolicy");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onParticipantDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Optional<Player> resolved = attackerResolver.resolve(event);
        if (resolved.isEmpty()) {
            return;
        }
        Player attacker = resolved.get();
        ParticipantRole attackerRole = gameManager.roleOf(attacker);
        ParticipantRole victimRole = gameManager.roleOf(victim);
        boolean directPlayerMelee = isDirectPlayerMelee(event, attacker);

        CombatPolicy.Decision decision = combatPolicy.decide(
                gameManager.getPhase(),
                attackerRole,
                victimRole,
                directPlayerMelee,
                event.isCancelled()
        );
        if (decision.cancelDamage()) {
            event.setCancelled(true);
            return;
        }
        if (decision.infectVictim()) {
            gameManager.infectPlayer(victim, true);
        }
    }

    private static boolean isDirectPlayerMelee(EntityDamageEvent event, Player attacker) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity) || byEntity.getDamager() != attacker) {
            return false;
        }
        return event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }
}
