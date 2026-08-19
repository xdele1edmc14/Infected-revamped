package me.DaWHeL.infected;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

public final class DamageAttackerResolver {

    public Optional<Player> resolve(EntityDamageEvent event) {
        Set<Entity> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Optional<Player> damager = resolveEntity(byEntity.getDamager(), visited);
            if (damager.isPresent()) {
                return damager;
            }
        }

        DamageSource source = event.getDamageSource();
        if (source == null) {
            return Optional.empty();
        }
        Optional<Player> causing = resolveEntity(source.getCausingEntity(), visited);
        if (causing.isPresent()) {
            return causing;
        }
        return resolveEntity(source.getDirectEntity(), visited);
    }

    private Optional<Player> resolveEntity(Entity entity, Set<Entity> visited) {
        if (entity == null) {
            return Optional.empty();
        }
        if (entity instanceof Player player) {
            return Optional.of(player);
        }
        if (!visited.add(entity)) {
            return Optional.empty();
        }

        if (entity instanceof Projectile projectile) {
            return resolveProjectileSource(projectile.getShooter(), visited);
        }
        if (entity instanceof TNTPrimed tnt) {
            return resolveEntity(tnt.getSource(), visited);
        }
        if (entity instanceof AreaEffectCloud cloud) {
            return resolveProjectileSource(cloud.getSource(), visited);
        }
        if (entity instanceof EvokerFangs fangs) {
            return resolveEntity(fangs.getOwner(), visited);
        }
        return Optional.empty();
    }

    private Optional<Player> resolveProjectileSource(ProjectileSource source, Set<Entity> visited) {
        if (source instanceof Player player) {
            return Optional.of(player);
        }
        if (source instanceof Entity entity) {
            return resolveEntity(entity, visited);
        }
        return Optional.empty();
    }
}
