package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.*;

public class GameManager {
    private final InfectedPlugin plugin;
    private final List<Survivor> survivors = new ArrayList<>();
    private final List<Infected> infected = new ArrayList<>();
    private final  ScoreboardManager scoreboardManager;
    private boolean gameRunning = false;

    private boolean buffEnabled = false;

    public GameManager(InfectedPlugin plugin) {
        this.plugin = plugin;
        this.scoreboardManager = new ScoreboardManager(plugin, this);
    }
    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    public void setBuffEnabled(boolean buffEnabled) {
        this.buffEnabled = buffEnabled;
    }


    public InfectedPlugin getPlugin() {
        return plugin;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }
    public List<Survivor> getSurvivors() {
        return survivors;
    }

    public List<Infected> getInfected() {
        return infected;
    }

    public void addSurvivor(Survivor survivor) {
        survivors.add(survivor);
    }
    public void addInfected(Infected infectedPlayer) {
        infected.add(infectedPlayer);
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    // Feather cooldown map
    private final Map<UUID, Long> featherCooldown = new HashMap<>();

    public void startGame() {
        gameRunning = true;
        infected.clear();


        int numToInfect = Math.min(
                plugin.getConfig().getInt("settings.starting-zombies", 5), // default fallback
                survivors.size()
        );

        Random random = new Random();

        // Load infected spawn from config
        String worldName = plugin.getConfig().getString("infected-spawn.world");
        double x = plugin.getConfig().getDouble("infected-spawn.x");
        double y = plugin.getConfig().getDouble("infected-spawn.y");
        double z = plugin.getConfig().getDouble("infected-spawn.z");
        float yaw = (float) plugin.getConfig().getDouble("infected-spawn.yaw");
        float pitch = (float) plugin.getConfig().getDouble("infected-spawn.pitch");

        Location infectedSpawn = null;
        if (worldName != null && plugin.getServer().getWorld(worldName) != null) {
            infectedSpawn = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
        }

        for (int i = 0; i < numToInfect; i++) {
            Survivor chosen = survivors.remove(random.nextInt(survivors.size()));
            Player infectedPlayer = chosen.getPlayer();

            infectPlayer(infectedPlayer, false);

            if (infectedSpawn != null) {
                infectedPlayer.teleport(infectedSpawn);
            } else {
                infectedPlayer.sendMessage(ChatColor.RED + "Infected spawn not set! You spawned normally.");
            }
        }

        String startMessage = plugin.getConfig().getString("messages.game-start", "&eGame started!");
        Bukkit.broadcastMessage(color(startMessage.replace("{zombies}", String.valueOf(numToInfect))));
    }

    public void stopGame() {
        if (!gameRunning) return;

        gameRunning = false;
        Bukkit.broadcastMessage(ChatColor.YELLOW + plugin.getConfig().getString("messages.game-stop", "The game has been stopped!"));

        // Get all online players
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Configurable batch size and delay
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 5);
        long delayBetweenBatches = plugin.getConfig().getInt("settings.teleport-delay", 40); // 2 seconds (40 ticks)

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int endIndex = Math.min(index + batchSize, allPlayers.size());
                List<Player> batch = allPlayers.subList(index, endIndex);

                index += batchSize;
                if (index >= allPlayers.size()) {
                    survivors.clear();
                    infected.clear();
                    cancel();
                }

                for (Player p : batch) {
                    resetPlayer(p);
                }
            }
        }.runTaskTimer(plugin, 0L, delayBetweenBatches);
    }

    // Helper method to reset a single player
    public void resetPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        player.setGlowing(false);
        survivors.add(new Survivor(player));
        player.teleport(player.getWorld().getSpawnLocation());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void handleHit(Player attacker, Player victim) {
        if (!gameRunning) return;

        boolean attackerIsZombie = infected.stream().anyMatch(z -> z.getPlayer().equals(attacker));
        boolean victimIsSurvivor = survivors.stream().anyMatch(s -> s.getPlayer().equals(victim));

        if (attackerIsZombie && victimIsSurvivor) {
            infectPlayer(victim, true);
        }
    }

    public void infectPlayer(Player victim, boolean announce) {
        survivors.removeIf(s -> s.getPlayer().equals(victim));
        infected.add(new Infected(plugin, victim, false));

        if (announce) {
            String msg = plugin.getConfig().getString("messages.infected", "&c{player} infected!");
            Bukkit.broadcastMessage(color(msg.replace("{player}", victim.getName())));
        }
        checkWin();
    }

    public void toggleZombie(Player player) {
        // If they are already infected -> switch to survivor
        if (infected.stream().anyMatch(z -> z.getPlayer().equals(player))) {
            infected.removeIf(z -> z.getPlayer().equals(player));
            survivors.add(new Survivor(player));
            player.sendMessage(ChatColor.GREEN + "You are now a survivor!");
            return;
        }

        // Otherwise make them infected
        survivors.removeIf(s -> s.getPlayer().equals(player));
        infected.add(new Infected(plugin, player, false));
    }

    public void checkWin() {
        if (survivors.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.all-infected.chat", "&cAll survivors infected!");
            String title = plugin.getConfig().getString("messages.all-infected.title", "&4Zombies Win!");
            String subtitle = plugin.getConfig().getString("messages.all-infected.subtitle", "&7All survivors have been infected!");

            // Send chat message
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));

            // Send title to all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendTitle(
                        ChatColor.translateAlternateColorCodes('&', title),
                        ChatColor.translateAlternateColorCodes('&', subtitle),
                        10, 80, 20
                );
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            }
            stopGame();
        }
    }

     // Starts the feather spawning task
    public void startFeatherTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isGameRunning()) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    // Skip if player already has a feather or is on cooldown
                    if (player.getInventory().contains(Material.FEATHER) || featherCooldown.containsKey(uuid)) continue;

                    // Give the feather
                    ItemStack feather = new ItemStack(Material.FEATHER);
                    ItemMeta meta = feather.getItemMeta();
                    meta.setDisplayName("§bJump Feather"); // AQUA colored
                    meta.setLore(Arrays.asList("§eUse this if you're stuck!", "§eLaunches you high into the air."));
                    feather.setItemMeta(meta);

                    player.getInventory().addItem(feather);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // check every second
    }
     // Set cooldown for a player using a feather
    public void setFeatherCooldown(Player player, int seconds) {
        featherCooldown.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> featherCooldown.remove(player.getUniqueId()), seconds * 20L);
    }
     // Check if player is on feather cooldown
    public boolean isOnFeatherCooldown(Player player) {
        Long expireTime = featherCooldown.get(player.getUniqueId());
        return expireTime != null && System.currentTimeMillis() < expireTime;
    }

    private String color(String input) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }
}
