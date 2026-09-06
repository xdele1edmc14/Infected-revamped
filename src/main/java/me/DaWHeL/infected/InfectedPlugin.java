package me.DaWHeL.infected;

import me.DaWHeL.infected.Handlers.*;
import me.DaWHeL.infected.commands.*;
import me.DaWHeL.infected.gui.AdminGuiListener;
import me.DaWHeL.infected.gui.AdminGuiManager;
import me.DaWHeL.infected.gui.AdminSetupService;
import me.DaWHeL.infected.gui.InfectedAdminCommand;
import me.DaWHeL.infected.gui.weapon.WeaponLootGuiListener;
import me.DaWHeL.infected.gui.weapon.WeaponLootGuiManager;
import me.DaWHeL.infected.loot.*;
import me.DaWHeL.infected.localization.DeathTitleMessages;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

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
        WeaponLootRepository weaponLootRepository = new WeaponLootRepository(getDataFolder(), new ItemSnapshotCodec());
        weaponLootRepository.snapshot().errors().forEach(error ->
                getLogger().warning("Weapon loot configuration: " + error));
        WeaponSelectionListener weaponSelectionListener = new WeaponSelectionListener(this, weaponLootRepository);
        WeaponChestService weaponChestService = new WeaponChestService(
                gameManager,
                weaponLootRepository::snapshot,
                new ChestDiscoveryService(),
                new ChestLootGenerator(new Random())
        );
        gameManager.setRoundStartAllowed(() -> !weaponChestService.isOperationActive());
        AtomicReference<AdminGuiManager> adminGuiReference = new AtomicReference<>();
        WeaponLootGuiManager weaponLootGuiManager = new WeaponLootGuiManager(
                this, weaponLootRepository, weaponChestService, weaponSelectionListener,
                player -> adminGuiReference.get().openMain(player));
        AdminGuiManager adminGuiManager = new AdminGuiManager(
                this, gameManager, adminSetupService, weaponLootGuiManager::openWizard,
                () -> {
                    java.util.List<String> errors = weaponLootRepository.reload();
                    errors.forEach(error -> getLogger().warning("Weapon loot configuration: " + error));
                    return errors;
                });
        adminGuiReference.set(adminGuiManager);
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

        //Register Events
        Bukkit.getPluginManager().registerEvents(new ParticipantDamageListener(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new InfectedContainmentListener(gameManager), this);
        Bukkit.getPluginManager().registerEvents(new HungerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new InfectedInventoryLockListener(gameManager), this);
        getServer().getPluginManager().registerEvents(
                new InfectedRespawnListener(gameManager, spawnRepository), this);
        getServer().getPluginManager().registerEvents(
                new InfectedDeathListener(gameManager, DeathTitleMessages.load(this)), this);
        getServer().getPluginManager().registerEvents(new AdminGuiListener(this, adminGuiManager), this);
        getServer().getPluginManager().registerEvents(weaponSelectionListener, this);
        getServer().getPluginManager().registerEvents(new WeaponLootGuiListener(this, weaponLootGuiManager), this);

        long scoreboardUpdateInterval = Math.max(1L,
                getConfig().getLong("scoreboard.update-interval-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            gameManager.getScoreboardManager().updateScoreboard();
        }, 0L, scoreboardUpdateInterval);

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
