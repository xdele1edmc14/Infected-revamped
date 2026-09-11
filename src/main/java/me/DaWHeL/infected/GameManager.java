package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Infected;
import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public class GameManager {
    private final InfectedPlugin plugin;
    private final SpawnRepository spawnRepository;
    private final TeleportManager teleportManager;
    private final PluginTaskScheduler scheduler;
    private final RoundStartValidator startValidator;
    private final Random random;
    private final ParticipantRoleFactory roleFactory;
    private final InfectedBuffLoadout buffLoadout;
    private final RoundTimeBossBarFactory bossBarFactory;
    private final TrackingCompass trackingCompass;
    private final List<Survivor> survivors = new ArrayList<>();
    private final List<Infected> infected = new ArrayList<>();
    private final Map<UUID, ParticipantRole> roles = new LinkedHashMap<>();
    private final InfectedLifeTracker infectedLives = new InfectedLifeTracker();
    private final RoundStatsTracker roundStats = new RoundStatsTracker();
    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, Player> roundParticipants = new LinkedHashMap<>();
    private final Set<UUID> containedInfected = new LinkedHashSet<>();
    private final Set<UUID> deploymentLockedSurvivors = new LinkedHashSet<>();
    private final Set<UUID> queuedPlayers = new LinkedHashSet<>();
    private final Set<UUID> roundTeleportBypass = new LinkedHashSet<>();
    private final Set<UUID> pendingCleanupRespawns = new LinkedHashSet<>();
    private final Set<BukkitTask> roundTasks = new LinkedHashSet<>();

    private RoundPhase phase = RoundPhase.LOBBY;
    private boolean buffEnabled;
    private long roundId;
    private BukkitTask cleanupTask;
    private BukkitTask trackingCompassTask;
    private RoundSpawnPool roundSpawns;
    private BooleanSupplier roundStartAllowed = () -> true;
    private RoundMode selectedRoundMode;
    private RoundRules activeRoundRules;
    private int roundTimeRemainingSeconds;
    private RoundTimeBossBar roundTimeBossBar;
    private TrackingCompassOverride trackingCompassOverride = TrackingCompassOverride.AUTO;
    private boolean trackingCompassActive;

    public GameManager(InfectedPlugin plugin) {
        this(
                plugin,
                new SpawnRepository(plugin),
                new TeleportManager(plugin),
                new BukkitPluginTaskScheduler(plugin),
                new RoundStartValidator(),
                new Random(),
                new BukkitParticipantRoleFactory(plugin),
                new BukkitInfectedBuffLoadout()
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
                new BukkitParticipantRoleFactory(plugin), new BukkitInfectedBuffLoadout());
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
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random, roleFactory,
                new BukkitInfectedBuffLoadout(), RoundTimeBossBar::new);
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffLoadout buffLoadout
    ) {
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random, roleFactory,
                buffLoadout, RoundTimeBossBar::new);
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffLoadout buffLoadout,
            RoundTimeBossBarFactory bossBarFactory
    ) {
        this(plugin, spawnRepository, teleportManager, scheduler, startValidator, random, roleFactory,
                buffLoadout, bossBarFactory, new TrackingCompass());
    }

    GameManager(
            InfectedPlugin plugin,
            SpawnRepository spawnRepository,
            TeleportManager teleportManager,
            PluginTaskScheduler scheduler,
            RoundStartValidator startValidator,
            Random random,
            ParticipantRoleFactory roleFactory,
            InfectedBuffLoadout buffLoadout,
            RoundTimeBossBarFactory bossBarFactory,
            TrackingCompass trackingCompass
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.spawnRepository = Objects.requireNonNull(spawnRepository, "spawnRepository");
        this.teleportManager = Objects.requireNonNull(teleportManager, "teleportManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.startValidator = Objects.requireNonNull(startValidator, "startValidator");
        this.random = Objects.requireNonNull(random, "random");
        this.roleFactory = Objects.requireNonNull(roleFactory, "roleFactory");
        this.buffLoadout = Objects.requireNonNull(buffLoadout, "buffLoadout");
        this.bossBarFactory = Objects.requireNonNull(bossBarFactory, "bossBarFactory");
        this.trackingCompass = Objects.requireNonNull(trackingCompass, "trackingCompass");
        this.selectedRoundMode = RoundMode.parse(plugin.getConfig().getString(
                "settings.default-round-mode", "deathmatch"));
        this.scoreboardManager = new ScoreboardManager(plugin, this);
        bootstrapOnlinePlayers();
    }

    public InfectedPlugin getPlugin() {
        return plugin;
    }

    public RoundPhase getPhase() {
        return phase;
    }

    public RoundMode selectedRoundMode() {
        return selectedRoundMode;
    }

    public RoundMode activeRoundMode() {
        return currentRules().mode();
    }

    public RoundActionResult cycleRoundMode() {
        if (phase != RoundPhase.LOBBY) {
            return RoundActionResult.rejected("Round mode can only be changed in the lobby.");
        }
        selectedRoundMode = selectedRoundMode.next();
        return RoundActionResult.accepted("Round mode set to " + selectedRoundMode.displayName() + ".");
    }

    public TrackingCompassOverride trackingCompassOverride() {
        return trackingCompassOverride;
    }

    public boolean isTrackingCompassActive() {
        return trackingCompassActive;
    }

    public RoundActionResult setTrackingCompassOverride(TrackingCompassOverride override) {
        Objects.requireNonNull(override, "override");
        if (phase != RoundPhase.ACTIVE) {
            return RoundActionResult.rejected(
                    "Tracking compass controls are only available during active play.");
        }
        trackingCompassOverride = override;
        reconcileTrackingCompassState();
        String message = switch (override) {
            case AUTO -> "Tracking compasses returned to automatic mode.";
            case ON -> "Tracking compasses forced on.";
            case OFF -> "Tracking compasses forced off.";
        };
        return RoundActionResult.accepted(message);
    }

    public int roundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }

    public String roundModeDisplayName() {
        return currentRules().mode().displayName();
    }

    public String roundTimeDisplay() {
        RoundRules rules = currentRules();
        if (!rules.hasTimeLimit()) {
            return "No Limit";
        }
        int seconds = activeRoundRules == null ? rules.timeLimitSeconds() : roundTimeRemainingSeconds;
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    public String roundStartStateKey() {
        RoundRules rules = configuredRules(selectedRoundMode);
        return roundId
                + "|" + rules.mode().name()
                + "|" + rules.startingInfected()
                + "|" + rules.infectedLives()
                + "|" + rules.timeLimitSeconds();
    }

    public void setRoundStartAllowed(BooleanSupplier roundStartAllowed) {
        this.roundStartAllowed = Objects.requireNonNull(roundStartAllowed, "roundStartAllowed");
    }

    public boolean isGameRunning() {
        return phase.isRunning();
    }

    public List<Survivor> getSurvivors() {
        return Collections.unmodifiableList(survivors);
    }

    public List<Infected> getInfected() {
        return Collections.unmodifiableList(infected);
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public boolean isBuffEnabled() {
        return buffEnabled;
    }

    public void setBuffEnabled(boolean buffEnabled) {
        if (this.buffEnabled == buffEnabled) {
            return;
        }
        if (buffEnabled && phase != RoundPhase.ACTIVE) {
            return;
        }
        this.buffEnabled = buffEnabled;
        for (Infected infectedPlayer : infected) {
            Player player = infectedPlayer.getPlayer();
            if (player.isOnline()) {
                applyInfectedBuffState(player, buffEnabled);
            }
        }
    }

    public void addSurvivor(Survivor survivor) {
        Objects.requireNonNull(survivor, "survivor");
        Player player = survivor.getPlayer();
        infected.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        survivors.removeIf(existing -> samePlayer(existing.getPlayer(), player));
        survivors.add(survivor);
        roles.put(player.getUniqueId(), ParticipantRole.SURVIVOR);
        onActiveRosterChanged();
    }

    public boolean registerLobbySurvivor(Player player) {
        if (phase != RoundPhase.LOBBY || player == null || !player.isOnline() || player.isDead()) {
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
        roles.put(player.getUniqueId(), ParticipantRole.INFECTED);
        if (buffEnabled && player.isOnline()) {
            applyInfectedBuffState(player, true);
        }
        onActiveRosterChanged();
    }

    public ParticipantRole roleOf(Player player) {
        if (player == null) {
            return ParticipantRole.NONE;
        }
        return roles.getOrDefault(player.getUniqueId(), ParticipantRole.NONE);
    }

    public boolean isContainedInfected(Player player) {
        return player != null && containedInfected.contains(player.getUniqueId());
    }

    public boolean isDeploymentLockedSurvivor(Player player) {
        return player != null && deploymentLockedSurvivors.contains(player.getUniqueId());
    }

    public boolean isRoundTeleportBypass(Player player) {
        return player != null && roundTeleportBypass.contains(player.getUniqueId());
    }

    public boolean teleportInfectedToRespawn(Player player, Location destination) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");
        boolean teleported = teleportWithContainmentBypass(player, destination);
        if (teleported) {
            containedInfected.remove(player.getUniqueId());
        }
        return teleported;
    }

    public void containInfectedForRespawn(Player player) {
        if (player != null && phase == RoundPhase.ACTIVE && roleOf(player) == ParticipantRole.INFECTED) {
            containedInfected.add(player.getUniqueId());
        }
    }

    public boolean isQueued(Player player) {
        return player != null && queuedPlayers.contains(player.getUniqueId());
    }

    public boolean isCleanupRespawnPending(Player player) {
        return player != null && pendingCleanupRespawns.contains(player.getUniqueId());
    }

    public void completeCleanupRespawn(Player player) {
        if (player == null || !pendingCleanupRespawns.remove(player.getUniqueId())) return;
        resetPlayerState(player);
        if (phase == RoundPhase.LOBBY) registerLobbySurvivor(player);
    }

    public long currentRoundId() {
        return roundId;
    }

    public BukkitTask scheduleRoundTask(Runnable operation, long delayTicks) {
        Objects.requireNonNull(operation, "operation");
        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) return null;
        BukkitTask task = scheduler.runLater(operation, delayTicks);
        trackRoundTask(task);
        return task;
    }

    public RoundStartValidator.Result validateStart() {
        return validateStart(uniqueOnlineLobbyPlayers(), configuredRules(selectedRoundMode));
    }

    public StartResult startGame() {
        if (!roundStartAllowed.getAsBoolean()) {
            return StartResult.rejected("Wait for the active chest operation to finish before starting a round.");
        }
        if (phase != RoundPhase.LOBBY) {
            return StartResult.rejected("The Infected event is already running or cleaning up.");
        }

        List<Player> participants = uniqueOnlineLobbyPlayers();
        RoundRules rules = configuredRules(selectedRoundMode);
        RoundStartValidator.Result validation = validateStart(participants, rules);
        if (!validation.valid()) {
            return StartResult.rejected(validation.errors());
        }

        activeRoundRules = rules;
        roundTimeRemainingSeconds = rules.hasTimeLimit() ? rules.timeLimitSeconds() : 0;
        long startedRound = ++roundId;
        transitionTo(RoundPhase.COUNTDOWN);
        cancelRoundTasks();
        infected.clear();
        infectedLives.clear();
        roundStats.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        deploymentLockedSurvivors.clear();
        roundTeleportBypass.clear();
        roundParticipants.clear();
        participants.forEach(player -> roundParticipants.put(player.getUniqueId(), player));

        survivors.removeIf(survivor -> !roundParticipants.containsKey(survivor.getPlayer().getUniqueId()));
        roles.keySet().retainAll(roundParticipants.keySet());
        int startingInfected = rules.startingInfected();
        AtomicReference<String> immediateFailure = new AtomicReference<>();
        RoundSpawnPool.preload(plugin, spawnRepository, Map.of(
                SpawnRole.SURVIVOR, participants.size() - startingInfected,
                SpawnRole.INFECTED_RELEASE, startingInfected
        )).whenComplete((spawns, error) ->
                immediateFailure.set(onRoundSpawnsPreloaded(startedRound, participants, spawns, error)));
        String failure = immediateFailure.get();
        return failure == null ? StartResult.started() : StartResult.rejected(failure);
    }

    private String onRoundSpawnsPreloaded(
            long expectedRound,
            List<Player> participants,
            RoundSpawnPool spawns,
            Throwable error
    ) {
        if (!isCurrentRound(expectedRound, RoundPhase.COUNTDOWN)) {
            if (spawns != null) {
                spawns.releaseTickets(plugin);
            }
            return null;
        }
        if (error != null || spawns == null) {
            String message = spawnPreparationFailure(error);
            beginEnding(EndReason.START_FAILURE, "&c" + message + " The round is being reset.");
            return message;
        }

        roundSpawns = spawns;
        return beginDeployment(expectedRound, participants, spawns);
    }

    private static String spawnPreparationFailure(Throwable error) {
        if (error == null) {
            return "Required spawn chunks could not be loaded.";
        }
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String detail = cause.getMessage();
        return detail == null || detail.isBlank()
                ? "Required spawn chunks could not be loaded."
                : "Spawn preparation failed: " + detail;
    }

    private String beginDeployment(long expectedRound, List<Player> participants, RoundSpawnPool spawns) {
        List<Player> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        int startingInfected = requireActiveRules().startingInfected();
        Location holdingSpawn = spawns.holdingSpawn().orElseThrow();

        for (int index = 0; index < startingInfected; index++) {
            Player player = shuffled.get(index);
            survivors.removeIf(survivor -> samePlayer(survivor.getPlayer(), player));
            assignInfected(player);
            containedInfected.add(player.getUniqueId());
            if (!teleportWithContainmentBypass(player, holdingSpawn)) {
                String error = "An initial infected could not be teleported to the holding spawn.";
                beginEnding(EndReason.START_FAILURE, "&c" + error + " The round is being reset.");
                return error;
            }
        }

        showRoundTitle(
                "messages.get-ready",
                "&e&lGET READY!",
                "",
                10,
                40,
                10,
                null,
                1.0f,
                1.0f
        );

        broadcast(plugin.getConfig().getString(
                "messages.game-start",
                "&eThe Infected game has started with &c{zombies} &ezombies!"
        ).replace("{zombies}", String.valueOf(startingInfected)));

        List<Player> survivorPlayers = survivors.stream()
                .map(Survivor::getPlayer)
                .filter(Player::isOnline)
                .toList();
        survivorPlayers.forEach(player -> deploymentLockedSurvivors.add(player.getUniqueId()));
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 10);
        long delayTicks = plugin.getConfig().getLong("settings.teleport-delay", 5L);
        BukkitTask task = teleportManager.teleportPlayersBatch(
                SpawnRole.SURVIVOR,
                spawns.locations(SpawnRole.SURVIVOR),
                survivorPlayers,
                batchSize,
                delayTicks,
                player -> isCurrentParticipant(player, ParticipantRole.SURVIVOR, expectedRound,
                        RoundPhase.COUNTDOWN),
                result -> onSurvivorsTeleported(expectedRound, result)
        );
        trackRoundTask(task);
        return null;
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

        deploymentLockedSurvivors.clear();
        transitionTo(RoundPhase.HEADSTART);
        int delaySeconds = Math.max(3,
                plugin.getConfig().getInt("settings.infected-teleport-delay", 10));
        broadcast(plugin.getConfig().getString(
                "messages.zombies-teleporting",
                "&cInfected zombies will be teleported in &e{time} &cseconds..."
        ).replace("{time}", String.valueOf(delaySeconds)));
        for (int countdown = 3; countdown >= 1; countdown--) {
            int displayedSecond = countdown;
            BukkitTask countdownTask = scheduler.runLater(
                    () -> showInfectedReleaseCountdown(expectedRound, displayedSecond),
                    (delaySeconds - countdown) * 20L
            );
            trackRoundTask(countdownTask);
        }
        BukkitTask delay = scheduler.runLater(
                () -> beginInfectedRelease(expectedRound),
                delaySeconds * 20L
        );
        trackRoundTask(delay);
    }

    private void showInfectedReleaseCountdown(long expectedRound, int second) {
        if (!isCurrentRound(expectedRound, RoundPhase.HEADSTART)) {
            return;
        }
        showRoundTitle(
                "messages.infected-release-countdown",
                "&e&l{time}",
                "",
                0,
                20,
                0,
                "block.note_block.hat",
                1.0f,
                1.0f,
                String.valueOf(second)
        );
    }

    private void beginInfectedRelease(long expectedRound) {
        if (!isCurrentRound(expectedRound, RoundPhase.HEADSTART)) {
            return;
        }
        List<Player> infectedPlayers = infected.stream()
                .map(Infected::getPlayer)
                .filter(Player::isOnline)
                .toList();
        int batchSize = plugin.getConfig().getInt("settings.teleport-batch-size", 10);
        long delayTicks = plugin.getConfig().getLong("settings.teleport-delay", 5L);
        BukkitTask task = teleportManager.teleportPlayersBatch(
                SpawnRole.INFECTED_RELEASE,
                roundSpawnLocations(SpawnRole.INFECTED_RELEASE),
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
        showRoundTitle(
                "messages.infection-started",
                "&c&lINFECTION HAS STARTED!",
                "",
                10,
                50,
                10,
                "block.note_block.bell",
                1.0f,
                0.5f
        );
        startRoundPresentation(expectedRound);
        checkWin();
    }

    public boolean stopGame() {
        return beginEnding(EndReason.ADMIN_STOP, null);
    }

    public boolean cancelForUnavailableInfectedRespawn() {
        return beginEnding(
                EndReason.RESPAWN_FAILURE,
                plugin.getConfig().getString(
                        "messages.infected-respawn-unavailable",
                        "&cNo infected respawn is available. The round has been cancelled."
                )
        );
    }

    private boolean beginEnding(EndReason reason, String failureMessage) {
        if (phase == RoundPhase.LOBBY || phase == RoundPhase.ENDING) {
            return false;
        }

        closeRoundPresentation();
        setBuffEnabled(false);
        transitionTo(RoundPhase.ENDING);
        roundId++;
        cancelRoundTasks();
        releaseRoundSpawns();
        containedInfected.clear();
        deploymentLockedSurvivors.clear();
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
        int batchSize = Math.max(1, plugin.getConfig().getInt("settings.teleport-batch-size", 10));
        long period = Math.max(1L, plugin.getConfig().getLong("settings.teleport-delay", 5L));

        if (queue.isComplete()) {
            finishCleanup(processed);
            return true;
        }

        BukkitTask[] taskHandle = new BukkitTask[1];
        Runnable cleanupOperation = () -> {
            for (Player player : queue.nextBatch(batchSize)) {
                if (player.isOnline() && !processed.contains(player.getUniqueId())) {
                    resetPlayerState(player);
                    if (player.isDead()) {
                        pendingCleanupRespawns.add(player.getUniqueId());
                    } else {
                        processed.add(player.getUniqueId());
                    }
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
        roles.clear();
        roundParticipants.clear();
        queuedPlayers.clear();
        infectedLives.clear();
        roundStats.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (pendingCleanupRespawns.contains(player.getUniqueId())) continue;
            if (processed.add(player.getUniqueId()) && player.isOnline()) {
                resetPlayerState(player);
            }
            if (player.isOnline() && !player.isDead()) {
                upsertSurvivor(player);
            }
        }
        activeRoundRules = null;
        roundTimeRemainingSeconds = 0;
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
        org.bukkit.scoreboard.ScoreboardManager scoreboards = plugin.getServer().getScoreboardManager();
        if (scoreboards != null) {
            player.setScoreboard(scoreboards.getMainScoreboard());
        }
        scoreboardManager.forgetPlayer(player);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setHelmet(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        if (!player.isDead()) {
            player.setHealth(player.getMaxHealth());
        }
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector());
    }

    public void handleHit(Player attacker, Player victim) {
        if (phase == RoundPhase.ACTIVE
                && roleOf(attacker) == ParticipantRole.INFECTED
                && roleOf(victim) == ParticipantRole.SURVIVOR) {
            infectPlayer(victim, attacker, true);
        }
    }

    public void infectPlayer(Player victim, boolean announce) {
        infectPlayer(victim, null, announce);
    }

    public void infectPlayer(Player victim, Player attacker, boolean announce) {
        if (phase != RoundPhase.ACTIVE || roleOf(victim) != ParticipantRole.SURVIVOR) {
            return;
        }
        if (roleOf(attacker) == ParticipantRole.INFECTED) {
            roundStats.recordInfection(attacker.getUniqueId());
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
                roundSpawnLocations(SpawnRole.INFECTED_RESPAWN), random);
        if (respawn.isEmpty()) {
            cancelForUnavailableInfectedRespawn();
            return RoundActionResult.rejected(
                    "No infected respawn was available, so the round was cancelled.");
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
        roles.remove(player.getUniqueId());
        infectedLives.remove(player.getUniqueId());
        roundParticipants.remove(player.getUniqueId());
        containedInfected.remove(player.getUniqueId());
        deploymentLockedSurvivors.remove(player.getUniqueId());
        roundTeleportBypass.remove(player.getUniqueId());
        queuedPlayers.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ChatColor.YELLOW
                + "A round is already running. You are queued as a spectator for the next round.");
        onActiveRosterChanged();
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
        player.setGameMode(GameMode.SPECTATOR);
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
        roles.remove(player.getUniqueId());
        infectedLives.remove(player.getUniqueId());
        queuedPlayers.remove(player.getUniqueId());
        roundParticipants.remove(player.getUniqueId());
        containedInfected.remove(player.getUniqueId());
        deploymentLockedSurvivors.remove(player.getUniqueId());
        roundTeleportBypass.remove(player.getUniqueId());
        roundStats.remove(player.getUniqueId());
        onActiveRosterChanged();
        return departedRole;
    }

    public void recordSurvivorKill(Player player) {
        if (phase == RoundPhase.ACTIVE && roleOf(player) == ParticipantRole.SURVIVOR) {
            roundStats.recordKill(player.getUniqueId());
        }
    }

    public int kills(Player player) {
        return player == null ? 0 : roundStats.kills(player.getUniqueId());
    }

    public int infections(Player player) {
        return player == null ? 0 : roundStats.infections(player.getUniqueId());
    }

    public int remainingInfectedLives(Player player) {
        return player == null ? 0 : infectedLives.remainingLives(player.getUniqueId());
    }

    public int configuredInfectedLives() {
        return getConfiguredInfectedLives();
    }

    public int configuredStartingInfected() {
        return currentRules().startingInfected();
    }

    public boolean handleInfectedDeath(Player player) {
        if (phase != RoundPhase.ACTIVE || roleOf(player) != ParticipantRole.INFECTED) {
            return false;
        }
        boolean hasRemainingLife = infectedLives.consumeLife(player.getUniqueId());
        if (!hasRemainingLife) {
            infected.removeIf(entry -> samePlayer(entry.getPlayer(), player));
            roles.remove(player.getUniqueId());
            onActiveRosterChanged();
            applyConclusion(RoundOutcomePolicy.evaluate(
                    phase, survivors.size(), infected.size(), RosterChange.INFECTED_ELIMINATION));
        }
        return hasRemainingLife;
    }

    public boolean isEliminatedInfected(Player player) {
        return infectedLives.isEliminated(player.getUniqueId());
    }

    public java.util.Optional<Location> roundHoldingSpawn() {
        return roundSpawns == null ? java.util.Optional.empty() : roundSpawns.holdingSpawn();
    }

    public List<Location> roundSpawnLocations(SpawnRole role) {
        return roundSpawns == null ? List.of() : roundSpawns.locations(role);
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

    public void shutdown() {
        roundId++;
        closeRoundPresentation();
        setBuffEnabled(false);
        cancelRoundTasks();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        releaseRoundSpawns();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isOnline()) {
                resetPlayerState(player);
            }
        }
        scoreboardManager.clearCachedBoards();
        survivors.clear();
        infected.clear();
        roles.clear();
        roundParticipants.clear();
        queuedPlayers.clear();
        containedInfected.clear();
        deploymentLockedSurvivors.clear();
        roundTeleportBypass.clear();
        pendingCleanupRespawns.clear();
        infectedLives.clear();
        roundStats.clear();
        activeRoundRules = null;
        roundTimeRemainingSeconds = 0;
        phase = RoundPhase.LOBBY;
    }

    private void bootstrapOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            resetPlayerState(player);
            upsertSurvivor(player);
        }
    }

    private RoundStartValidator.Result validateStart(List<Player> participants, RoundRules rules) {
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
                rules.startingInfected(),
                plugin.getConfig().getInt("settings.teleport-batch-size", 10),
                plugin.getConfig().getInt("settings.teleport-delay", 5),
                plugin.getConfig().getInt("settings.infected-teleport-delay", 10)
        ));
    }

    private List<Player> uniqueOnlineLobbyPlayers() {
        LinkedHashMap<UUID, Player> unique = new LinkedHashMap<>();
        for (Survivor survivor : survivors) {
            Player player = survivor.getPlayer();
            if (player.isOnline() && !player.isDead()) {
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
        roles.put(player.getUniqueId(), ParticipantRole.SURVIVOR);
        onActiveRosterChanged();
    }

    private void clearInfectedRoleState(Player player) {
        containedInfected.remove(player.getUniqueId());
        player.setGlowing(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setHelmet(null);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.getInventory().remove(Material.COMPASS);
    }

    private void applyInfectedBuffState(Player player, boolean enabled) {
        if (enabled) {
            buffLoadout.applyBoosted(player);
            return;
        }

        buffLoadout.applyBase(player);
    }

    private void startTrackingCompassTracker() {
        if (trackingCompassTask == null) {
            trackingCompassTask = scheduler.runRepeating(this::updateTrackingCompassTargets, 0L, 20L);
        }
    }

    private void stopTrackingCompassTracker() {
        if (trackingCompassTask != null) {
            trackingCompassTask.cancel();
            trackingCompassTask = null;
        }
    }

    private void updateTrackingCompassTargets() {
        if (phase != RoundPhase.ACTIVE) {
            stopTrackingCompassTracker();
            return;
        }

        reconcileTrackingCompassState();
        if (!trackingCompassActive) {
            return;
        }

        List<SurvivorPosition> survivorPositions = new ArrayList<>(survivors.size());
        for (Survivor survivor : survivors) {
            Player player = survivor.getPlayer();
            if (player.isOnline()) {
                Location location = player.getLocation();
                survivorPositions.add(new SurvivorPosition(
                        location.getWorld(), location.getX(), location.getY(), location.getZ(), location));
            }
        }

        for (Infected infectedPlayer : infected) {
            Player player = infectedPlayer.getPlayer();
            if (!player.isOnline()) {
                continue;
            }
            Location infectedLocation = player.getLocation();
            Location nearest = null;
            double nearestDistanceSquared = Double.MAX_VALUE;
            for (SurvivorPosition survivor : survivorPositions) {
                if (survivor.world() != infectedLocation.getWorld()) {
                    continue;
                }
                double x = infectedLocation.getX() - survivor.x();
                double y = infectedLocation.getY() - survivor.y();
                double z = infectedLocation.getZ() - survivor.z();
                double distanceSquared = x * x + y * y + z * z;
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearest = survivor.location();
                }
            }
            if (nearest != null) {
                player.setCompassTarget(nearest);
            }
        }
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

    private void releaseRoundSpawns() {
        if (roundSpawns != null) {
            roundSpawns.releaseTickets(plugin);
            roundSpawns = null;
        }
    }

    private int getConfiguredInfectedLives() {
        return currentRules().infectedLives();
    }

    private RoundRules configuredRules(RoundMode mode) {
        return RoundRules.from(plugin.getConfig(), mode);
    }

    private RoundRules currentRules() {
        return activeRoundRules == null ? configuredRules(selectedRoundMode) : activeRoundRules;
    }

    private RoundRules requireActiveRules() {
        return Objects.requireNonNull(activeRoundRules, "Active round rules have not been captured.");
    }

    private void startRoundPresentation(long expectedRound) {
        RoundRules rules = requireActiveRules();
        roundTimeRemainingSeconds = rules.hasTimeLimit() ? rules.timeLimitSeconds() : 0;
        reconcileTrackingCompassState();
        startTrackingCompassTracker();
        if (!rules.hasTimeLimit()) {
            return;
        }
        if (rules.bossBarEnabled()) {
            roundTimeBossBar = bossBarFactory.create(
                    plugin, rules.bossBarTitle(), rules.bossBarColor(), rules.bossBarStyle());
            updateTimeBossBar();
        }
        BukkitTask timer = scheduler.runRepeating(
                () -> tickRoundTimer(expectedRound),
                20L,
                20L
        );
        trackRoundTask(timer);
    }

    private void tickRoundTimer(long expectedRound) {
        if (!isCurrentRound(expectedRound, RoundPhase.ACTIVE)
                || activeRoundRules == null
                || !activeRoundRules.hasTimeLimit()) {
            return;
        }
        roundTimeRemainingSeconds = Math.max(0, roundTimeRemainingSeconds - 1);
        updateTimeBossBar();
        reconcileTrackingCompassState();
        if (roundTimeRemainingSeconds == 0) {
            announceTimeLimitSurvivorWin();
        }
    }

    private void updateTimeBossBar() {
        if (roundTimeBossBar != null && activeRoundRules != null) {
            roundTimeBossBar.update(
                    roundTimeRemainingSeconds,
                    activeRoundRules.timeLimitSeconds(),
                    activeParticipantPlayers()
            );
        }
    }

    private List<Player> activeParticipantPlayers() {
        List<Player> players = new ArrayList<>(survivors.size() + infected.size());
        survivors.forEach(role -> players.add(role.getPlayer()));
        infected.forEach(role -> players.add(role.getPlayer()));
        return players;
    }

    private void closeRoundPresentation() {
        stopTrackingCompassTracker();
        disableTrackingCompasses(false);
        trackingCompassOverride = TrackingCompassOverride.AUTO;
        if (roundTimeBossBar != null) {
            roundTimeBossBar.close();
            roundTimeBossBar = null;
        }
    }

    private void announceTimeLimitSurvivorWin() {
        String messagePath = "messages.time-limit-survived";
        broadcast(plugin.getConfig().getString(messagePath + ".chat",
                "&2The time limit expired with survivors remaining! Survivors win!"));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendTitle(
                    color(plugin.getConfig().getString(messagePath + ".title", "&2Survivors Win!")),
                    color(plugin.getConfig().getString(messagePath + ".subtitle",
                            "&7The survivors outlasted the time limit!")),
                    10,
                    80,
                    20
            );
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
        }
        beginEnding(EndReason.WINNER, null);
    }

    private void broadcast(String message) {
        plugin.getServer().broadcastMessage(color(message));
    }

    private void onActiveRosterChanged() {
        if (phase == RoundPhase.ACTIVE) {
            reconcileTrackingCompassState();
        }
    }

    private void reconcileTrackingCompassState() {
        if (phase != RoundPhase.ACTIVE || activeRoundRules == null) {
            disableTrackingCompasses(false);
            return;
        }

        boolean shouldBeActive = activeRoundRules.hasTimeLimit()
                ? TrackingCompassPolicy.timeLimit(
                        activeRoundRules.trackingCompassEnabled(),
                        trackingCompassOverride,
                        roundTimeRemainingSeconds,
                        activeRoundRules.trackingCompassActivationSeconds())
                : TrackingCompassPolicy.deathmatch(
                        activeRoundRules.trackingCompassEnabled(),
                        trackingCompassOverride,
                        infected.size(),
                        activeRoundRules.trackingCompassDisableBelowInfected());

        if (!shouldBeActive) {
            disableTrackingCompasses(trackingCompassActive);
            return;
        }

        boolean changed = !trackingCompassActive;
        trackingCompassActive = true;
        for (Infected infectedPlayer : infected) {
            Player player = infectedPlayer.getPlayer();
            if (player.isOnline()) {
                trackingCompass.ensurePresent(player);
            }
        }
        if (changed) {
            broadcast(plugin.getConfig().getString(
                    "messages.tracking-compass.enabled",
                    "&eZombie tracking compasses are now &aenabled&e."));
        }
    }

    private void disableTrackingCompasses(boolean announce) {
        trackingCompassActive = false;
        for (Infected infectedPlayer : infected) {
            Player player = infectedPlayer.getPlayer();
            if (player.isOnline()) {
                trackingCompass.remove(player);
            }
        }
        if (announce) {
            broadcast(plugin.getConfig().getString(
                    "messages.tracking-compass.disabled",
                    "&eZombie tracking compasses are now &cdisabled&e."));
        }
    }

    private void showRoundTitle(
            String configPath,
            String defaultTitle,
            String defaultSubtitle,
            int defaultFadeIn,
            int defaultStay,
            int defaultFadeOut,
            String defaultSound,
            float defaultVolume,
            float defaultPitch
    ) {
        showRoundTitle(configPath, defaultTitle, defaultSubtitle, defaultFadeIn, defaultStay,
                defaultFadeOut, defaultSound, defaultVolume, defaultPitch, null);
    }

    private void showRoundTitle(
            String configPath,
            String defaultTitle,
            String defaultSubtitle,
            int defaultFadeIn,
            int defaultStay,
            int defaultFadeOut,
            String defaultSound,
            float defaultVolume,
            float defaultPitch,
            String countdownSecond
    ) {
        String title = plugin.getConfig().getString(configPath + ".title", defaultTitle);
        String subtitle = plugin.getConfig().getString(configPath + ".subtitle", defaultSubtitle);
        if (countdownSecond != null) {
            title = title.replace("{time}", countdownSecond);
            subtitle = subtitle.replace("{time}", countdownSecond);
        }
        int fadeIn = plugin.getConfig().getInt(configPath + ".fade-in", defaultFadeIn);
        int stay = plugin.getConfig().getInt(configPath + ".stay", defaultStay);
        int fadeOut = plugin.getConfig().getInt(configPath + ".fade-out", defaultFadeOut);
        String sound = plugin.getConfig().getString(configPath + ".sound", defaultSound);
        float volume = (float) plugin.getConfig().getDouble(configPath + ".volume", defaultVolume);
        float pitch = (float) plugin.getConfig().getDouble(configPath + ".pitch", defaultPitch);

        for (Player player : roundParticipants.values()) {
            if (!player.isOnline()) {
                continue;
            }
            player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
            if (sound != null && !sound.isBlank()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static boolean samePlayer(Player first, Player second) {
        return first.getUniqueId().equals(second.getUniqueId());
    }

    private record SurvivorPosition(
            World world,
            double x,
            double y,
            double z,
            Location location
    ) {
    }

    interface InfectedBuffLoadout {
        void applyBoosted(Player player);

        void applyBase(Player player);
    }

    @FunctionalInterface
    interface RoundTimeBossBarFactory {
        RoundTimeBossBar create(InfectedPlugin plugin, String title, BarColor color, BarStyle style);
    }

    private static final class BukkitInfectedBuffLoadout implements InfectedBuffLoadout {
        @Override
        public void applyBoosted(Player player) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false, true));
        }

        @Override
        public void applyBase(Player player) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, true));
        }
    }

    private enum EndReason {
        ADMIN_STOP,
        ABANDONMENT,
        START_FAILURE,
        RESPAWN_FAILURE,
        WINNER
    }
}
