package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantDamageListenerTest {
    private GameManager gameManager;
    private ParticipantDamageListener listener;
    private Player attacker;
    private Player victim;

    @BeforeEach
    void setUp() {
        gameManager = mock(GameManager.class);
        listener = new ParticipantDamageListener(gameManager);
        attacker = mock(Player.class);
        victim = mock(Player.class);
    }

    @Test
    void cancelsProjectileFriendlyFireBetweenSurvivors() {
        Projectile projectile = mock(Projectile.class);
        EntityDamageByEntityEvent event = event(projectile, EntityDamageEvent.DamageCause.PROJECTILE, false);
        when(projectile.getShooter()).thenReturn(attacker);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(attacker)).thenReturn(ParticipantRole.SURVIVOR);
        when(gameManager.roleOf(victim)).thenReturn(ParticipantRole.SURVIVOR);

        listener.onParticipantDamage(event);

        verify(event).setCancelled(true);
        verify(gameManager, never()).infectPlayer(victim, true);
    }

    @Test
    void cancelsInfectedMeleeDuringHeadStart() {
        EntityDamageByEntityEvent event = event(attacker, EntityDamageEvent.DamageCause.ENTITY_ATTACK, false);
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);
        when(gameManager.roleOf(attacker)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.roleOf(victim)).thenReturn(ParticipantRole.SURVIVOR);

        listener.onParticipantDamage(event);

        verify(event).setCancelled(true);
        verify(gameManager, never()).infectPlayer(victim, true);
    }

    @Test
    void cancelsInfectedProjectilesDuringActivePlay() {
        Projectile projectile = mock(Projectile.class);
        EntityDamageByEntityEvent event = event(projectile, EntityDamageEvent.DamageCause.PROJECTILE, false);
        when(projectile.getShooter()).thenReturn(attacker);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(attacker)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.roleOf(victim)).thenReturn(ParticipantRole.SURVIVOR);

        listener.onParticipantDamage(event);

        verify(event).setCancelled(true);
        verify(gameManager, never()).infectPlayer(victim, true);
    }

    @Test
    void infectsOnlyFromUncancelledActiveDirectMelee() {
        EntityDamageByEntityEvent event = event(attacker, EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, false);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(attacker)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.roleOf(victim)).thenReturn(ParticipantRole.SURVIVOR);

        listener.onParticipantDamage(event);

        verify(event, never()).setCancelled(true);
        verify(gameManager).infectPlayer(victim, true);
    }

    @Test
    void doesNotInfectFromAnAlreadyCancelledMeleeHit() {
        EntityDamageByEntityEvent event = event(attacker, EntityDamageEvent.DamageCause.ENTITY_ATTACK, true);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.roleOf(attacker)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.roleOf(victim)).thenReturn(ParticipantRole.SURVIVOR);

        listener.onParticipantDamage(event);

        verify(gameManager, never()).infectPlayer(victim, true);
    }

    private EntityDamageByEntityEvent event(
            org.bukkit.entity.Entity damager,
            EntityDamageEvent.DamageCause cause,
            boolean cancelled
    ) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(damager);
        when(event.getCause()).thenReturn(cause);
        when(event.isCancelled()).thenReturn(cancelled);
        return event;
    }
}
