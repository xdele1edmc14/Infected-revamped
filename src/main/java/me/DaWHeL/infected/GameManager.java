package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GameManager {
    private final InfectedPlugin plugin;
    private final SpawnRepository spawnRepository;
    private final TeleportManager teleportManager;
    private final PluginTaskScheduler scheduler;
    private final RoundStartValidator startValidator;
    private final Random random;
    private final ParticipantRoleFactory roleFactory;
    private final List<Survivor> survivors = new ArrayList<>();
    private final List<Infected> infected = new ArrayList<>();
    private final InfectedLifeTracker infectedLives = new InfectedLifeTracker();
    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, Player> roundParticipants = new LinkedHashMap<>();
    private final Set<UUID> containedInfected = new LinkedHashSet<>();
    private final Set<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Set<UUID> roundTeleportBypass = new LinkedHashSet<>();
    private final Set<BukkitTask> roundTasks = new LinkedHashSet<>();
    private final Map<UUID, Long> featherCooldown = new HashMap<>();

    private RoundPhase phase = RoundPhase.LOBBY;
    private boolean buffEnabled;
    private long roundId;
    private BukkitTask cleanupTask;

    public GameManager(InfectedPlugin plugin) {
        this(
                plugin,
                new SpawnRepository(plugin),
                new TeleportManager(plugin),
                new BukkitPluginTaskScheduler(plugin),
                new RoundStartValidator(),
                new Random(),
                new BukkitParticipantRoleFactory(plugin)
        );
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random
    ) {
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random,
                new BukkitParticipantRoleFactory(plugin));
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.startValidator = Objects.requireNonNull(startValidator, "startValidator");
        this.random = Objects.requireNonNull(random, "random");
        this.roleFactory = Objects.requireNonNull(roleFactory, "roleFactory");
        this.scoreboardManager = new ScoreboardManager(plugin, this);
    }

    public InfectedPlugin getPlugin() {
        return plugin;
    }

    public RoundPhase getPhase() {
        return phase;
    }

    public boolean isGameRunning() {
        return phase.isRunning();
    }

    public List<Survivor> getSurvivors() {
        return survivors;
    }

    public List<Infected> getInfected() {
        return infected;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    public void setBuffEnabled(boolean buffEnabled) {
        this.buffEnabled = buffEnabled;
    }

    public void addSurvivor(Survivor survivor) {
        Objects.requireNonNull(survivor, "survivor");
        Player player = survivor.getPlayer();
        infected.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        survivors.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        survivors.add(survivor);
    }

    public boolean registerLobbySurvivor(Player player) {
        if (phase != RoundPhase.LOBBY || player == null || !player.isOnline()) {
            return false;
        }
        upsertSurvivor(player);
        return true;
    }

    public void addInfected(Infected infectedPlayer) {
        Objects.requireNonNull(infectedPlayer, "infectedPlayer");
        Player player = infectedPlayer.getPlayer();
        survivors.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        queuedPlayers.remove(player.getUniqueId());
        infected.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        infected.add(infectedPlayer);
        infectedLives.register(player.getUniqueId(), getConfiguredInfectedLives());
        roundParticipants.put(player.getUniqueId(), player);
    }

    public ParticipantRole roleOf(Player player) {
        if (player == null) {
            return ParticipantRole.NONE;
        }
        if (infected.stream().anyMatch(entry -> samePlayer(entry.getPlayer(), player))) {
            return ParticipantRole.INFECTED;
        }
        if (survivors.stream().anyMatch(entry -> samePlayer(entry.getPlayer(), player))) {
            return ParticipantRole.SURVIVOR;
        }
        return ParticipantRole.NONE;
    }

    public boolean isContainedInfected(Player player) {
        return player != null && containedInfected.contains(player.getUniqueId());
    }

    public boolean isRoundTeleportBypass(Player player) {
        return player != null && roundTeleportBypass.contains(player.getUniqueId());
    }

    public boolean isQueued(Player player) {
        return player != null && queuedPlayers.contains(player.getUniqueId());
    }

    public long currentRoundId() {
        return roundId;
    }

    public RoundStartValidator.Result validateStart() {
        return validateStart(uniqueOnlineLobbyPlayers());
    }

    public StartResult startGame() {
        if (phase != RoundPhase.LOBBY) {
            return StartResult.rejected("The Infected event is already running or cleaning up.");
        }

        List<Player> participants = uniqueOnlineLobbyPlayers();
        RoundStartValidator.Result validation = validateStart(participants);
        if (!validation.valid()) {
            return StartResult.rejected(validation.errors());
        }

        long startedRound = ++roundId;
        transitionTo(RoundPhase.COUNTDOWN);
        cancelRoundTasks();
        infected.clear();
        infectedLives.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        roundTeleportBypass.clear();
        roundParticipants.clear();
        participants.forEach(player -> roundParticipants.put(player.getUniqueId(), player));

        survivors.removeIf(survivor -> !roundParticipants.containsKey(survivor.getPlayer().getUniqueId()));
        List<Player> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        int startingInfected = plugin.getConfig().getInt("settings.starting-zombies", 5);
        Location holdingSpawn = spawnRepository.loadedHoldingSpawn().orElseThrow();

        for (int index = 0; index < startingInfected; index++) {
            Player player = shuffled.get(index);
            survivors.removeIf(survivor -> samePlayer(survivor.getPlayer(), player));
            assignInfected(player);
            containedInfected.add(player.getUniqueId());
            if (!teleportWithContainmentBypass(player, holdingSpawn)) {
                String error = "An initial infected could not be teleported to the holding spawn.";
                beginEnding(EndReason.START_FAILURE, "&c" + error + " The round is being reset.");
                return StartResult.rejected(error);
            }
        }

        broadcast(plugin.getConfig().getString(
                "messages.game-start",
                "&eThe Infected game has started with &c{zombies} &ezombies!"
        ).replace("{zombies}", String.valueOf(startingInfected)));

        List<Player> survivorPlayers = survivors.stream()
                .map(Survivor::getPlayer)
                .filter(Player::isOnline)
                .toList();
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 5);
        long delayTicks = plugin.getConfig().getLong("settings.teleport-delay", 40L);
        BukkitTask task = teleportManager.teleportPlayersBatch(
                SpawnRole.SURVIVOR,
                survivorPlayers,
                batchSize,
                delayTicks,
                player -> isCurrentParticipant(player, ParticipantRole.SURVIVOR, startedRound,
                        RoundPhase.COUNTDOWN),
                result -> onSurvivorsTeleported(startedRound, result)
        );
        trackRoundTask(task);
        return StartResult.started();
    }

    private void onSurvivorsTeleported(long expectedRound, TeleportBatchResult result) {
        if (!isCurrentRound(expectedRound, RoundPhase.COUNTDOWN)) {
            return;
        }
        if (!result.success()) {
            beginEnding(EndReason.START_FAILURE,
                    "&cSurvivor teleporting failed. The round is being reset.");
            return;
        }

        transitionTo(RoundPhase.HEADSTART);
        int delaySeconds = plugin.getConfig().getInt("settings.infected-teleport-delay", 10);
        broadcast(plugin.getConfig().getString(
                "messages.zombies-teleporting",
                "&cInfected zombies will be teleported in &e{time} &cseconds..."
        ).replace("{time}", String.valueOf(delaySeconds)));
        BukkitTask delay = scheduler.runLater(
                () -> beginInfectedRelease(expectedRound),
                delaySeconds * 20L
        );
        trackRoundTask(delay);
    }

    private void beginInfectedRelease(long expectedRound) {
        if (!isCurrentRound(expectedRound, RoundPhase.HEADSTART)) {
            return;
        }
        List<Player> infectedPlayers = infected.stream()
                .map(Infected::getPlayer)
                .filter(Player::isOnline)
                .toList();
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 5);
        long delayTicks = plugin.getConfig().getLong("settings.teleport-delay", 40L);
        BukkitTask task = teleportManager.teleportPlayersBatch(
                SpawnRole.INFECTED_RELEASE,
                infectedPlayers,
                batchSize,
                delayTicks,
                player -> isCurrentParticipant(player, ParticipantRole.INFECTED, expectedRound,
                        RoundPhase.HEADSTART),
                player -> roundTeleportBypass.add(player.getUniqueId()),
                (player, teleported) -> {
                    roundTeleportBypass.remove(player.getUniqueId());
                },
                result -> onInfectedReleased(expectedRound, result)
        );
        trackRoundTask(task);
    }

    private void onInfectedReleased(long expectedRound, TeleportBatchResult result) {
        if (!isCurrentRound(expectedRound, RoundPhase.HEADSTART)) {
            return;
        }
        roundTeleportBypass.clear();
        if (!result.success()) {
            beginEnding(EndReason.START_FAILURE,
                    "&cInfected release teleporting failed. The round is being reset.");
            return;
        }

        containedInfected.clear();
        transitionTo(RoundPhase.ACTIVE);
        checkWin();
    }

    public boolean stopGame() {
        return beginEnding(EndReason.ADMIN_STOP, null);
    }

    public boolean cancelForUnsafeInfectedRespawn() {
        return beginEnding(
                EndReason.RESPAWN_FAILURE,
                plugin.getConfig().getString(
                        "messages.unsafe-infected-respawn",
                        "&cNo safe dedicated infected respawn is available. The round has been cancelled."
                )
        );
    }

    private boolean beginEnding(EndReason reason, String failureMessage) {
        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return false;
        }

        transitionTo(RoundPhase.ENDING);
        roundId++;
        cancelRoundTasks();
        containedInfected.clear();
        roundTeleportBypass.clear();
        featherCooldown.clear();

        if (reason == EndReason.ADMIN_STOP) {
            broadcast(plugin.getConfig().getString(
                    "messages.game-stop",
                    "&eThe Infected game has been stopped!"
            ));
        } else if (failureMessage != null) {
            broadcast(failureMessage);
        }

        List<Player> cleanupPlayers = new ArrayList<>(roundParticipants.values());
        survivors.forEach(survivor -> cleanupPlayers.add(survivor.getPlayer()));
        infected.forEach(infectedPlayer -> cleanupPlayers.add(infectedPlayer.getPlayer()));
        UniqueBatchQueue<UUID, Player> queue = new UniqueBatchQueue<>(cleanupPlayers, Player::getUniqueId);
        Set<UUID> processed = new LinkedHashSet<>();
        int batchSize = Math.max(1, plugin.getConfig().getInt("settings.teleport-batch-size", 5));
        long period = Math.max(1L, plugin.getConfig().getLong("settings.teleport-delay", 40L));

        if (queue.isComplete()) {
            finishCleanup(processed);
            return true;
        }

        BukkitTask[] taskHandle = new BukkitTask[1];
        Runnable cleanupOperation = () -> {
            for (Player player : queue.nextBatch(batchSize)) {
                if (player.isOnline() && processed.add(player.getUniqueId())) {
                    resetPlayerState(player);
                }
            }
            if (queue.isComplete()) {
                if (taskHandle[0] != null) {
                    taskHandle[0].cancel();
                }
                cleanupTask = null;
                finishCleanup(processed);
            }
        };
        cleanupTask = scheduler.runRepeating(cleanupOperation, 0L, period);
        taskHandle[0] = cleanupTask;
        return true;
    }

    private void finishCleanup(Set<UUID> processed) {
        survivors.clear();
        infected.clear();
        roundParticipants.clear();
        queuedPlayers.clear();
        infectedLives.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (processed.add(player.getUniqueId()) && player.isOnline()) {
                resetPlayerState(player);
            }
            if (player.isOnline()) {
                upsertSurvivor(player);
            }
        }
        transitionTo(RoundPhase.LOBBY);
    }

    public void resetPlayer(Player player) {
        resetPlayerState(player);
        if (phase == RoundPhase.LOBBY) {
            registerLobbySurvivor(player);
        }
    }

    public void resetPlayerState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setGlowing(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setPlayerListName(player.getName());
        player.teleport(player.getWorld().getSpawnLocation());
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void handleHit(Player attacker, Player victim) {
        if (phase == RoundPhase.ACTIVE
                && roleOf(attacker) == ParticipantRole.INFECTED
                && roleOf(victim) == ParticipantRole.SURVIVOR) {
            infectPlayer(victim, true);
        }
    }

    public void infectPlayer(Player victim, boolean announce) {
        if (phase != RoundPhase.ACTIVE || roleOf(victim) != ParticipantRole.SURVIVOR) {
            return;
        }
        survivors.removeIf(survivor -> samePlayer(survivor.getPlayer(), victim));
        assignInfected(victim);
        if (announce) {
            String message = plugin.getConfig().getString("messages.infected", "&c{player} infected!");
            broadcast(message.replace("{player}", victim.getName()));
        }
        applyConclusion(RoundOutcomePolicy.evaluate(
                phase, survivors.size(), infected.size(), RosterChange.INFECTION));
    }

    public RoundActionResult toggleZombieSafely(Player player) {
        if (phase != RoundPhase.ACTIVE) {
            return RoundActionResult.rejected(
                    "Zombie toggles are only allowed during active play.");
        }
        if (player == null || !player.isOnline()) {
            return RoundActionResult.rejected("The target player must be online.");
        }

        ParticipantRole role = roleOf(player);
        if (role == ParticipantRole.INFECTED) {
            if (infected.size() <= 1) {
                return RoundActionResult.rejected(
                        "The final infected cannot be toggled away. Use /removeplayer or /stopinfected.");
            }
            infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
            infectedLives.remove(player.getUniqueId());
            clearInfectedRoleState(player);
            upsertSurvivor(player);
            roundParticipants.put(player.getUniqueId(), player);
            return RoundActionResult.accepted(player.getName() + " is now a survivor.");
        }

        if (role == ParticipantRole.SURVIVOR && survivors.size() <= 1) {
            return RoundActionResult.rejected(
                    "The final survivor cannot be toggled. Infect them through normal gameplay or stop the round.");
        }
        if (role == ParticipantRole.NONE && !isQueued(player)) {
            return RoundActionResult.rejected(
                    "That player is not an active participant or queued spectator.");
        }

        java.util.Optional<Location> respawn = InfectedRespawnSelector.select(
                spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (respawn.isEmpty()) {
            cancelForUnsafeInfectedRespawn();
            return RoundActionResult.rejected(
                    "No safe infected respawn was available, so the round was cancelled.");
        }

        if (!teleportWithContainmentBypass(player, respawn.get())) {
            beginEnding(
                    EndReason.RESPAWN_FAILURE,
                    plugin.getConfig().getString(
                            "messages.infected-admission-teleport-failed",
                            "&cThe infected player could not be teleported safely. The round has been cancelled."
                    )
            );
            return RoundActionResult.rejected(
                    "The infected teleport failed, so the round was cancelled.");
        }

        survivors.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        queuedPlayers.remove(player.getUniqueId());
        player.setGameMode(GameMode.SURVIVAL);
        assignInfected(player);
        return RoundActionResult.accepted(player.getName() + " is now infected.");
    }

    @Deprecated
    public void toggleZombie(Player player) {
        RoundActionResult result = toggleZombieSafely(player);
        if (player != null) {
            player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        }
    }

    public void recordRoundParticipant(Player player) {
        if (player != null && phase != RoundPhase.LOBBY && phase != RoundPhase.ENDING) {
            roundParticipants.put(player.getUniqueId(), player);
        }
    }

    public boolean queueLateJoin(Player player) {
        if (player == null || phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return false;
        }
        survivors.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        roundParticipants.remove(player.getUniqueId());
        containedInfected.remove(player.getUniqueId());
        roundTeleportBypass.remove(player.getUniqueId());
        queuedPlayers.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.YELLOW
                + "A round is already running. You are queued as a spectator for the next round.");
        return true;
    }

    @Deprecated
    public boolean addLateJoinInfected(Player player) {
        return queueLateJoin(player);
    }

    public void handleQuit(Player player) {
        ParticipantRole departedRole = removeRoundMembership(player);
        applyDepartureOutcome(departedRole);
    }

    public RoundActionResult removePlayer(Player player) {
        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return RoundActionResult.rejected(
                    "Players can only be removed during countdown, head start, or active play.");
        }
        ParticipantRole role = roleOf(player);
        boolean queued = isQueued(player);
        if (role == ParticipantRole.NONE && !queued) {
            return RoundActionResult.rejected("That player is not part of this round or its queue.");
        }

        ParticipantRole departedRole = removeRoundMembership(player);
        resetPlayerState(player);
        applyDepartureOutcome(departedRole);
        return RoundActionResult.accepted(player.getName() + " was removed from the round.");
    }

    @Deprecated
    public void removeParticipant(Player player) {
        handleQuit(player);
    }

    private ParticipantRole removeRoundMembership(Player player) {
        if (player == null) {
            return ParticipantRole.NONE;
        }
        ParticipantRole departedRole = roleOf(player);
        survivors.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        roundParticipants.remove(player.getUniqueId());
        containedInfected.remove(player.getUniqueId());
        roundTeleportBypass.remove(player.getUniqueId());
        featherCooldown.remove(player.getUniqueId());
        return departedRole;
    }

    public boolean handleInfectedDeath(Player player) {
        if (phase != RoundPhase.ACTIVE || roleOf(player) != ParticipantRole.INFECTED) {
            return false;
        }
        boolean hasRemainingLife = infectedLives.consumeLife(player.getUniqueId());
        if (!hasRemainingLife) {
            infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
            applyConclusion(RoundOutcomePolicy.evaluate(
                    phase, survivors.size(), infected.size(), RosterChange.INFECTED_ELIMINATION));
        }
        return hasRemainingLife;
    }

    public boolean isEliminatedInfected(Player player) {
        return infectedLives.isEliminated(player.getUniqueId());
    }

    public void checkWin() {
        if (phase != RoundPhase.ACTIVE) {
            return;
        }
        if (survivors.isEmpty() && infected.isEmpty()) {
            applyConclusion(RoundConclusion.CANCELLED);
        }
    }

    private void announceWinner(RoundWinner winner) {
        String messagePath = winner == RoundWinner.SURVIVORS
                ? "messages.all-survivors"
                : "messages.all-infected";
        String defaultChat = winner == RoundWinner.SURVIVORS
                ? "&2All infected have been eliminated! Survivors win!"
                : "&cAll survivors infected!";
        String defaultTitle = winner == RoundWinner.SURVIVORS ? "&2Survivors Win!" : "&4Zombies Win!";
        String defaultSubtitle = winner == RoundWinner.SURVIVORS
                ? "&7All infected have been eliminated!"
                : "&7All survivors have been infected!";
        String victorySound = winner == RoundWinner.SURVIVORS
                ? "entity.player.levelup"
                : "entity.wither.spawn";

        broadcast(plugin.getConfig().getString(messagePath + ".chat", defaultChat));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendTitle(
                    color(plugin.getConfig().getString(messagePath + ".title", defaultTitle)),
                    color(plugin.getConfig().getString(messagePath + ".subtitle", defaultSubtitle)),
                    10,
                    80,
                    20
            );
            player.playSound(player.getLocation(), victorySound, 1.0f, 1.0f);
        }
        beginEnding(EndReason.WINNER, null);
    }

    public void startFeatherTask() {
        scheduler.runRepeating(() -> {
            if (phase != RoundPhase.ACTIVE) {
                return;
            }
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (roleOf(player) == ParticipantRole.NONE) {
                    continue;
                }
                UUID uuid = player.getUniqueId();
                if (player.getInventory().contains(Material.FEATHER) || featherCooldown.containsKey(uuid)) {
                    continue;
                }
                ItemStack feather = new ItemStack(Material.FEATHER);
                ItemMeta meta = feather.getItemMeta();
                meta.setDisplayName("§bJump Feather");
                meta.setLore(List.of("§eUse this if you're stuck!", "§eLaunches you high into the air."));
                feather.setItemMeta(meta);
                player.getInventory().addItem(feather);
            }
        }, 0L, 20L);
    }

    public void setFeatherCooldown(Player player, int seconds) {
        featherCooldown.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        BukkitTask task = scheduler.runLater(
                () -> featherCooldown.remove(player.getUniqueId()),
                seconds * 20L
        );
        trackRoundTask(task);
    }

    public boolean isOnFeatherCooldown(Player player) {
        Long expiry = featherCooldown.get(player.getUniqueId());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public void shutdown() {
        roundId++;
        cancelRoundTasks();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        survivors.clear();
        infected.clear();
        roundParticipants.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        roundTeleportBypass.clear();
        infectedLives.clear();
        featherCooldown.clear();
        phase = RoundPhase.LOBBY;
    }

    private RoundStartValidator.Result validateStart(List<Player> participants) {
        EnumSet<SpawnRole> loadedRoles = EnumSet.noneOf(SpawnRole.class);
        for (SpawnRole role : SpawnRole.values()) {
            if (!spawnRepository.loadedLocations(role).isEmpty()) {
                loadedRoles.add(role);
            }
        }
        return startValidator.validate(new RoundStartValidator.Input(
                spawnRepository.loadedHoldingSpawn().isPresent(),
                loadedRoles,
                participants.size(),
                plugin.getConfig().getInt("settings.starting-zombies", 5),
                plugin.getConfig().getInt("settings.teleport-batch-size", 5),
                plugin.getConfig().getInt("settings.teleport-delay", 40),
                plugin.getConfig().getInt("settings.infected-teleport-delay", 10)
        ));
    }

    private List<Player> uniqueOnlineLobbyPlayers() {
        LinkedHashMap<UUID, Player> unique = new LinkedHashMap<>();
        for (Survivor survivor : survivors) {
            Player player = survivor.getPlayer();
            if (player.isOnline()) {
                unique.put(player.getUniqueId(), player);
            }
        }
        return List.copyOf(unique.values());
    }

    private void assignInfected(Player player) {
        addInfected(roleFactory.createInfected(player));
    }

    private void upsertSurvivor(Player player) {
        infected.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        survivors.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        survivors.add(roleFactory.createSurvivor(player));
    }

    private void clearInfectedRoleState(Player player) {
        player.setGlowing(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setHelmet(null);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
    }

    private boolean teleportWithContainmentBypass(Player player, Location destination) {
        roundTeleportBypass.add(player.getUniqueId());
        try {
            return player.teleport(destination);
        } finally {
            roundTeleportBypass.remove(player.getUniqueId());
        }
    }

    private boolean isCurrentRound(long expectedRound, RoundPhase expectedPhase) {
        return expectedRound == roundId && phase == expectedPhase;
    }

    private boolean isCurrentParticipant(
            Player player,
            ParticipantRole expectedRole,
            long expectedRound,
            RoundPhase expectedPhase
    ) {
        return isCurrentRound(expectedRound, expectedPhase)
                && roundParticipants.containsKey(player.getUniqueId())
                && roleOf(player) == expectedRole;
    }

    private void applyDepartureOutcome(ParticipantRole departedRole) {
        RosterChange change = switch (departedRole) {
            case SURVIVOR -> RosterChange.SURVIVOR_DEPARTURE;
            case INFECTED -> RosterChange.INFECTED_DEPARTURE;
            case NONE -> null;
        };
        if (change != null) {
            applyConclusion(RoundOutcomePolicy.evaluate(
                    phase, survivors.size(), infected.size(), change));
        }
    }

    private void applyConclusion(RoundConclusion conclusion) {
        switch (conclusion) {
            case NONE -> {
            }
            case SURVIVORS_WIN -> announceWinner(RoundWinner.SURVIVORS);
            case INFECTED_WIN -> announceWinner(RoundWinner.INFECTED);
            case CANCELLED -> beginEnding(
                    EndReason.ABANDONMENT,
                    plugin.getConfig().getString(
                            "messages.round-abandoned",
                            "&eThe Infected round was abandoned because a team left the event."
                    )
            );
        }
    }

    private void transitionTo(RoundPhase next) {
        if (!phase.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal round phase transition: " + phase + " -> " + next);
        }
        phase = next;
    }

    private void trackRoundTask(BukkitTask task) {
        if (task != null && phase != RoundPhase.LOBBY && phase != RoundPhase.ENDING) {
            roundTasks.add(task);
        }
    }

    private void cancelRoundTasks() {
        for (BukkitTask task : roundTasks) {
            task.cancel();
        }
        roundTasks.clear();
    }

    private int getConfiguredInfectedLives() {
        return Math.max(1, plugin.getConfig().getInt("settings.infected-lives", 3));
    }

    private void broadcast(String message) {
        plugin.getServer().broadcastMessage(color(message));
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static boolean samePlayer(Player first, Player second) {
        return first.getUniqueId().equals(second.getUniqueId());
    }

    private enum EndReason {
        ADMIN_STOP,
        ABANDONMENT,
        START_FAILURE,
        RESPAWN_FAILURE,
        WINNER
    }
}
