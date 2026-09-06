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
    private static final long RESPAWN_COOLDOWN_TICKS = 60L;
    private final GameManager gameManager;
    private final SpawnRepository spawnRepository;
    private final Random random;
    private final RespawnPotionEffects potionEffects;

    public InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository) {
        this(gameManager, spawnRepository, new Random(), new BukkitRespawnPotionEffects());
    }

    InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository, Random random) {
        this(gameManager, spawnRepository, random, new BukkitRespawnPotionEffects());
    }

    InfectedRespawnListener(GameManager gameManager, SpawnRepository spawnRepository, Random random,
                            RespawnPotionEffects potionEffects) {
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.random = Objects.requireNonNull(random, "random");
        this.potionEffects = Objects.requireNonNull(potionEffects, "potionEffects");
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

        Optional<Location> holdingSpawn = spawnRepository.loadedHoldingSpawn();
        Optional<Location> configured = InfectedRespawnSelector.select(
                spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (holdingSpawn.isEmpty() || configured.isEmpty()) {
            player.sendMessage(ChatColor.RED
                    + "No safe infected cage or respawn is available. The round is being cancelled.");
            gameManager.cancelForUnsafeInfectedRespawn();
            return;
        }

        event.setRespawnLocation(holdingSpawn.get());
        potionEffects.applyBlindness(player, (int) RESPAWN_COOLDOWN_TICKS);

        long roundId = gameManager.currentRoundId();
        gameManager.getPlugin().getServer().getScheduler().runTaskLater(
                gameManager.getPlugin(),
                () -> releaseIfStillActive(player, roundId, configured.get()),
                RESPAWN_COOLDOWN_TICKS
        );
    }

    private void releaseIfStillActive(Player player, long roundId, Location respawn) {
        if (!player.isOnline()) {
            return;
        }
        potionEffects.removeBlindness(player);
        if (gameManager.currentRoundId() != roundId
                || gameManager.getPhase() != RoundPhase.ACTIVE
                || gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }
        if (!gameManager.teleportInfectedToRespawn(player, respawn)) {
            player.sendMessage(ChatColor.RED
                    + "Your infected respawn was cancelled. The round is being cancelled.");
            gameManager.cancelForUnsafeInfectedRespawn();
            return;
        }

        potionEffects.applyInfectedLoadout(player, gameManager.isBuffEnabled());
    }

    interface RespawnPotionEffects {
        void applyBlindness(Player player, int durationTicks);

        void removeBlindness(Player player);

        void applyInfectedLoadout(Player player, boolean buffEnabled);
    }

    private static final class BukkitRespawnPotionEffects implements RespawnPotionEffects {
        @Override
        public void applyBlindness(Player player, int durationTicks) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, durationTicks, 0, false, false, false));
        }

        @Override
        public void removeBlindness(Player player) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        @Override
        public void applyInfectedLoadout(Player player, boolean buffEnabled) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setHelmet(new ItemStack(Material.ZOMBIE_HEAD));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, Integer.MAX_VALUE, buffEnabled ? 1 : 0, false, false, true));
            if (buffEnabled) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false, true));
                player.getInventory().addItem(new ItemStack(Material.COMPASS));
            }
        }
    }
}
