package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.DamageAttackerResolver;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.localization.DeathTitleMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfectedDeathListenerTest {

    @Test
    void activeInfectedDeathConsumesExactlyOneLifeDecision() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);

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
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.SURVIVOR);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(gameManager, never()).handleInfectedDeath(player);
    }

    @Test
    void finalInfectedDeathShowsOnlyThatPlayerTheConfiguredOutOfLivesSubtitle() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.handleInfectedDeath(player)).thenReturn(false);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(player).sendTitle("§c§lYOU DIED", "§4Your lives have run out!", 10, 60, 20);
    }

    @Test
    void finalTeamEliminationDoesNotOverwriteTheVictoryTitle() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE, RoundPhase.ENDING);
        when(gameManager.handleInfectedDeath(player)).thenReturn(false);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(player, never()).sendTitle(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void creditsResolvedSurvivorForAnInfectedDeath() {
        GameManager gameManager = mock(GameManager.class);
        Player deadInfected = mock(Player.class);
        Player survivor = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        DamageAttackerResolver resolver = mock(DamageAttackerResolver.class);
        when(event.getEntity()).thenReturn(deadInfected);
        when(deadInfected.getLastDamageCause()).thenReturn(damage);
        when(gameManager.roleOf(deadInfected)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(resolver.resolve(damage)).thenReturn(java.util.Optional.of(survivor));

        new InfectedDeathListener(gameManager, messages(), resolver).onPlayerDeath(event);

        verify(gameManager).recordSurvivorKill(survivor);
    }

    @Test
    void ignoresInfectedDeathsOutsideActivePlay() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.getPhase()).thenReturn(RoundPhase.HEADSTART);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);

        new InfectedDeathListener(gameManager, messages()).onPlayerDeath(event);

        verify(gameManager, never()).handleInfectedDeath(player);
        verify(event, never()).setDroppedExp(org.mockito.ArgumentMatchers.anyInt());
        verify(player, never()).sendTitle(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void usesDeathTitleMessagesReloadedAtRuntime() {
        GameManager gameManager = mock(GameManager.class);
        Player player = mock(Player.class);
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(gameManager.roleOf(player)).thenReturn(ParticipantRole.INFECTED);
        when(gameManager.getPhase()).thenReturn(RoundPhase.ACTIVE);
        when(gameManager.handleInfectedDeath(player)).thenReturn(true);
        InfectedDeathListener listener = new InfectedDeathListener(gameManager, messages());

        listener.reloadDeathTitles(new DeathTitleMessages(
                "&aRELOADED", "&bNew respawn text", "&4No lives", 1, 2, 3));
        listener.onPlayerDeath(event);

        verify(player).sendTitle("§aRELOADED", "§bNew respawn text", 1, 2, 3);
    }

    private static DeathTitleMessages messages() {
        return new DeathTitleMessages("&c&lYOU DIED", "&7Respawning in 3 seconds...",
                "&4Your lives have run out!", 10, 60, 20);
    }
}
