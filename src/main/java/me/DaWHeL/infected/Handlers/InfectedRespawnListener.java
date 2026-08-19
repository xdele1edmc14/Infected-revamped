package me.DaWHeL.infected.Handlers;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.InfectedRespawnSelector;
import me.DaWHeL.infected.ParticipantRole;
import me.DaWHeL.infected.RoundPhase;
import me.DaWHeL.infected.SpawnRepository;
import me.DaWHeL.infected.SpawnRole;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public final class InfectedRespawnListener implements Listener {
    private final GameManager gameManager;
    private final SpawnRepository spawnRepository;
    private final Random random;

    public InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository) {
        this(gameManager, spawnRepository, new Random());
    }

    InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository, Random random) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.random = Objects.requireNonNull(random, "random");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        RoundPhase phase = gameManager.getPhase();
        if ((phase == RoundPhase.ACTIVE || phase == RoundPhase.ENDING)
                && gameManager.isEliminatedInfected(player)) {
            gameManager.getPlugin().getServer().getScheduler().runTask(
                    gameManager.getPlugin(),
                    () -> {
                        RoundPhase currentPhase = gameManager.getPhase();
                        if ((currentPhase == RoundPhase.ACTIVE || currentPhase == RoundPhase.ENDING)
                                && gameManager.isEliminatedInfected(player)) {
                            player.setGameMode(GameMode.SPECTATOR);
                        }
                    }
            );
            return;
        }
        if (phase != RoundPhase.ACTIVE) {
            return;
        }
        if (gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }

        Optional<Location> configured = InfectedRespawnSelector.select(
                spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (configured.isEmpty()) {
            player.sendMessage(ChatColor.RED
                    + "No safe infected respawn is available. The round is being cancelled.");
            gameManager.cancelForUnsafeInfectedRespawn();
            return;
        }

        event.setRespawnLocation(configured.get());
        player.sendMessage(ChatColor.GRAY + "You respawned as an infected!");

        long roundId = gameManager.currentRoundId();
        gameManager.getPlugin().getServer().getScheduler().runTaskLater(
                gameManager.getPlugin(),
                () -> applyLoadoutIfStillActive(player, roundId),
                1L
        );
    }

    private void applyLoadoutIfStillActive(Player player, long roundId) {
        if (gameManager.currentRoundId() != roundId
                || gameManager.getPhase() != RoundPhase.ACTIVE
                || gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setHelmet(new ItemStack(Material.ZOMBIE_HEAD));
        if (gameManager.isBuffEnabled()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false, true));
            player.getInventory().addItem(new ItemStack(Material.COMPASS));
        } else {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, true));
        }
    }
}
