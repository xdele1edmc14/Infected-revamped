package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.DamageAttackerResolver;
import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.localization.DeathTitleMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedDeathListenerTest {

    @Test
    void activeInfectedDeathConsumesExactlyOneLifeDecision() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        Infected infected = mock(Infected.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(infected.getPlayer()).thenReturn(player);
        when(gameManager.getInfected()).thenReturn(List.of(infected));

        when(gameManager.handleInfectedDeath(player)).thenReturn(true);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(gameManager).handleInfectedDeath(player);
        verify(event).setDroppedExp(0);
        verify(player).sendTitle("§c§lYOU DIED", "§7Respawning in 3 seconds...", 10, 60, 20);
    }

    @Test
    void nonInfectedDeathDoesNotTouchFiniteLives() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.getInfected()).thenReturn(List.of());

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(gameManager, never()).handleInfectedDeath(player);
    }

    @Test
    void finalInfectedDeathShowsOnlyThatPlayerTheConfiguredOutOfLivesSubtitle() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        Infected infected = mock(Infected.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(infected.getPlayer()).thenReturn(player);
        when(gameManager.getInfected()).thenReturn(List.of(infected));
        when(gameManager.handleInfectedDeath(player)).thenReturn(false);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(player).sendTitle("§c§lYOU DIED", "§4Your lives have run out!", 10, 60, 20);
    }

    @Test
    void creditsResolvedSurvivorForAnInfectedDeath() {
        GameManager gameManager = mock(GameManager.class);
        Player deadInfected = mock(Player.class);
        Player survivor = mock(Player.class);
        Infected infected = mock(Infected.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        DamageAttackerResolver resolver = mock(DamageAttackerResolver.class);
        when(event.getEntity()).thenReturn(deadInfected);
        when(deadInfected.getLastDamageCause()).thenReturn(damage);
        when(infected.getPlayer()).thenReturn(deadInfected);
        when(gameManager.getInfected()).thenReturn(List.of(infected));
        when(resolver.resolve(damage)).thenReturn(java.util.Optional.of(survivor));

        new InfectedDeathListener(gameManager, messages(), resolver).onPlayerDeath(event);

        verify(gameManager).recordSurvivorKill(survivor);
    }

    private static DeathTitleMessages messages() {
        return new DeathTitleMessages("&c&lYOU DIED", "&7Respawning in 3 seconds...",
                "&4Your lives have run out!", 10, 60, 20);
    }
}
