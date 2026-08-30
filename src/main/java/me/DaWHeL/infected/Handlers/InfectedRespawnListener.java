package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedRespawnSelector;
import me.DaWHeL.infected.InfectedRoleEquipment;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class InfectedRespawnListener implements Listener {
    private final GameManager gameManager;
    private final SpawnRepository spawnRepository;
    private final Random random;
    private final InfectedRoleEquipment roleEquipment;

    public InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository) {
        this(gameManager, spawnRepository, new Random(),
                new InfectedRoleEquipment(gameManager.getPlugin()));
    }

    InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository, Random random) {
        this(gameManager, spawnRepository, random,
                new InfectedRoleEquipment(gameManager.getPlugin()));
    }

    public InfectedRespawnListener(
            GameManager gameManager,
            SpawnRepository spawnRepository,
            Random random,
            InfectedRoleEquipment roleEquipment
    ) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.random = Objects.requireNonNull(random, "random");
        this.roleEquipment = Objects.requireNonNull(roleEquipment, "roleEquipment");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<Location> restored = gameManager.restoreAfterRoundRespawn(player);
        if (restored.isPresent()) {
            event.setRespawnLocation(restored.get());
            return;
        }
        RoundPhase phase = gameManager.getPhase();
        if ((phase == RoundPhase.DEPLOYING || phase == RoundPhase.HEADSTART)
                && gameManager.roleOf(player) == ParticipantRole.INFECTED
                && gameManager.claimHoldingRespawn(player)) {
            Optional<Location> holding = spawnRepository.loadedHoldingSpawn();
            if (holding.isEmpty()) {
                gameManager.stopGame();
                gameManager.restoreAfterRoundRespawn(player).ifPresent(event::setRespawnLocation);
                return;
            }
            event.setRespawnLocation(holding.get());
            gameManager.scheduleRoundLater(
                    player, phase, 1L, () -> applyLoadoutIfStillInfected(player));
            return;
        }
        if ((phase == RoundPhase.ACTIVE || phase == RoundPhase.ENDING)
                && gameManager.isEliminatedInfected(player)) {
            if (!gameManager.claimInfectedRespawn(player)) {
                return;
            }
            if (phase == RoundPhase.ACTIVE) {
                Optional<Location> configured = selectConfiguredRespawn();
                if (configured.isEmpty()) {
                    cancelForMissingRespawn(event, player);
                    return;
                }
                event.setRespawnLocation(configured.get());
            }
            Runnable spectator = () -> {
                if (gameManager.isEliminatedInfected(player)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            };
            if (phase == RoundPhase.ENDING) {
                gameManager.scheduleCleanupLater(player, 1L, spectator);
            } else {
                gameManager.scheduleRoundLater(player, RoundPhase.ACTIVE, 1L, spectator);
            }
            return;
        }
        if (phase != RoundPhase.ACTIVE) {
            return;
        }
        if (gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }
        if (!gameManager.claimInfectedRespawn(player)) {
            return;
        }

        Optional<Location> configured = selectConfiguredRespawn();
        if (configured.isEmpty()) {
            cancelForMissingRespawn(event, player);
            return;
        }

        event.setRespawnLocation(configured.get());
        player.sendMessage(ChatColor.GRAY + "You respawned as an infected!");

        gameManager.scheduleRoundLater(
                player, RoundPhase.ACTIVE, 1L, () -> applyLoadoutIfStillInfected(player));
    }

    private void applyLoadoutIfStillInfected(Player player) {
        RoundPhase phase = gameManager.getPhase();
        if ((phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING)
                || gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setHelmet(roleEquipment.createHead());
        gameManager.applyCurrentInfectedBuff(player);
    }

    private Optional<Location> selectConfiguredRespawn() {
        return InfectedRespawnSelector.select(
                spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN), random);
    }

    private void cancelForMissingRespawn(PlayerRespawnEvent event, Player player) {
        player.sendMessage(ChatColor.RED
                + "No safe infected respawn is available. The round is being cancelled.");
        gameManager.cancelForUnsafeInfectedRespawn();
        gameManager.restoreAfterRoundRespawn(player).ifPresent(event::setRespawnLocation);
    }
}
