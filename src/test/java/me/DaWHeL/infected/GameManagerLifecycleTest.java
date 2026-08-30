package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameManagerLifecycleTest {
    private InfectedPlugin plugin;
    private Server server;
    private YamlConfiguration config;
    private SpawnRepository spawnRepository;
    private TeleportManager teleportManager;
    private PluginTaskScheduler scheduler;
    private GameManager gameManager;
    private ParticipantRoleFactory roleFactory;
    private InfectedBuffController buffController;
    private MatchPresentationService presentation;
    private InfectedRoleEquipment roleEquipment;
    private Map<SpawnRole, Consumer<TeleportBatchResult>> completions;
    private Map<SpawnRole, List<Player>> teleportPlayers;
    private Runnable[] delayedRelease;
    private Map<Long, List<Runnable>> laterTasks;
    private List<Runnable> repeatingTasks;
    private List<BukkitTask> repeatingHandles;
    private List<World> retainedWorlds;
    private Consumer<Player> beforeReleaseTeleport;
    private BiConsumer<Player, Boolean> afterReleaseTeleport;

    @BeforeEach
    void setUp() {
        plugin = mock(InfectedPlugin.class);
        server = mock(Server.class);
        config = new YamlConfiguration();
        spawnRepository = mock(SpawnRepository.class);
        teleportManager = mock(TeleportManager.class);
        scheduler = mock(PluginTaskScheduler.class);
        roleFactory = mock(ParticipantRoleFactory.class);
        buffController = mock(InfectedBuffController.class);
        presentation = mock(MatchPresentationService.class);
        roleEquipment = mock(InfectedRoleEquipment.class);
        repeatingTasks = new ArrayList<>();
        repeatingHandles = new ArrayList<>();
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(scheduler.runLater(any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        doAnswer(invocation -> {
            repeatingTasks.add(invocation.getArgument(0));
            BukkitTask handle = mock(BukkitTask.class);
            repeatingHandles.add(handle);
            return handle;
        }).when(scheduler).runRepeating(any(Runnable.class), anyLong(), anyLong());
        gameManager = org.mockito.Mockito.spy(new GameManager(
                plugin,
                spawnRepository,
                teleportManager,
                scheduler,
                new RoundStartValidator(),
                new Random(1),
                roleFactory,
                buffController,
                presentation,
                roleEquipment
        ));
        doNothing().when(gameManager).resetPlayerState(any(Player.class));
        when(roleFactory.createInfected(any(Player.class))).thenAnswer(invocation -> {
            me.DaWHeL.infected.Roles.Infected role = mock(me.DaWHeL.infected.Roles.Infected.class);
            when(role.getPlayer()).thenReturn(invocation.getArgument(0));
            return role;
        });
        when(roleFactory.createSurvivor(any(Player.class))).thenAnswer(invocation -> mockSurvivor(
                invocation.getArgument(0)));
        completions = new EnumMap<>(SpawnRole.class);
        teleportPlayers = new EnumMap<>(SpawnRole.class);
        delayedRelease = new Runnable[1];
        laterTasks = new java.util.LinkedHashMap<>();
        retainedWorlds = new ArrayList<>();
        doAnswer(invocation -> {
            SpawnRole role = invocation.getArgument(0);
            teleportPlayers.put(role, List.copyOf(invocation.getArgument(1)));
            completions.put(role, invocation.getArgument(5));
            return mock(BukkitTask.class);
        }).when(teleportManager).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any());
        doAnswer(invocation -> {
            teleportPlayers.put(invocation.getArgument(0), List.copyOf(invocation.getArgument(1)));
            beforeReleaseTeleport = invocation.getArgument(5);
            afterReleaseTeleport = invocation.getArgument(6);
            completions.put(invocation.getArgument(0), invocation.getArgument(7));
            return mock(BukkitTask.class);
        }).when(teleportManager).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any(), any(), any());
        doAnswer(invocation -> {
            delayedRelease[0] = invocation.getArgument(0);
            long delayTicks = invocation.getArgument(1);
            laterTasks.computeIfAbsent(delayTicks, ignored -> new ArrayList<>())
                    .add(invocation.getArgument(0));
            return mock(BukkitTask.class);
        }).when(scheduler).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void lifecyclePresentsCountdownDeploymentAndActiveRoleBoundaries() {
        configureValidSetup(3, 1);
        config.set("settings.start-countdown-seconds", 3);
        Player first = addLobbyPlayer("presentation-first");
        Player second = addLobbyPlayer("presentation-second");
        Player third = addLobbyPlayer("presentation-third");

        assertTrue(gameManager.startGame().success());
        verify(presentation).countdown(List.of(first, second, third), 3);
        repeatingTasks.getFirst().run();
        verify(presentation).countdown(List.of(first, second, third), 2);
        repeatingTasks.getFirst().run();
        verify(presentation).countdown(List.of(first, second, third), 1);
        repeatingTasks.getFirst().run();
        verify(presentation).deployment(List.of(first, second, third));

        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        Player queued = player("presentation-queued");
        when(server.getPlayer(queued.getUniqueId())).thenReturn(queued);
        assertTrue(gameManager.queueLateJoin(queued));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        ArgumentCaptor<java.util.Collection<Player>> survivors = ArgumentCaptor.forClass(
                java.util.Collection.class);
        ArgumentCaptor<java.util.Collection<Player>> infected = ArgumentCaptor.forClass(
                java.util.Collection.class);
        ArgumentCaptor<java.util.Collection<Player>> spectators = ArgumentCaptor.forClass(
                java.util.Collection.class);
        verify(presentation).active(survivors.capture(), infected.capture(), spectators.capture());
        assertEquals(2, survivors.getValue().size());
        assertEquals(1, infected.getValue().size());
        assertEquals(List.of(queued), List.copyOf(spectators.getValue()));
    }

    @Test
    void completedSurvivorTeleportTaskIsForgottenBeforeRoundStop() {
        configureValidSetup(3, 1);
        addLobbyPlayer("forget-first");
        addLobbyPlayer("forget-second");
        addLobbyPlayer("forget-third");
        BukkitTask survivorTeleport = mock(BukkitTask.class);
        doAnswer(invocation -> {
            SpawnRole role = invocation.getArgument(0);
            teleportPlayers.put(role, List.copyOf(invocation.getArgument(1)));
            completions.put(role, invocation.getArgument(5));
            return survivorTeleport;
        }).when(teleportManager).teleportPlayersBatch(
                eq(SpawnRole.SURVIVOR), anyList(), anyInt(), anyLong(), any(), any());
        assertTrue(gameManager.startGame().success());

        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        assertTrue(gameManager.stopGame());

        verify(survivorTeleport, never()).cancel();
    }

    @Test
    void locksRosterUntilCountdownCompletes() {
        configureValidSetup(3, 1);
        config.set("settings.start-countdown-seconds", 1);
        Player first = addLobbyPlayer("locked-first");
        Player second = addLobbyPlayer("locked-second");
        Player third = addLobbyPlayer("locked-third");

        StartResult result = gameManager.startGame();
        Player late = addLobbyPlayer("late-during-countdown");

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(RoundPhase.COUNTDOWN, gameManager.getPhase()),
                () -> assertTrue(gameManager.getInfected().isEmpty()),
                () -> assertFalse(teleportPlayers.containsKey(SpawnRole.SURVIVOR)),
                () -> assertEquals(1, repeatingTasks.size())
        );

        repeatingTasks.getFirst().run();

        List<Player> deployed = new ArrayList<>(teleportPlayers.get(SpawnRole.SURVIVOR));
        deployed.addAll(gameManager.getInfected().stream()
                .map(me.DaWHeL.infected.Roles.Infected::getPlayer)
                .toList());
        assertAll(
                () -> assertEquals(RoundPhase.DEPLOYING, gameManager.getPhase()),
                () -> assertEquals(Set.of(first, second, third), Set.copyOf(deployed)),
                () -> assertFalse(deployed.contains(late))
        );
    }

    @Test
    void countdownDepartureContinuesWhileMinimumRemains() {
        configureValidSetup(4, 1);
        config.set("settings.minimum-players", 3);
        config.set("settings.start-countdown-seconds", 1);
        Player departing = addLobbyPlayer("countdown-departing");
        addLobbyPlayer("countdown-staying-one");
        addLobbyPlayer("countdown-staying-two");
        addLobbyPlayer("countdown-staying-three");
        assertTrue(gameManager.startGame().success());

        gameManager.handleQuit(departing);

        assertEquals(RoundPhase.COUNTDOWN, gameManager.getPhase());
        repeatingTasks.getFirst().run();
        List<Player> survivorsDeployed = teleportPlayers.get(SpawnRole.SURVIVOR);
        assertAll(
                () -> assertEquals(RoundPhase.DEPLOYING, gameManager.getPhase()),
                () -> assertFalse(survivorsDeployed.contains(departing)),
                () -> assertEquals(2, survivorsDeployed.size())
        );
    }

    @Test
    void countdownDepartureBelowMinimumAbandonsRound() {
        configureValidSetup(3, 1);
        config.set("settings.minimum-players", 3);
        config.set("settings.start-countdown-seconds", 5);
        Player departing = addLobbyPlayer("below-minimum-departing");
        addLobbyPlayer("below-minimum-one");
        addLobbyPlayer("below-minimum-two");
        assertTrue(gameManager.startGame().success());

        gameManager.handleQuit(departing);

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("abandoned"));
    }

    @Test
    void zeroCountdownDeploysWithoutSchedulingATick() {
        configureValidSetup(3, 1);
        addLobbyPlayer("zero-countdown-one");
        addLobbyPlayer("zero-countdown-two");
        addLobbyPlayer("zero-countdown-three");

        StartResult result = gameManager.startGame();

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(RoundPhase.DEPLOYING, gameManager.getPhase()),
                () -> assertTrue(repeatingTasks.isEmpty()),
                () -> assertTrue(teleportPlayers.containsKey(SpawnRole.SURVIVOR))
        );
    }

    @Test
    void countdownExpiryRevalidatesArenaBeforeDeployment() {
        configureValidSetup(3, 1);
        config.set("settings.start-countdown-seconds", 1);
        addLobbyPlayer("revalidate-one");
        addLobbyPlayer("revalidate-two");
        addLobbyPlayer("revalidate-three");
        assertTrue(gameManager.startGame().success());
        when(spawnRepository.loadedLocations(SpawnRole.SURVIVOR)).thenReturn(List.of());

        repeatingTasks.getFirst().run();

        assertAll(
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase()),
                () -> assertFalse(teleportPlayers.containsKey(SpawnRole.SURVIVOR))
        );
    }

    @Test
    void staleCountdownTickDoesNotMutateOrBroadcast() {
        configureValidSetup(3, 1);
        config.set("settings.start-countdown-seconds", 2);
        addLobbyPlayer("stale-countdown-one");
        addLobbyPlayer("stale-countdown-two");
        addLobbyPlayer("stale-countdown-three");
        assertTrue(gameManager.startGame().success());
        Runnable staleTick = repeatingTasks.getFirst();
        assertTrue(gameManager.stopGame());
        clearInvocations(server);

        staleTick.run();

        assertAll(
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase()),
                () -> assertFalse(teleportPlayers.containsKey(SpawnRole.SURVIVOR))
        );
        verify(server, never()).broadcastMessage(any(String.class));
    }

    @Test
    void invalidStartDoesNotMutatePhaseOrRosters() {
        Player first = addLobbyPlayer("first");
        Player second = addLobbyPlayer("second");
        config.set("settings.starting-zombies", 2);
        config.set("settings.teleport-batch-size", 5);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);

        StartResult result = gameManager.startGame();

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("holding spawn")),
                () -> assertTrue(result.message().contains("lower than the participant count")),
                () -> assertEquals(RoundPhase.LOBBY, gameManager.getPhase()),
                () -> assertEquals(List.of(first, second), gameManager.getSurvivors().stream()
                        .map(Survivor::getPlayer)
                        .toList()),
                () -> assertTrue(gameManager.getInfected().isEmpty())
        );
        verify(teleportManager, never()).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any());
    }

    @Test
    void startRejectsConfiguredButUnsafeInfectedRespawnPoints() {
        configureValidSetup(2, 1);
        Location unsafe = safeLocation();
        when(unsafe.getWorld().getBlockAt(unsafe.getBlockX(), unsafe.getBlockY() - 1, unsafe.getBlockZ())
                .isPassable()).thenReturn(true);
        when(spawnRepository.loadedLocations(SpawnRole.INFECTED_RESPAWN)).thenReturn(List.of(unsafe));
        addLobbyPlayer("unsafe-respawn-one");
        addLobbyPlayer("unsafe-respawn-two");

        StartResult result = gameManager.startGame();

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("Infected respawn spawns")),
                () -> assertEquals(RoundPhase.LOBBY, gameManager.getPhase())
        );
    }

    @Test
    void lobbyRejectsZombieToggleAndPlayerRemoval() {
        Player target = addLobbyPlayer("lobby-phase-target");

        RoundActionResult removal = gameManager.removePlayer(target);

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertFalse(removal.success()),
                () -> assertTrue(removal.message().contains("deployment")),
                () -> assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(target))
        );
    }

    @Test
    void countdownAllowsRemovalButRejectsZombieToggle() {
        configureValidSetup(4, 1);
        config.set("settings.start-countdown-seconds", 5);
        for (int index = 0; index < 4; index++) {
            addLobbyPlayer("countdown-phase-" + index);
        }
        assertTrue(gameManager.startGame().success());
        Player target = gameManager.getSurvivors().getFirst().getPlayer();

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertTrue(gameManager.removePlayer(target).success()),
                () -> assertEquals(RoundPhase.COUNTDOWN, gameManager.getPhase())
        );
    }

    @Test
    void deploymentAllowsRemovalButRejectsZombieToggle() {
        configureValidSetup(4, 1);
        for (int index = 0; index < 4; index++) {
            addLobbyPlayer("deployment-phase-" + index);
        }
        assertTrue(gameManager.startGame().success());
        Player target = gameManager.getSurvivors().getFirst().getPlayer();

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertTrue(gameManager.removePlayer(target).success()),
                () -> assertEquals(RoundPhase.DEPLOYING, gameManager.getPhase())
        );
    }

    @Test
    void headStartAllowsRemovalButRejectsZombieToggle() {
        configureValidSetup(4, 1);
        for (int index = 0; index < 4; index++) {
            addLobbyPlayer("headstart-phase-" + index);
        }
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(3));
        Player target = gameManager.getSurvivors().getFirst().getPlayer();

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertTrue(gameManager.removePlayer(target).success()),
                () -> assertEquals(RoundPhase.HEADSTART, gameManager.getPhase())
        );
    }

    @Test
    void deadParticipantWaitsForRespawnBeforeSnapshotAndHelmetRestoration() {
        configureValidSetup(2, 1);
        Player first = addLobbyPlayer("dead-cleanup-first");
        Player second = addLobbyPlayer("dead-cleanup-second");
        org.mockito.Mockito.doReturn(List.of(first, second)).when(server).getOnlinePlayers();
        for (Player participant : List.of(first, second)) {
            when(participant.getInventory().getArmorContents())
                    .thenReturn(new ItemStack[]{null, null, null, null});
        }

        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(1));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));
        Player deadInfected = gameManager.getInfected().getFirst().getPlayer();
        PlayerInventory inventory = deadInfected.getInventory();
        World capturedWorld = deadInfected.getWorld();
        when(deadInfected.getServer()).thenReturn(server);
        when(server.getWorld(capturedWorld.getUID())).thenReturn(capturedWorld);
        when(deadInfected.isDead()).thenReturn(true);
        clearInvocations(deadInfected, inventory);

        assertTrue(gameManager.stopGame());
        repeatingTasks.getLast().run();

        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());
        verify(inventory, never()).setArmorContents(any(ItemStack[].class));

        when(deadInfected.isDead()).thenReturn(false);
        Optional<Location> respawn = gameManager.restoreAfterRoundRespawn(deadInfected);

        assertTrue(respawn.isPresent());
        assertEquals(0.5, respawn.get().getX());
        assertEquals(64.0, respawn.get().getY());
        assertEquals(0.5, respawn.get().getZ());
        ArgumentCaptor<ItemStack[]> restoredArmor = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).setArmorContents(restoredArmor.capture());
        assertNull(restoredArmor.getValue()[3]);
        assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(deadInfected));
    }

    @Test
    void endingRejectsZombieToggleAndPlayerRemoval() {
        configureValidSetup(3, 1);
        for (int index = 0; index < 3; index++) {
            addLobbyPlayer("ending-phase-" + index);
        }
        assertTrue(gameManager.startGame().success());
        Player target = gameManager.getSurvivors().getFirst().getPlayer();
        assertTrue(gameManager.stopGame());

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertFalse(gameManager.removePlayer(target).success()),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase())
        );
    }

    @Test
    void advancesOnlyAfterSuccessfulSurvivorAndReleaseBatches() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");

        StartResult result = gameManager.startGame();

        assertTrue(result.success());
        assertEquals(RoundPhase.DEPLOYING, gameManager.getPhase());
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        assertEquals(RoundPhase.HEADSTART, gameManager.getPhase());
        assertTrue(gameManager.getInfected().stream()
                .allMatch(infected -> gameManager.isContainedInfected(infected.getPlayer())));

        delayedRelease[0].run();
        assertEquals(RoundPhase.HEADSTART, gameManager.getPhase());
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        assertAll(
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase()),
                () -> assertTrue(gameManager.getInfected().stream()
                        .noneMatch(infected -> gameManager.isContainedInfected(infected.getPlayer())))
        );
    }

    @Test
    void selectionCreatesExactlyTheConfiguredNumberOfUniqueStartingInfected() {
        configureValidSetup(5, 2);
        for (int index = 0; index < 5; index++) {
            addLobbyPlayer("selection-participant-" + index);
        }

        assertTrue(gameManager.startGame().success());

        Set<UUID> infectedIds = gameManager.getInfected().stream()
                .map(role -> role.getPlayer().getUniqueId())
                .collect(java.util.stream.Collectors.toSet());
        assertAll(
                () -> assertEquals(2, gameManager.getInfected().size()),
                () -> assertEquals(2, infectedIds.size()),
                () -> assertEquals(3, gameManager.getSurvivors().size())
        );
    }

    @Test
    void infectedReleaseDoesNotBypassContainmentBeforeEachPlayersTeleportAttempt() {
        configureValidSetup(4, 3);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        addLobbyPlayer("fourth");
        gameManager.startGame();
        completions.get(SpawnRole.SURVIVOR).accept(success(1));

        delayedRelease[0].run();

        assertTrue(gameManager.getInfected().stream()
                .allMatch(role -> gameManager.isContainedInfected(role.getPlayer())));
        assertTrue(gameManager.getInfected().stream()
                .noneMatch(role -> gameManager.isRoundTeleportBypass(role.getPlayer())));

        Player firstReleased = gameManager.getInfected().getFirst().getPlayer();
        Player stillWaiting = gameManager.getInfected().get(1).getPlayer();
        beforeReleaseTeleport.accept(firstReleased);
        assertAll(
                () -> assertTrue(gameManager.isRoundTeleportBypass(firstReleased)),
                () -> assertFalse(gameManager.isRoundTeleportBypass(stillWaiting)),
                () -> assertTrue(gameManager.isContainedInfected(stillWaiting))
        );
        afterReleaseTeleport.accept(firstReleased, true);
        assertAll(
                () -> assertFalse(gameManager.isRoundTeleportBypass(firstReleased)),
                () -> assertTrue(gameManager.isContainedInfected(firstReleased)),
                () -> assertTrue(gameManager.isContainedInfected(stillWaiting))
        );

        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(3));
        assertAll(
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase()),
                () -> assertTrue(gameManager.getInfected().stream()
                        .noneMatch(role -> gameManager.isContainedInfected(role.getPlayer())))
        );
    }

    @Test
    void failedSurvivorTeleportBeginsCleanupWithoutHeadStart() {
        configureValidSetup(2, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        gameManager.startGame();

        completions.get(SpawnRole.SURVIVOR).accept(new TeleportBatchResult(
                1, 0, List.of(UUID.randomUUID()), null));

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(scheduler, never()).runLater(any(Runnable.class), anyLong());
    }

    @Test
    void failedInitialInfectedHoldingTeleportCancelsStartBeforeSurvivorTeleporting() {
        configureValidSetup(3, 1);
        for (int index = 0; index < 3; index++) {
            Player player = addLobbyPlayer("holding-failure-" + index);
            when(player.teleport(any(Location.class))).thenReturn(false);
        }

        StartResult result = gameManager.startGame();

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("holding spawn")),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase())
        );
        verify(teleportManager, never()).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any());
    }

    @Test
    void staleReleaseCallbackCannotActivateAnEndingRound() {
        configureValidSetup(2, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        gameManager.startGame();
        completions.get(SpawnRole.SURVIVOR).accept(success(1));
        delayedRelease[0].run();
        Consumer<TeleportBatchResult> stale = completions.get(SpawnRole.INFECTED_RELEASE);

        assertTrue(gameManager.stopGame());
        stale.accept(success(1));

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
    }

    @Test
    void disabledRoundTimeLimitSchedulesNoExpiry() {
        startActiveRound(3, 1);

        assertAll(
                () -> assertEquals(1, laterTasks.values().stream().mapToInt(List::size).sum()),
                () -> assertTrue(laterTasks.containsKey(200L))
        );
    }

    @Test
    void positiveRoundTimeLimitSchedulesOneTrackedExpiryAtConfiguredDelay() {
        configureValidSetup(3, 1);
        config.set("settings.round-time-limit-seconds", 6);
        for (int index = 0; index < 3; index++) {
            addLobbyPlayer("timed-participant-" + index);
        }
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        assertEquals(1, laterTasks.getOrDefault(120L, List.of()).size());
    }

    @Test
    void activeRoundExpiryAwardsSurvivors() {
        configureValidSetup(3, 1);
        config.set("settings.round-time-limit-seconds", 6);
        for (int index = 0; index < 3; index++) {
            addLobbyPlayer("expiring-participant-" + index);
        }
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        List<Runnable> expiryTasks = laterTasks.getOrDefault(120L, List.of());
        assertEquals(1, expiryTasks.size());
        expiryTasks.getFirst().run();

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("Time expired"));
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("Survivors win"));
    }

    @Test
    void staleRoundExpiryAfterStopDoesNothing() {
        configureValidSetup(3, 1);
        config.set("settings.round-time-limit-seconds", 6);
        for (int index = 0; index < 3; index++) {
            addLobbyPlayer("stale-expiry-participant-" + index);
        }
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));
        List<Runnable> expiryTasks = laterTasks.getOrDefault(120L, List.of());
        assertEquals(1, expiryTasks.size());
        Runnable staleExpiry = expiryTasks.getFirst();
        assertTrue(gameManager.stopGame());
        clearInvocations(server);

        staleExpiry.run();

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server, never()).broadcastMessage(any(String.class));
    }

    @Test
    void shutdownRestoresSnapshotsSynchronouslyWithoutSchedulingCleanup() {
        startActiveRound(3, 1);
        List<Player> participants = new ArrayList<>();
        gameManager.getSurvivors().forEach(role -> participants.add(role.getPlayer()));
        gameManager.getInfected().forEach(role -> participants.add(role.getPlayer()));
        clearInvocations(scheduler);
        participants.forEach(org.mockito.Mockito::clearInvocations);

        gameManager.shutdown();

        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());
        for (Player participant : participants) {
            verify(participant, times(1)).setFoodLevel(0);
        }
        verify(scheduler, never()).runLater(any(Runnable.class), anyLong());
        verify(scheduler, never()).runRepeating(any(Runnable.class), anyLong(), anyLong());
    }

    @Test
    void shutdownAlsoRestoresQueuedSpectatorsWithSnapshots() {
        startActiveRound(3, 1);
        Player queued = player("shutdown-queued");
        assertTrue(gameManager.queueLateJoin(queued));
        org.mockito.Mockito.doReturn(List.of(queued)).when(server).getOnlinePlayers();
        clearInvocations(queued);

        gameManager.shutdown();

        verify(queued).setFoodLevel(0);
        verify(roleEquipment).removeOwnedHead(queued);
    }

    @Test
    void cancelledRestorationTeleportKeepsSnapshotForRetry() {
        startActiveRound(3, 1);
        Player departed = gameManager.getSurvivors().getFirst().getPlayer();
        when(departed.teleport(any(Location.class))).thenReturn(false, true);
        clearInvocations(departed);

        gameManager.handleQuit(departed);
        gameManager.handleQuit(departed);

        verify(departed, times(2)).setFoodLevel(0);
        verify(departed, times(2)).teleport(any(Location.class));
    }

    @Test
    void newRoundPreservesADeadDepartedPlayersPendingSnapshotUntilRespawn() {
        startActiveRound(3, 1);
        Player deadDeparted = gameManager.getSurvivors().getFirst().getPlayer();
        when(deadDeparted.isDead()).thenReturn(true);
        World originalWorld = deadDeparted.getWorld();
        when(originalWorld.getSpawnLocation()).thenReturn(
                new Location(originalWorld, 0.5, 64, 0.5));

        gameManager.handleQuit(deadDeparted);
        assertTrue(gameManager.stopGame());
        repeatingTasks.getLast().run();
        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());

        configureValidSetup(2, 1);
        addLobbyPlayer("next-round-first");
        addLobbyPlayer("next-round-second");
        assertTrue(gameManager.startGame().success());

        assertTrue(gameManager.restoreAfterRoundRespawn(deadDeparted).isPresent());
        verify(deadDeparted).setFoodLevel(0);
    }

    @Test
    void failedLaterRoundCaptureRollsBackOnlyNewSnapshotsAndPreservesOlderPendingRestoration() {
        startActiveRound(3, 1);
        Player deadDeparted = gameManager.getSurvivors().getFirst().getPlayer();
        when(deadDeparted.isDead()).thenReturn(true);
        World originalWorld = deadDeparted.getWorld();
        when(originalWorld.getSpawnLocation()).thenReturn(
                new Location(originalWorld, 0.5, 64, 0.5));
        gameManager.handleQuit(deadDeparted);
        assertTrue(gameManager.stopGame());
        repeatingTasks.getLast().run();
        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());

        configureValidSetup(2, 1);
        Player capturedThisStart = addLobbyPlayer("capture-rollback-first");
        Player captureFailure = player("capture-rollback-failure");
        when(captureFailure.getInventory()).thenThrow(
                new IllegalStateException("simulated snapshot failure"));
        gameManager.addSurvivor(mockSurvivor(captureFailure));

        StartResult result = gameManager.startGame();

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertEquals(RoundPhase.LOBBY, gameManager.getPhase()),
                () -> assertTrue(gameManager.restoreAfterRoundRespawn(deadDeparted).isPresent())
        );
        verify(capturedThisStart).setFoodLevel(0);
    }

    @Test
    void guardedRoundCallbackCannotMutateAfterStop() {
        startActiveRound(3, 1);
        Player participant = gameManager.getSurvivors().getFirst().getPlayer();
        java.util.concurrent.atomic.AtomicBoolean mutated =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        gameManager.scheduleRoundLater(
                participant, RoundPhase.ACTIVE, 1L, () -> mutated.set(true));
        Runnable stale = laterTasks.get(1L).getFirst();
        assertTrue(gameManager.stopGame());

        stale.run();

        assertFalse(mutated.get());
    }

    @Test
    void activeLateJoinBecomesAQueuedSpectatorOutsideBothTeams() {
        startActiveRound(2, 1);
        Player lateJoin = player("late-join");

        assertTrue(gameManager.queueLateJoin(lateJoin));

        assertAll(
                () -> assertTrue(gameManager.isQueued(lateJoin)),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(lateJoin)),
                () -> assertTrue(gameManager.getSurvivors().stream()
                        .noneMatch(role -> role.getPlayer().getUniqueId().equals(lateJoin.getUniqueId()))),
                () -> assertTrue(gameManager.getInfected().stream()
                        .noneMatch(role -> role.getPlayer().getUniqueId().equals(lateJoin.getUniqueId()))
        ));
        verify(lateJoin).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void lastSurvivorDepartureAwardsZombies() {
        startActiveRound(2, 1);
        Player lastSurvivor = gameManager.getSurvivors().getFirst().getPlayer();
        clearInvocations(lastSurvivor, server);

        gameManager.handleQuit(lastSurvivor);

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        org.mockito.InOrder restorationBeforeOutcome = org.mockito.Mockito.inOrder(lastSurvivor, server);
        restorationBeforeOutcome.verify(lastSurvivor).setFoodLevel(0);
        restorationBeforeOutcome.verify(server).broadcastMessage(
                org.mockito.ArgumentMatchers.contains("All survivors infected"));
        verify(server, never()).broadcastMessage(org.mockito.ArgumentMatchers.contains("abandoned"));
    }

    @Test
    void lastInfectedDepartureAwardsSurvivors() {
        startActiveRound(2, 1);
        Player lastInfected = gameManager.getInfected().getFirst().getPlayer();
        clearInvocations(lastInfected, server);

        gameManager.handleQuit(lastInfected);

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        org.mockito.InOrder restorationBeforeOutcome = org.mockito.Mockito.inOrder(lastInfected, server);
        restorationBeforeOutcome.verify(lastInfected).setFoodLevel(0);
        restorationBeforeOutcome.verify(server).broadcastMessage(
                org.mockito.ArgumentMatchers.contains("Survivors win"));
    }

    @Test
    void directRosterCheckAwardsInfectedWhenNoSurvivorsRemain() {
        startActiveRound(2, 1);
        gameManager.getSurvivors().clear();
        clearInvocations(server);

        gameManager.checkWin();

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("All survivors infected"));
    }

    @Test
    void directRosterCheckAwardsSurvivorsWhenNoInfectedRemain() {
        startActiveRound(2, 1);
        gameManager.getInfected().clear();
        clearInvocations(server);

        gameManager.checkWin();

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("Survivors win"));
    }

    @Test
    void directRosterCheckAbandonsWhenBothTeamsAreEmpty() {
        startActiveRound(2, 1);
        gameManager.getSurvivors().clear();
        gameManager.getInfected().clear();
        clearInvocations(server);

        gameManager.checkWin();

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("abandoned"));
    }

    @Test
    void removePlayerLeavesThemOutsideTheRoundWithoutLobbyRegistration() {
        startActiveRound(3, 1);
        Player removed = gameManager.getSurvivors().getFirst().getPlayer();
        clearInvocations(removed);

        RoundActionResult result = gameManager.removePlayer(removed);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(removed)),
                () -> assertFalse(gameManager.isQueued(removed)),
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase())
        );
        verify(removed).setFoodLevel(0);
        verify(gameManager, never()).resetPlayerState(removed);
        verify(roleFactory, never()).createSurvivor(removed);
    }

    @Test
    void removePlayerDropsAQueuedSpectatorWithoutChangingTheActiveTeams() {
        startActiveRound(3, 1);
        int survivorsBefore = gameManager.getSurvivors().size();
        int infectedBefore = gameManager.getInfected().size();
        Player queued = player("queued-removal");
        assertTrue(gameManager.queueLateJoin(queued));
        clearInvocations(queued, roleFactory);

        RoundActionResult result = gameManager.removePlayer(queued);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertFalse(gameManager.isQueued(queued)),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(queued)),
                () -> assertEquals(survivorsBefore, gameManager.getSurvivors().size()),
                () -> assertEquals(infectedBefore, gameManager.getInfected().size()),
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase())
        );
        verify(queued).setFoodLevel(0);
        verify(roleFactory, never()).createSurvivor(queued);
    }

    @Test
    void removePlayerRestoresTheFinalInfectedBeforeAwardingSurvivors() {
        startActiveRound(2, 1);
        Player finalInfected = gameManager.getInfected().getFirst().getPlayer();
        clearInvocations(finalInfected, server);

        RoundActionResult result = gameManager.removePlayer(finalInfected);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(finalInfected)),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase())
        );
        org.mockito.InOrder restorationBeforeOutcome = org.mockito.Mockito.inOrder(finalInfected, server);
        restorationBeforeOutcome.verify(finalInfected).setFoodLevel(0);
        restorationBeforeOutcome.verify(server).broadcastMessage(
                org.mockito.ArgumentMatchers.contains("Survivors win"));
        verify(roleFactory, never()).createSurvivor(finalInfected);
    }

    @Test
    void resetPlayerStateRestoresTheNeutralPlayerListName() {
        Player player = player("neutral-list-name");
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        doCallRealMethod().when(gameManager).resetPlayerState(player);

        gameManager.resetPlayerState(player);

        verify(player).setPlayerListName("neutral-list-name");
    }

    @Test
    void activeAdminCanAdmitAQueuedPlayerAsInfectedAtASafeRespawn() {
        startActiveRound(3, 1);
        Player queued = player("queued-admin-add");
        assertTrue(gameManager.queueLateJoin(queued));

        RoundActionResult result = gameManager.toggleZombieSafely(queued);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertFalse(gameManager.isQueued(queued)),
                () -> assertEquals(ParticipantRole.INFECTED, gameManager.roleOf(queued))
        );
        verify(queued).teleport(any(Location.class));
        verify(queued).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void failedAdminAdmissionTeleportCancelsWithoutStrandingTheQueuedPlayerInARole() {
        startActiveRound(3, 1);
        Player queued = player("queued-failed-teleport");
        when(queued.teleport(any(Location.class))).thenReturn(false);
        assertTrue(gameManager.queueLateJoin(queued));

        RoundActionResult result = gameManager.toggleZombieSafely(queued);

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(gameManager.isQueued(queued)),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(queued)),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase())
        );
    }

    @Test
    void adminCannotToggleAwayTheLastMemberOfEitherTeam() {
        startActiveRound(2, 1);
        Player lastSurvivor = gameManager.getSurvivors().getFirst().getPlayer();
        Player lastInfected = gameManager.getInfected().getFirst().getPlayer();

        RoundActionResult survivorResult = gameManager.toggleZombieSafely(lastSurvivor);
        RoundActionResult infectedResult = gameManager.toggleZombieSafely(lastInfected);

        assertAll(
                () -> assertFalse(survivorResult.success()),
                () -> assertFalse(infectedResult.success()),
                () -> assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(lastSurvivor)),
                () -> assertEquals(ParticipantRole.INFECTED, gameManager.roleOf(lastInfected)),
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase())
        );
    }

    @Test
    void adminCannotToggleADeadParticipant() {
        startActiveRound(3, 1);
        Player survivor = gameManager.getSurvivors().getFirst().getPlayer();
        when(survivor.isDead()).thenReturn(true);

        RoundActionResult result = gameManager.toggleZombieSafely(survivor);

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.message().contains("alive")),
                () -> assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(survivor))
        );
        verify(survivor, never()).teleport(any(Location.class));
    }

    @Test
    void togglingInfectedToSurvivorAppliesSurvivorPresentation() {
        startActiveRound(4, 2);
        Player converted = gameManager.getInfected().getFirst().getPlayer();
        Survivor survivorRole = mockSurvivor(converted);
        when(roleFactory.createSurvivor(converted)).thenReturn(survivorRole);

        RoundActionResult result = gameManager.toggleZombieSafely(converted);

        assertTrue(result.success());
        verify(survivorRole).prepareForMatch();
        assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(converted));
    }

    @Test
    void eliminatedInfectedCanStillBeRemovedFromTheRound() {
        config.set("settings.infected-lives", 1);
        startActiveRound(4, 2);
        Player eliminated = gameManager.getInfected().getFirst().getPlayer();
        assertFalse(gameManager.handleInfectedDeath(eliminated));
        assertEquals(RoundPhase.ACTIVE, gameManager.getPhase());

        RoundActionResult result = gameManager.removePlayer(eliminated);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertFalse(gameManager.isRoundParticipant(eliminated)),
                () -> assertFalse(gameManager.isEliminatedInfected(eliminated))
        );
        verify(eliminated).setFoodLevel(0);
    }

    @Test
    void winnerEffectsAreScopedToParticipantsAndQueuedSpectators() {
        startActiveRound(2, 1);
        Player survivor = gameManager.getSurvivors().getFirst().getPlayer();
        Player infectedPlayer = gameManager.getInfected().getFirst().getPlayer();
        Player queued = player("winner-queued");
        Player unrelated = player("winner-unrelated");
        assertTrue(gameManager.queueLateJoin(queued));
        org.mockito.Mockito.doReturn(List.of(survivor, infectedPlayer, queued, unrelated))
                .when(server).getOnlinePlayers();
        clearInvocations(survivor, infectedPlayer, queued, unrelated);

        gameManager.handleHit(infectedPlayer, survivor);

        verify(survivor).sendTitle(any(String.class), any(String.class), anyInt(), anyInt(), anyInt());
        verify(infectedPlayer).sendTitle(any(String.class), any(String.class), anyInt(), anyInt(), anyInt());
        verify(queued).sendTitle(any(String.class), any(String.class), anyInt(), anyInt(), anyInt());
        verify(unrelated, never()).sendTitle(any(String.class), any(String.class), anyInt(), anyInt(), anyInt());
        verify(unrelated, never()).playSound(any(Location.class), any(String.class), anyFloat(), anyFloat());
    }

    @Test
    void onlineLobbyRegistrationIgnoresDeadOfflineAndDuplicatePlayers() {
        Player ready = player("hot-enable-ready");
        Player duplicate = ready;
        Player dead = player("hot-enable-dead");
        Player offline = player("hot-enable-offline");
        when(dead.isDead()).thenReturn(true);
        when(offline.isOnline()).thenReturn(false);

        gameManager.registerOnlineLobbySurvivors(List.of(ready, duplicate, dead, offline));

        assertAll(
                () -> assertEquals(1, gameManager.getSurvivors().size()),
                () -> assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(ready)),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(dead)),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(offline))
        );
        verify(roleFactory, times(1)).createSurvivor(ready);
    }

    @Test
    void finalInfectedEliminationRemainsMarkedUntilEndingCleanup() {
        config.set("settings.infected-lives", 1);
        startActiveRound(2, 1);
        Player finalInfected = gameManager.getInfected().getFirst().getPlayer();

        boolean hasRemainingLife = gameManager.handleInfectedDeath(finalInfected);

        assertAll(
                () -> assertFalse(hasRemainingLife),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase()),
                () -> assertTrue(gameManager.isEliminatedInfected(finalInfected))
        );
    }

    @Test
    void activeSurvivorDeathConvertsToInfectedWithAPendingArenaRespawn() {
        config.set("settings.infected-lives", 3);
        startActiveRound(3, 1);
        Player survivor = gameManager.getSurvivors().getFirst().getPlayer();

        boolean roundOwned = gameManager.handlePlayerDeath(survivor);

        assertAll(
                () -> assertTrue(roundOwned),
                () -> assertEquals(ParticipantRole.INFECTED, gameManager.roleOf(survivor)),
                () -> assertTrue(gameManager.claimInfectedRespawn(survivor)),
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase()),
                () -> assertEquals(1, gameManager.getSurvivors().size()),
                () -> assertEquals(2, gameManager.getInfected().size())
        );
    }

    @Test
    void lastSurvivorDeathConvertsThenAwardsTheInfectedWin() {
        startActiveRound(2, 1);
        Player survivor = gameManager.getSurvivors().getFirst().getPlayer();

        assertTrue(gameManager.handlePlayerDeath(survivor));

        assertAll(
                () -> assertEquals(ParticipantRole.INFECTED, gameManager.roleOf(survivor)),
                () -> assertEquals(RoundPhase.ENDING, gameManager.getPhase())
        );
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("All survivors infected"));
    }

    @Test
    void playerWhoseDeathEndsRoundCanJoinTheImmediateNextRoundAfterRespawn() {
        startActiveRound(2, 1);
        Player deadPlayer = gameManager.getSurvivors().getFirst().getPlayer();
        Player otherPlayer = gameManager.getInfected().getFirst().getPlayer();
        org.mockito.Mockito.doReturn(List.of(deadPlayer, otherPlayer)).when(server).getOnlinePlayers();
        when(deadPlayer.isDead()).thenReturn(true);

        assertTrue(gameManager.handlePlayerDeath(deadPlayer));
        repeatingTasks.getLast().run();
        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());

        assertTrue(gameManager.restoreAfterRoundRespawn(deadPlayer).isPresent());
        when(deadPlayer.isDead()).thenReturn(false);

        StartResult nextRound = gameManager.startGame();
        assertTrue(nextRound.success(), () -> String.join("; ", nextRound.errors()));
    }

    @Test
    void preActiveInfectedDeathQueuesHoldingRespawnWithoutConsumingALife() {
        configureValidSetup(2, 1);
        addLobbyPlayer("pre-active-first");
        addLobbyPlayer("pre-active-second");
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(1));
        assertEquals(RoundPhase.HEADSTART, gameManager.getPhase());
        Player infected = gameManager.getInfected().getFirst().getPlayer();

        assertTrue(gameManager.handlePlayerDeath(infected));

        assertAll(
                () -> assertTrue(gameManager.claimHoldingRespawn(infected)),
                () -> assertFalse(gameManager.isEliminatedInfected(infected)),
                () -> assertEquals(ParticipantRole.INFECTED, gameManager.roleOf(infected)),
                () -> assertEquals(RoundPhase.HEADSTART, gameManager.getPhase())
        );
    }

    @Test
    void duplicateDeathNotificationCannotConsumeAnotherInfectedLifeBeforeRespawn() {
        config.set("settings.infected-lives", 3);
        startActiveRound(2, 1);
        Player infected = gameManager.getInfected().getFirst().getPlayer();

        assertTrue(gameManager.handleInfectedDeath(infected));
        assertTrue(gameManager.handleInfectedDeath(infected));
        assertTrue(gameManager.claimInfectedRespawn(infected));
        assertTrue(gameManager.handleInfectedDeath(infected));
        assertTrue(gameManager.claimInfectedRespawn(infected));
        assertFalse(gameManager.handleInfectedDeath(infected));

        assertTrue(gameManager.isEliminatedInfected(infected));
    }

    @Test
    void buffToggleOwnsOneTrackingTaskAcrossDisableReenableAndCleanup() {
        startActiveRound(3, 1);
        Player infected = gameManager.getInfected().getFirst().getPlayer();
        assertEquals(0, repeatingHandles.size());

        assertTrue(gameManager.toggleInfectedBuff().success());
        assertTrue(gameManager.isBuffEnabled());
        assertEquals(1, repeatingHandles.size());
        verify(buffController).apply(infected, true);

        assertTrue(gameManager.toggleInfectedBuff().success());
        assertFalse(gameManager.isBuffEnabled());
        assertEquals(1, repeatingHandles.size());
        verify(repeatingHandles.getFirst()).cancel();
        verify(buffController).apply(infected, false);

        assertTrue(gameManager.toggleInfectedBuff().success());
        assertEquals(2, repeatingHandles.size());
        assertTrue(gameManager.stopGame());
        verify(repeatingHandles.get(1)).cancel();
    }

    @Test
    void survivorConvertedWhileBuffIsEnabledReceivesCurrentBuffState() {
        startActiveRound(3, 1);
        assertTrue(gameManager.toggleInfectedBuff().success());
        Player converted = gameManager.getSurvivors().getFirst().getPlayer();

        assertTrue(gameManager.toggleZombieSafely(converted).success());

        verify(buffController).apply(converted, true);
    }

    @Test
    void trackingUsesTheCurrentRostersAfterARoleShiftAndIgnoresQueuedPlayers() {
        startActiveRound(4, 1);
        assertTrue(gameManager.toggleInfectedBuff().success());
        Player tracker = gameManager.getInfected().getFirst().getPlayer();
        Player nearest = gameManager.getSurvivors().getFirst().getPlayer();
        Player fallback = gameManager.getSurvivors().get(1).getPlayer();
        Player queued = player("tracking-queued");
        assertTrue(gameManager.queueLateJoin(queued));

        World sharedWorld = mock(World.class);
        Location trackerLocation = new Location(sharedWorld, 0, 70, 0);
        Location nearestLocation = new Location(sharedWorld, 2, 70, 0);
        Location fallbackLocation = new Location(sharedWorld, 20, 70, 0);
        Location queuedLocation = new Location(sharedWorld, 1, 70, 0);
        org.mockito.Mockito.doReturn(sharedWorld).when(tracker).getWorld();
        org.mockito.Mockito.doReturn(sharedWorld).when(nearest).getWorld();
        org.mockito.Mockito.doReturn(sharedWorld).when(fallback).getWorld();
        org.mockito.Mockito.doReturn(sharedWorld).when(queued).getWorld();
        org.mockito.Mockito.doReturn(trackerLocation).when(tracker).getLocation();
        org.mockito.Mockito.doReturn(nearestLocation).when(nearest).getLocation();
        org.mockito.Mockito.doReturn(fallbackLocation).when(fallback).getLocation();
        org.mockito.Mockito.doReturn(queuedLocation).when(queued).getLocation();
        clearInvocations(tracker, queued);

        repeatingTasks.getFirst().run();

        verify(tracker).setCompassTarget(nearestLocation);
        verify(queued, never()).setCompassTarget(any(Location.class));

        assertTrue(gameManager.toggleZombieSafely(nearest).success());
        clearInvocations(tracker);
        repeatingTasks.getFirst().run();

        verify(tracker).setCompassTarget(fallbackLocation);
    }

    private void startActiveRound(int participants, int startingInfected) {
        configureValidSetup(participants, startingInfected);
        for (int index = 0; index < participants; index++) {
            addLobbyPlayer("participant-" + index);
        }
        assertTrue(gameManager.startGame().success());
        completions.get(SpawnRole.SURVIVOR).accept(success(participants - startingInfected));
        delayedRelease[0].run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(startingInfected));
        assertEquals(RoundPhase.ACTIVE, gameManager.getPhase());
    }

    private void configureValidSetup(int participants, int startingInfected) {
        config.set("settings.starting-zombies", startingInfected);
        config.set("settings.teleport-batch-size", 2);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);
        config.set("settings.minimum-players", Math.max(2, startingInfected + 1));
        config.set("settings.start-countdown-seconds", 0);
        config.set("settings.round-time-limit-seconds", 0);
        Location location = safeLocation();
        when(spawnRepository.loadedHoldingSpawn()).thenReturn(Optional.of(location));
        for (SpawnRole role : SpawnRole.values()) {
            when(spawnRepository.loadedLocations(role)).thenReturn(List.of(location));
        }
    }

    private Location safeLocation() {
        World world = mock(World.class);
        retainedWorlds.add(world);
        org.bukkit.WorldBorder border = mock(org.bukkit.WorldBorder.class);
        org.bukkit.block.Block ground = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block feet = mock(org.bukkit.block.Block.class);
        org.bukkit.block.Block head = mock(org.bukkit.block.Block.class);
        Location location = new Location(world, 0.5, 64, 0.5);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);
        when(world.getBlockAt(0, 64, 0)).thenReturn(feet);
        when(world.getBlockAt(0, 65, 0)).thenReturn(head);
        when(ground.getType()).thenReturn(org.bukkit.Material.STONE);
        when(ground.isPassable()).thenReturn(false);
        when(ground.getBoundingBox()).thenReturn(
                new org.bukkit.util.BoundingBox(0, 63, 0, 1, 64, 1));
        when(feet.getType()).thenReturn(org.bukkit.Material.AIR);
        when(feet.isPassable()).thenReturn(true);
        when(head.getType()).thenReturn(org.bukkit.Material.AIR);
        when(head.isPassable()).thenReturn(true);
        return location;
    }

    private Player addLobbyPlayer(String name) {
        Player player = player(name);
        gameManager.addSurvivor(mockSurvivor(player));
        return player;
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.teleport(any(Location.class))).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        World world = mock(World.class);
        UUID worldId = UUID.nameUUIDFromBytes((name + "-world").getBytes());
        when(world.getUID()).thenReturn(worldId);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(player.getServer()).thenReturn(server);
        when(server.getWorld(worldId)).thenReturn(world);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(player.getActivePotionEffects()).thenReturn(Set.of());
        return player;
    }

    private static Survivor mockSurvivor(Player player) {
        Survivor survivor = mock(Survivor.class);
        when(survivor.getPlayer()).thenReturn(player);
        return survivor;
    }

    private static TeleportBatchResult success(int attempted) {
        return new TeleportBatchResult(attempted, attempted, List.of(), null);
    }
}
