package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final InfectedBuffController buffController;
    private final MatchPresentationService presentation;
    private final InfectedRoleEquipment roleEquipment;
    private final List<Survivor> survivors = new ArrayList<>();
    private final List<Infected> infected = new ArrayList<>();
    private final InfectedLifeTracker infectedLives = new InfectedLifeTracker();
    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, Player> roundParticipants = new LinkedHashMap<>();
    private final Set<UUID> containedInfected = new LinkedHashSet<>();
    private final Set<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Set<UUID> roundTeleportBypass = new LinkedHashSet<>();
    private final Set<UUID> pendingInfectedRespawns = new LinkedHashSet<>();
    private final Set<UUID> pendingHoldingRespawns = new LinkedHashSet<>();
    private final Map<UUID, Player> lockedParticipants = new LinkedHashMap<>();
    private final Map<UUID, PlayerStateSnapshot> playerSnapshots = new LinkedHashMap<>();
    private final RoundTaskRegistry taskRegistry = new RoundTaskRegistry();

    private RoundPhase phase = RoundPhase.LOBBY;
    private boolean buffEnabled;
    private long roundId;
    private BukkitTask cleanupTask;
    private BukkitTask infectedTrackingTask;

    public GameManager(InfectedPlugin plugin) {
        this(
                plugin,
                new SpawnRepository(plugin),
                new TeleportManager(plugin),
                new BukkitPluginTaskScheduler(plugin),
                new RoundStartValidator(),
                new Random(),
                new BukkitParticipantRoleFactory(plugin),
                new InfectedBuffController(plugin),
                new MatchPresentationService(plugin),
                new InfectedRoleEquipment(plugin)
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
                new BukkitParticipantRoleFactory(plugin), new InfectedBuffController(plugin),
                new MatchPresentationService(plugin), new InfectedRoleEquipment(plugin));
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
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random,
                roleFactory, new InfectedBuffController(plugin), new MatchPresentationService(plugin),
                new InfectedRoleEquipment(plugin));
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffController buffController
    ) {
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random,
                roleFactory, buffController, new MatchPresentationService(plugin),
                new InfectedRoleEquipment(plugin));
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffController buffController,
            MatchPresentationService presentation
    ) {
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random,
                roleFactory, buffController, presentation, new InfectedRoleEquipment(plugin));
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffController buffController,
            MatchPresentationService presentation,
            InfectedRoleEquipment roleEquipment
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.startValidator = Objects.requireNonNull(startValidator, "startValidator");
        this.random = Objects.requireNonNull(random, "random");
        this.roleFactory = Objects.requireNonNull(roleFactory, "roleFactory");
        this.buffController = Objects.requireNonNull(buffController, "buffController");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.roleEquipment = Objects.requireNonNull(roleEquipment, "roleEquipment");
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
        if (phase != RoundPhase.LOBBY || player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        restoreSnapshot(player);
        upsertSurvivor(player);
        return true;
    }

    public void registerOnlineLobbySurvivors(Collection<? extends Player> onlinePlayers) {
        if (phase != RoundPhase.LOBBY || onlinePlayers == null) {
            return;
        }
        LinkedHashMap<UUID, Player> unique = new LinkedHashMap<>();
        for (Player player : onlinePlayers) {
            if (player != null && player.isOnline() && !player.isDead()) {
                unique.putIfAbsent(player.getUniqueId(), player);
            }
        }
        unique.values().forEach(this::registerLobbySurvivor);
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
        if (phase == RoundPhase.ACTIVE) {
            buffController.apply(player, buffEnabled);
        }
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

    public boolean isRoundParticipant(Player player) {
        return player != null
                && phase != RoundPhase.LOBBY
                && roundParticipants.containsKey(player.getUniqueId());
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
        taskRegistry.resetForNewRound();
        buffEnabled = false;
        infectedTrackingTask = null;
        infected.clear();
        infectedLives.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        roundTeleportBypass.clear();
        pendingInfectedRespawns.clear();
        pendingHoldingRespawns.clear();
        roundParticipants.clear();
        lockedParticipants.clear();
        Set<UUID> capturedForThisStart = new LinkedHashSet<>();
        try {
            for (Player player : participants) {
                UUID playerId = player.getUniqueId();
                lockedParticipants.put(playerId, player);
                roundParticipants.put(playerId, player);
                if (!playerSnapshots.containsKey(playerId)) {
                    playerSnapshots.put(playerId, PlayerStateSnapshot.capture(player));
                    capturedForThisStart.add(playerId);
                }
            }
        } catch (RuntimeException exception) {
            restoreCapturedPlayers(capturedForThisStart);
            lockedParticipants.clear();
            roundParticipants.clear();
            taskRegistry.cancelAll();
            return StartResult.rejected("A participant's pre-match state could not be saved.");
        }

        transitionTo(RoundPhase.COUNTDOWN);
        int countdownSeconds = plugin.getConfig().getInt("settings.start-countdown-seconds", 10);
        if (countdownSeconds == 0) {
            return beginDeployment(startedRound);
        }
        broadcastCountdown(countdownSeconds);
        int[] remainingSeconds = {countdownSeconds};
        BukkitTask[] countdownHandle = new BukkitTask[1];
        Runnable countdownTick = () -> {
            if (!isCurrentRound(startedRound, RoundPhase.COUNTDOWN)) {
                cancelAndForget(countdownHandle[0]);
                return;
            }
            remainingSeconds[0]--;
            if (remainingSeconds[0] <= 0) {
                cancelAndForget(countdownHandle[0]);
                beginDeployment(startedRound);
                return;
            }
            if (remainingSeconds[0] == 10 || remainingSeconds[0] <= 5) {
                broadcastCountdown(remainingSeconds[0]);
            }
        };
        countdownHandle[0] = scheduler.runRepeating(countdownTick, 20L, 20L);
        trackRoundTask(countdownHandle[0]);
        return StartResult.started();
    }

    private StartResult beginDeployment(long expectedRound) {
        if (!isCurrentRound(expectedRound, RoundPhase.COUNTDOWN)) {
            return StartResult.rejected("The countdown is no longer current.");
        }

        List<Player> participants = lockedParticipants.values().stream()
                .filter(Player::isOnline)
                .toList();
        RoundStartValidator.Result validation = validateStart(participants);
        if (!validation.valid()) {
            beginEnding(EndReason.START_FAILURE,
                    plugin.getConfig().getString(
                            "messages.countdown-cancelled",
                            "&cThe Infected countdown was cancelled because the round is no longer ready."
                    ));
            return StartResult.rejected(validation.errors());
        }
        transitionTo(RoundPhase.DEPLOYING);
        presentation.deployment(participants);
        Set<UUID> deployingIds = participants.stream()
                .map(Player::getUniqueId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        roundParticipants.keySet().retainAll(deployingIds);
        lockedParticipants.keySet().retainAll(deployingIds);
        survivors.removeIf(survivor -> !deployingIds.contains(survivor.getPlayer().getUniqueId()));

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

        survivors.forEach(Survivor::prepareForMatch);
        List<Player> survivorPlayers = survivors.stream()
                .map(Survivor::getPlayer)
                .filter(Player::isOnline)
                .toList();
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 5);
        long delayTicks = plugin.getConfig().getLong("settings.teleport-delay", 40L);
        BukkitTask[] taskHandle = new BukkitTask[1];
        taskHandle[0] = teleportManager.teleportPlayersBatch(
                SpawnRole.SURVIVOR,
                survivorPlayers,
                batchSize,
                delayTicks,
                player -> isCurrentParticipant(player, ParticipantRole.SURVIVOR, expectedRound,
                        RoundPhase.DEPLOYING),
                result -> {
                    taskRegistry.forget(taskHandle[0]);
                    onSurvivorsTeleported(expectedRound, result);
                }
        );
        trackRoundTask(taskHandle[0]);
        return StartResult.started();
    }

    private void onSurvivorsTeleported(long expectedRound, TeleportBatchResult result) {
        if (!isCurrentRound(expectedRound, RoundPhase.DEPLOYING)) {
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
        BukkitTask[] taskHandle = new BukkitTask[1];
        taskHandle[0] = teleportManager.teleportPlayersBatch(
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
                result -> {
                    taskRegistry.forget(taskHandle[0]);
                    onInfectedReleased(expectedRound, result);
                }
        );
        trackRoundTask(taskHandle[0]);
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
        presentation.active(
                survivors.stream().map(Survivor::getPlayer).filter(Player::isOnline).toList(),
                infected.stream().map(Infected::getPlayer).filter(Player::isOnline).toList(),
                queuedPlayers.stream()
                        .map(plugin.getServer()::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(Player::isOnline)
                        .toList()
        );
        scheduleRoundExpiry(expectedRound);
        checkWin();
    }

    private void scheduleRoundExpiry(long expectedRound) {
        int timeLimitSeconds = plugin.getConfig().getInt("settings.round-time-limit-seconds", 0);
        if (timeLimitSeconds <= 0) {
            return;
        }

        BukkitTask[] expiryHandle = new BukkitTask[1];
        Runnable expiry = () -> {
            taskRegistry.forget(expiryHandle[0]);
            if (isCurrentRound(expectedRound, RoundPhase.ACTIVE) && !survivors.isEmpty()) {
                broadcast(plugin.getConfig().getString(
                        "messages.round-expired",
                        "&eTime expired. The remaining survivors win!"
                ));
                applyConclusion(RoundConclusion.SURVIVORS_WIN);
            }
        };
        expiryHandle[0] = scheduler.runLater(expiry, timeLimitSeconds * 20L);
        trackRoundTask(expiryHandle[0]);
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
        buffEnabled = false;
        infectedTrackingTask = null;
        containedInfected.clear();
        roundTeleportBypass.clear();

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
                    restoreSnapshot(player);
                }
            }
            if (queue.isComplete()) {
                if (taskHandle[0] != null) {
                    taskHandle[0].cancel();
                    taskRegistry.forget(taskHandle[0]);
                }
                cleanupTask = null;
                finishCleanup(processed);
            }
        };
        cleanupTask = scheduler.runRepeating(cleanupOperation, 0L, period);
        taskRegistry.trackCleanup(cleanupTask);
        taskHandle[0] = cleanupTask;
        return true;
    }

    private void finishCleanup(Set<UUID> processed) {
        survivors.clear();
        infected.clear();
        roundParticipants.clear();
        lockedParticipants.clear();
        queuedPlayers.clear();
        infectedLives.clear();
        pendingInfectedRespawns.clear();
        pendingHoldingRespawns.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isOnline()) {
                restoreSnapshot(player);
                if (!playerSnapshots.containsKey(player.getUniqueId())) {
                    upsertSurvivor(player);
                }
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
        if (!phase.allowsZombieToggle()) {
            return RoundActionResult.rejected(
                    "Zombie toggles are only allowed during active play.");
        }
        if (player == null || !player.isOnline()) {
            return RoundActionResult.rejected("The target player must be online.");
        }
        if (player.isDead()) {
            return RoundActionResult.rejected("The target player must be alive before changing teams.");
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
            Survivor survivor = upsertSurvivor(player);
            survivor.prepareForMatch();
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
        if (player == null || !phase.queuesLateJoins()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        try {
            playerSnapshots.computeIfAbsent(playerId, ignored -> PlayerStateSnapshot.capture(player));
        } catch (RuntimeException exception) {
            return false;
        }
        survivors.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
        infectedLives.remove(playerId);
        roundParticipants.remove(playerId);
        lockedParticipants.remove(playerId);
        containedInfected.remove(playerId);
        roundTeleportBypass.remove(playerId);
        queuedPlayers.add(playerId);
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
        restoreSnapshot(player);
        ParticipantRole departedRole = removeRoundMembership(player);
        applyDepartureOutcome(departedRole);
    }

    public RoundActionResult removePlayer(Player player) {
        if (!phase.allowsParticipantRemoval()) {
            return RoundActionResult.rejected(
                    "Players can only be removed during countdown, deployment, head start, or active play.");
        }
        ParticipantRole role = roleOf(player);
        boolean queued = isQueued(player);
        if (role == ParticipantRole.NONE && !queued
                && !roundParticipants.containsKey(player.getUniqueId())) {
            return RoundActionResult.rejected("That player is not part of this round or its queue.");
        }

        ParticipantRole departedRole = removeRoundMembership(player);
        restoreSnapshot(player);
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
        lockedParticipants.remove(player.getUniqueId());
        containedInfected.remove(player.getUniqueId());
        roundTeleportBypass.remove(player.getUniqueId());
        pendingInfectedRespawns.remove(player.getUniqueId());
        pendingHoldingRespawns.remove(player.getUniqueId());
        return departedRole;
    }

    public boolean handlePlayerDeath(Player player) {
        if (player == null || !roundParticipants.containsKey(player.getUniqueId())) {
            return false;
        }
        ParticipantRole role = roleOf(player);
        if (phase == RoundPhase.ACTIVE && role == ParticipantRole.SURVIVOR) {
            if (pendingInfectedRespawns.add(player.getUniqueId())) {
                infectPlayer(player, true);
            }
            return true;
        }
        if (phase == RoundPhase.ACTIVE && role == ParticipantRole.INFECTED) {
            handleInfectedDeath(player);
            return true;
        }
        if ((phase == RoundPhase.DEPLOYING || phase == RoundPhase.HEADSTART)
                && role == ParticipantRole.INFECTED) {
            pendingHoldingRespawns.add(player.getUniqueId());
            return true;
        }
        return false;
    }

    public boolean handleInfectedDeath(Player player) {
        if (phase != RoundPhase.ACTIVE || roleOf(player) != ParticipantRole.INFECTED) {
            return false;
        }
        if (!pendingInfectedRespawns.add(player.getUniqueId())) {
            return true;
        }
        boolean hasRemainingLife = infectedLives.consumeLife(player.getUniqueId());
        if (!hasRemainingLife) {
            infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
            applyConclusion(RoundOutcomePolicy.evaluate(
                    phase, survivors.size(), infected.size(), RosterChange.INFECTED_ELIMINATION));
        }
        return hasRemainingLife;
    }

    public Optional<Location> restoreAfterRoundRespawn(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        UUID playerId = player.getUniqueId();
        boolean cleanupRestore = phase == RoundPhase.ENDING || phase == RoundPhase.LOBBY;
        boolean removedParticipantRestore = phase != RoundPhase.ENDING
                && phase != RoundPhase.LOBBY
                && !roundParticipants.containsKey(playerId);
        if (!cleanupRestore && !removedParticipantRestore) {
            return Optional.empty();
        }

        PlayerStateSnapshot snapshot = playerSnapshots.get(playerId);
        if (snapshot == null) {
            return Optional.empty();
        }
        roleEquipment.removeOwnedHead(player);
        PlayerStateSnapshot.RespawnRestoration restoration = snapshot.restoreForRespawn(player);
        if (!restoration.success()) {
            return Optional.empty();
        }
        playerSnapshots.remove(playerId, snapshot);
        if (phase == RoundPhase.LOBBY && player.isOnline() && !player.isDead()) {
            upsertSurvivor(player);
        }
        return Optional.ofNullable(restoration.location());
    }

    public boolean claimInfectedRespawn(Player player) {
        return player != null && pendingInfectedRespawns.remove(player.getUniqueId());
    }

    public boolean claimHoldingRespawn(Player player) {
        return player != null && pendingHoldingRespawns.remove(player.getUniqueId());
    }

    public boolean isEliminatedInfected(Player player) {
        return infectedLives.isEliminated(player.getUniqueId());
    }

    public RoundActionResult toggleInfectedBuff() {
        if (phase != RoundPhase.ACTIVE) {
            return RoundActionResult.rejected("Infected buffs can only be changed during active play.");
        }

        buffEnabled = !buffEnabled;
        for (Infected infectedRole : List.copyOf(infected)) {
            buffController.apply(infectedRole.getPlayer(), buffEnabled);
        }
        if (buffEnabled) {
            startInfectedTracking();
        } else {
            stopInfectedTracking();
        }
        return RoundActionResult.accepted(buffEnabled
                ? "Infected buffs enabled."
                : "Infected buffs disabled.");
    }

    public void applyCurrentInfectedBuff(Player player) {
        if (phase != RoundPhase.LOBBY
                && phase != RoundPhase.ENDING
                && roleOf(player) == ParticipantRole.INFECTED) {
            buffController.apply(player, phase == RoundPhase.ACTIVE && buffEnabled);
        }
    }

    public void checkWin() {
        applyConclusion(RoundOutcomePolicy.evaluate(
                phase, survivors.size(), infected.size()));
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
        LinkedHashMap<UUID, Player> recipients = new LinkedHashMap<>(roundParticipants);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (queuedPlayers.contains(player.getUniqueId())) {
                recipients.putIfAbsent(player.getUniqueId(), player);
            }
        }
        for (Player player : recipients.values()) {
            if (!player.isOnline()) {
                continue;
            }
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

    public BukkitTask scheduleRoundLater(
            Player player,
            RoundPhase requiredPhase,
            long delayTicks,
            Runnable operation
    ) {
        Objects.requireNonNull(requiredPhase, "requiredPhase");
        Objects.requireNonNull(operation, "operation");
        if (player == null
                || phase != requiredPhase
                || !roundParticipants.containsKey(player.getUniqueId())) {
            return null;
        }
        long expectedRound = roundId;
        BukkitTask[] handle = new BukkitTask[1];
        Runnable guarded = () -> {
            taskRegistry.forget(handle[0]);
            if (expectedRound == roundId
                    && phase == requiredPhase
                    && roundParticipants.containsKey(player.getUniqueId())) {
                operation.run();
            }
        };
        handle[0] = scheduler.runLater(guarded, Math.max(0L, delayTicks));
        trackRoundTask(handle[0]);
        return handle[0];
    }

    public BukkitTask scheduleCleanupLater(Player player, long delayTicks, Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        if (player == null
                || phase != RoundPhase.ENDING
                || !roundParticipants.containsKey(player.getUniqueId())) {
            return null;
        }
        long expectedRound = roundId;
        BukkitTask[] handle = new BukkitTask[1];
        Runnable guarded = () -> {
            taskRegistry.forget(handle[0]);
            if (expectedRound == roundId
                    && phase == RoundPhase.ENDING
                    && roundParticipants.containsKey(player.getUniqueId())) {
                operation.run();
            }
        };
        handle[0] = scheduler.runLater(guarded, Math.max(0L, delayTicks));
        taskRegistry.trackCleanup(handle[0]);
        return handle[0];
    }

    private void startInfectedTracking() {
        if (phase != RoundPhase.ACTIVE || !buffEnabled || infectedTrackingTask != null) {
            return;
        }
        long expectedRound = roundId;
        infectedTrackingTask = scheduler.runRepeating(() -> {
            if (roundId != expectedRound || phase != RoundPhase.ACTIVE || !buffEnabled) {
                return;
            }
            List<Survivor> currentSurvivors = List.copyOf(survivors);
            for (Infected infectedRole : List.copyOf(infected)) {
                Player infectedPlayer = infectedRole.getPlayer();
                Player nearest = nearestSurvivor(infectedPlayer, currentSurvivors);
                if (nearest != null) {
                    infectedPlayer.setCompassTarget(nearest.getLocation());
                }
            }
        }, 0L, 20L);
        trackRoundTask(infectedTrackingTask);
    }

    private void stopInfectedTracking() {
        cancelAndForget(infectedTrackingTask);
        infectedTrackingTask = null;
    }

    private Player nearestSurvivor(Player infectedPlayer, List<Survivor> candidates) {
        Player nearest = null;
        double minimumDistance = Double.MAX_VALUE;
        for (Survivor survivor : candidates) {
            Player candidate = survivor.getPlayer();
            if (candidate.getWorld() != infectedPlayer.getWorld()) {
                continue;
            }
            double distance = infectedPlayer.getLocation().distance(candidate.getLocation());
            if (distance < minimumDistance) {
                minimumDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public void shutdown() {
        roundId++;
        taskRegistry.cancelAll();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        LinkedHashMap<UUID, Player> playersToRestore = new LinkedHashMap<>(lockedParticipants);
        playersToRestore.putAll(roundParticipants);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (playerSnapshots.containsKey(player.getUniqueId())) {
                playersToRestore.putIfAbsent(player.getUniqueId(), player);
            }
        }
        for (Player player : playersToRestore.values()) {
            if (player.isOnline()) {
                restoreSnapshotImmediately(player);
            }
        }
        survivors.clear();
        infected.clear();
        roundParticipants.clear();
        lockedParticipants.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        roundTeleportBypass.clear();
        infectedLives.clear();
        pendingInfectedRespawns.clear();
        pendingHoldingRespawns.clear();
        buffEnabled = false;
        infectedTrackingTask = null;
        phase = RoundPhase.LOBBY;
    }

    private RoundStartValidator.Result validateStart(List<Player> participants) {
        EnumSet<SpawnRole> loadedRoles = EnumSet.noneOf(SpawnRole.class);
        for (SpawnRole role : SpawnRole.values()) {
            List<Location> locations = spawnRepository.loadedLocations(role);
            boolean available = role == SpawnRole.INFECTED_RESPAWN
                    ? locations.stream().anyMatch(InfectedRespawnSelector::isSafe)
                    : !locations.isEmpty();
            if (available) {
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
                plugin.getConfig().getInt("settings.infected-teleport-delay", 10),
                plugin.getConfig().getInt("settings.minimum-players", 2),
                plugin.getConfig().getInt("settings.start-countdown-seconds", 10),
                plugin.getConfig().getInt("settings.round-time-limit-seconds", 0)
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
        return unique.values().stream().filter(player -> !player.isDead()).toList();
    }

    private void assignInfected(Player player) {
        addInfected(roleFactory.createInfected(player));
    }

    private Survivor upsertSurvivor(Player player) {
        infected.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        survivors.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        Survivor survivor = roleFactory.createSurvivor(player);
        survivors.add(survivor);
        return survivor;
    }

    private void clearInfectedRoleState(Player player) {
        buffController.clearRoleState(player);
        roleEquipment.removeOwnedHead(player);
        player.setGlowing(false);
        player.setGameMode(GameMode.SURVIVAL);
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
        if (phase == RoundPhase.COUNTDOWN) {
            int minimumPlayers = plugin.getConfig().getInt("settings.minimum-players", 2);
            if (lockedParticipants.values().stream().filter(Player::isOnline).count()
                    < minimumPlayers) {
                applyConclusion(RoundConclusion.ABANDONED);
            }
            return;
        }
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
            case ABANDONED -> beginEnding(
                    EndReason.ABANDONMENT,
                    plugin.getConfig().getString(
                            "messages.round-abandoned",
                            "&eThe Infected round was abandoned because a team left the event."
                    )
            );
            case CANCELLED -> beginEnding(
                    EndReason.CANCELLATION,
                    plugin.getConfig().getString(
                            "messages.round-cancelled",
                            "&cThe Infected round was cancelled."
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
            taskRegistry.trackGameplay(task);
        }
    }

    private void cancelRoundTasks() {
        taskRegistry.cancelGameplay();
    }

    private void cancelAndForget(BukkitTask task) {
        if (task != null) {
            task.cancel();
            taskRegistry.forget(task);
        }
    }

    private void broadcastCountdown(int seconds) {
        presentation.countdown(
                lockedParticipants.values().stream().filter(Player::isOnline).toList(),
                seconds
        );
        broadcast(plugin.getConfig().getString(
                "messages.countdown",
                "&eThe Infected round starts in &c{time} &eseconds."
        ).replace("{time}", String.valueOf(seconds)));
    }

    private void restoreCapturedPlayers(Set<UUID> capturedPlayerIds) {
        for (UUID playerId : capturedPlayerIds) {
            PlayerStateSnapshot snapshot = playerSnapshots.get(playerId);
            Player player = lockedParticipants.get(playerId);
            if (snapshot != null && player != null && player.isOnline()) {
                roleEquipment.removeOwnedHead(player);
                if (snapshot.restore(player)) {
                    playerSnapshots.remove(playerId, snapshot);
                }
            }
        }
    }

    private void restoreSnapshot(Player player) {
        if (player == null) {
            return;
        }
        if (player.isDead()) {
            return;
        }
        restoreSnapshotImmediately(player);
    }

    private void restoreSnapshotImmediately(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PlayerStateSnapshot snapshot = playerSnapshots.get(playerId);
        if (snapshot != null) {
            roleEquipment.removeOwnedHead(player);
            if (snapshot.restore(player)) {
                playerSnapshots.remove(playerId, snapshot);
            }
        }
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
        CANCELLATION,
        START_FAILURE,
        RESPAWN_FAILURE,
        WINNER
    }
}
