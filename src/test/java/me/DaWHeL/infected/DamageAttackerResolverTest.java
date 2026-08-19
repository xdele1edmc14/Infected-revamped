package me.DaWHeL.infected;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DamageAttackerResolverTest {
    private final DamageAttackerResolver resolver = new DamageAttackerResolver();

    @Test
    void resolvesADirectPlayerBeforeDamageSourceFallbacks() {
        Player direct = mock(Player.class);
        Player fallback = mock(Player.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        DamageSource source = mock(DamageSource.class);
        when(event.getDamager()).thenReturn(direct);
        when(event.getDamageSource()).thenReturn(source);
        when(source.getCausingEntity()).thenReturn(fallback);

        assertEquals(direct, resolver.resolve(event).orElseThrow());
    }

    @Test
    void resolvesAProjectileShooter() {
        Player shooter = mock(Player.class);
        Projectile projectile = mock(Projectile.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(projectile.getShooter()).thenReturn(shooter);
        when(event.getDamager()).thenReturn(projectile);

        assertEquals(shooter, resolver.resolve(event).orElseThrow());
    }

    @Test
    void resolvesPaperCausingEntityAndOwnedIndirectEntities() {
        Player owner = mock(Player.class);
        TNTPrimed tnt = mock(TNTPrimed.class);
        DamageSource source = mock(DamageSource.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(tnt.getSource()).thenReturn(owner);
        when(source.getCausingEntity()).thenReturn(tnt);
        when(event.getDamageSource()).thenReturn(source);

        assertEquals(owner, resolver.resolve(event).orElseThrow());
    }

    @Test
    void resolvesPaperDirectEntityAreaCloudSource() {
        Player owner = mock(Player.class);
        AreaEffectCloud cloud = mock(AreaEffectCloud.class);
        DamageSource source = mock(DamageSource.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(cloud.getSource()).thenReturn(owner);
        when(source.getDirectEntity()).thenReturn(cloud);
        when(event.getDamageSource()).thenReturn(source);

        assertEquals(owner, resolver.resolve(event).orElseThrow());
    }

    @Test
    void resolvesEvokerFangOwners() {
        Player owner = mock(Player.class);
        EvokerFangs fangs = mock(EvokerFangs.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(fangs.getOwner()).thenReturn(owner);
        when(event.getDamager()).thenReturn(fangs);

        assertEquals(owner, resolver.resolve(event).orElseThrow());
    }

    @Test
    void stopsAtCyclicOrUnattributableSources() {
        TNTPrimed tnt = mock(TNTPrimed.class);
        DamageSource source = mock(DamageSource.class);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(tnt.getSource()).thenReturn(tnt);
        when(source.getCausingEntity()).thenReturn(tnt);
        when(source.getDirectEntity()).thenReturn(tnt);
        when(event.getDamageSource()).thenReturn(source);

        assertTrue(resolver.resolve(event).isEmpty());
    }
}
