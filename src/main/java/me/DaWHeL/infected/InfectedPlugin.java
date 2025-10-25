package me.DaWHeL.infected;

import me.DaWHeL.infected.Handlers.*;
import me.DaWHeL.infected.commands.*;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectedPlugin extends JavaPlugin {

    private GameManager gameManager;
    private TeleportManager teleportManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        gameManager = new GameManager(this);
        teleportManager = new TeleportManager(this);

        // Register Commands
        getCommand("startinfected").setExecutor(new StartGame(gameManager, this));
        getCommand("togglezombie").setExecutor(new ToggleZombie(gameManager));
        getCommand("stopinfected").setExecutor(new StopGame(gameManager));
        getCommand("addteleport").setExecutor(new AddTeleportCommand(this));
        getCommand("removeteleport").setExecutor(new RemoveTeleportCommand(this));
        getCommand("listteleportpoints").setExecutor(new ListTeleportPoints(this));
        getCommand("reloadinfected").setExecutor(new Reload(this));
        getCommand("tttp").setExecutor(new TeleportToTeleportPoint(this));
        getCommand("removeplayer").setExecutor(new RemovePlayer(gameManager));
        getCommand("buffinfected").setExecutor(new BuffInfectedCommand(gameManager, this));
        getCommand("listplayers").setExecutor(new ListPlayersCommand(gameManager));
        getCommand("helpinfected").setExecutor(new HelpInfectedCommand());
        getCommand("createinfectedspawn").setExecutor(new CreateInfectedSpawn(this));
        getCommand("tpinfectedspawn").setExecutor(new TpInfectedSpawn(this));
        getCommand("givefeather").setExecutor(new GiveFeather());

        //Register Events
        Bukkit.getPluginManager().registerEvents(new HitHandler(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new HungerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, gameManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new InfectedInventoryLockListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new InfectedRespawnListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new InfectedDeathListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new FriendlyFireListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new JumpFeatherListener(gameManager, this), this);

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