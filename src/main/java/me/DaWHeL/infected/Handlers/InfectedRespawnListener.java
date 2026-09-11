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
        Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.random = Objects.requireNonNull(random, "random");
        this.potionEffects = Objects.requireNonNull(potionEffects, "potionEffects");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gameManager.isCleanupRespawnPending(player)) {
            event.setRespawnLocation(player.getWorld().getSpawnLocation());
            gameManager.getPlugin().getServer().getScheduler().runTask(
                    gameManager.getPlugin(), () -> gameManager.completeCleanupRespawn(player));
            return;
        }
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
        ParticipantRole role = gameManager.roleOf(player);
        if (role == ParticipantRole.SURVIVOR) {
            java.util.List<Location> survivorSpawns = gameManager.roundSpawnLocations(SpawnRole.SURVIVOR);
            if (survivorSpawns.isEmpty()) {
                player.sendMessage(ChatColor.RED
                        + "No survivor respawn is available. The round is being cancelled.");
                gameManager.cancelForUnavailableInfectedRespawn();
                return;
            }
            event.setRespawnLocation(survivorSpawns.get(random.nextInt(survivorSpawns.size())));
            return;
        }
        if (role != ParticipantRole.INFECTED) {
            return;
        }

        Optional<Location> holdingSpawn = gameManager.roundHoldingSpawn();
        Optional<Location> configured = InfectedRespawnSelector.select(
                gameManager.roundSpawnLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (holdingSpawn.isEmpty() || configured.isEmpty()) {
            player.sendMessage(ChatColor.RED
                    + "The infected holding spawn or respawn is unavailable. The round is being cancelled.");
            gameManager.cancelForUnavailableInfectedRespawn();
            return;
        }

        event.setRespawnLocation(holdingSpawn.get());
        gameManager.containInfectedForRespawn(player);
        potionEffects.applyBlindness(player, (int) RESPAWN_COOLDOWN_TICKS);

        long roundId = gameManager.currentRoundId();
        gameManager.scheduleRoundTask(
                () -> releaseIfStillActive(player, roundId), RESPAWN_COOLDOWN_TICKS);
    }

    private void releaseIfStillActive(Player player, long roundId) {
        if (!player.isOnline()) {
            return;
        }
        if (gameManager.currentRoundId() != roundId
                || gameManager.getPhase() != RoundPhase.ACTIVE
                || gameManager.roleOf(player) != ParticipantRole.INFECTED) {
            return;
        }
        potionEffects.removeBlindness(player);
        Optional<Location> respawn = InfectedRespawnSelector.select(
                gameManager.roundSpawnLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (respawn.isEmpty()) {
            player.sendMessage(ChatColor.RED
                    + "No infected respawn is available. The round is being cancelled.");
            gameManager.cancelForUnavailableInfectedRespawn();
            return;
        }
        if (!gameManager.teleportInfectedToRespawn(player, respawn.get())) {
            player.sendMessage(ChatColor.RED
                    + "Your infected respawn was cancelled. The round is being cancelled.");
            gameManager.cancelForUnavailableInfectedRespawn();
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
            }
        }
    }
}
