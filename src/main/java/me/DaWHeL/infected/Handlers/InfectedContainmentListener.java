package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

public final class InfectedContainmentListener implements Listener {
    private final GameManager gameManager;

    public InfectedContainmentListener(GameManager gameManager) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainedInfectedMove(PlayerMoveEvent event) {
        if (!gameManager.isContainedInfected(event.getPlayer())
                || gameManager.isRoundTeleportBypass(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) {
            return;
        }

        event.setTo(new Location(
                from.getWorld(),
                from.getX(),
                from.getY(),
                from.getZ(),
                to.getYaw(),
                to.getPitch()
        ));
    }

    private static boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
