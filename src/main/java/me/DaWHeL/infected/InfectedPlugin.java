package me.DaWHeL.infected;

import me.DaWHeL.infected.Handlers.*;
import me.DaWHeL.infected.commands.*;
import me.DaWHeL.infected.gui.AdminGuiListener;
import me.DaWHeL.infected.gui.AdminGuiManager;
import me.DaWHeL.infected.gui.AdminSetupService;
import me.DaWHeL.infected.gui.InfectedAdminCommand;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Random;

public final class InfectedPlugin extends JavaPlugin {

    private GameManager gameManager;
    private TeleportManager teleportManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        SpawnRepository spawnRepository = new SpawnRepository(this);
        SpawnRepository.MigrationResult migration = spawnRepository.migrateLegacyTeleports();
        if (migration.migrated()) {
            getLogger().info("Migrated " + migration.copiedEntries()
                    + " legacy teleport entries into role-specific spawn groups.");
            migration.skippedPaths().forEach(path ->
                    getLogger().warning("Skipped malformed legacy teleport point: " + path));
        }

        PluginTaskScheduler scheduler = new BukkitPluginTaskScheduler(this);
        teleportManager = new TeleportManager(this, spawnRepository);
        gameManager = new GameManager(
                this,
                spawnRepository,
                teleportManager,
                scheduler,
                new RoundStartValidator(),
                new Random()
        );

        AdminSetupService adminSetupService = new AdminSetupService(this, spawnRepository);
        AdminGuiManager adminGuiManager = new AdminGuiManager(this, gameManager, adminSetupService);
        InfectedAdminCommand infectedAdminCommand = new InfectedAdminCommand(
                gameManager, adminSetupService, adminGuiManager);

        // Register Commands
        PluginCommand infectedCommand = Objects.requireNonNull(getCommand("infected"),
                "The infected command is missing from plugin.yml");
        infectedCommand.setExecutor(infectedAdminCommand);
        infectedCommand.setTabCompleter(infectedAdminCommand);
        getCommand("startinfected").setExecutor(new StartGame(gameManager));
        getCommand("togglezombie").setExecutor(new ToggleZombie(gameManager));
        getCommand("stopinfected").setExecutor(new StopGame(gameManager));
        getCommand("addteleport").setExecutor(new AddTeleportCommand(this));
        getCommand("removeteleport").setExecutor(new RemoveTeleportCommand(this));
        getCommand("listteleportpoints").setExecutor(new ListTeleportPoints(this));
        getCommand("reloadinfected").setExecutor(new Reload(this, gameManager));
        getCommand("tttp").setExecutor(new TeleportToTeleportPoint(this));
        getCommand("removeplayer").setExecutor(new RemovePlayer(gameManager));
        getCommand("buffinfected").setExecutor(new BuffInfectedCommand(gameManager, this));
        getCommand("listplayers").setExecutor(new ListPlayersCommand(gameManager));
        getCommand("helpinfected").setExecutor(new HelpInfectedCommand());
        getCommand("createinfectedspawn").setExecutor(new CreateInfectedSpawn(this));
        getCommand("tpinfectedspawn").setExecutor(new TpInfectedSpawn(this));
        getCommand("givefeather").setExecutor(new GiveFeather());

        //Register Events
        Bukkit.getPluginManager().registerEvents(new ParticipantDamageListener(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new InfectedContainmentListener(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new HungerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new InfectedInventoryLockListener(gameManager), this);
        getServer().getPluginManager().registerEvents(
                new InfectedRespawnListener(gameManager, spawnRepository), this);
        getServer().getPluginManager().registerEvents(new InfectedDeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new JumpFeatherListener(gameManager, this), this);
        getServer().getPluginManager().registerEvents(new AdminGuiListener(this, adminGuiManager), this);

        // Start feather spawning
        gameManager.startFeatherTask();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.getScoreboardManager().updateScoreboard();
        }, 0L, 40L); // every 2 seconds

        getLogger().info("##################################");
        getLogger().info("#                                #");
        getLogger().info("#    Infected Plugin Enabled!    #");
        getLogger().info("#                                #");
        getLogger().info("##################################");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("###################################");
        getLogger().info("#                                 #");
        getLogger().info("#    Infected Plugin Disabled!    #");
        getLogger().info("#                                 #");
        getLogger().info("###################################");
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }
}
