package me.DaWHeL.infected;

import me.DaWHeL.infected.Roles.Survivor;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private Map<SpawnRole, Consumer<TeleportBatchResult>> completions;
    private Runnable[] delayedRelease;
    private Map<Long, Runnable> scheduledTasks;
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
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(config);
        when(scheduler.runLater(any(Runnable.class), anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runRepeating(any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        gameManager = org.mockito.Mockito.spy(new GameManager(
                plugin,
                spawnRepository,
                teleportManager,
                scheduler,
                new RoundStartValidator(),
                new Random(1),
                roleFactory
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
        delayedRelease = new Runnable[1];
        scheduledTasks = new LinkedHashMap<>();
        retainedWorlds = new ArrayList<>();
        doAnswer(invocation -> {
            SpawnRole role = invocation.getArgument(0);
            completions.put(role, invocation.getArgument(5));
            return mock(BukkitTask.class);
        }).when(teleportManager).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any());
        doAnswer(invocation -> {
            beforeReleaseTeleport = invocation.getArgument(5);
            afterReleaseTeleport = invocation.getArgument(6);
            completions.put(invocation.getArgument(0), invocation.getArgument(7));
            return mock(BukkitTask.class);
        }).when(teleportManager).teleportPlayersBatch(
                any(SpawnRole.class), anyList(), anyInt(), anyLong(), any(), any(), any(), any());
        doAnswer(invocation -> {
            delayedRelease[0] = invocation.getArgument(0);
            scheduledTasks.put(invocation.getArgument(1), invocation.getArgument(0));
            return mock(BukkitTask.class);
        }).when(scheduler).runLater(any(Runnable.class), anyLong());
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
    void activeChestMutationLeasePreventsARoundFromStartingMidOperation() {
        gameManager.setRoundStartAllowed(() -> false);

        StartResult result = gameManager.startGame();

        assertFalse(result.success());
        assertTrue(result.message().contains("chest operation"));
        assertEquals(RoundPhase.LOBBY, gameManager.getPhase());
    }

    @Test
    void lobbyRejectsZombieToggleAndPlayerRemoval() {
        Player target = addLobbyPlayer("lobby-phase-target");

        assertAll(
                () -> assertFalse(gameManager.toggleZombieSafely(target).success()),
                () -> assertFalse(gameManager.removePlayer(target).success()),
                () -> assertEquals(ParticipantRole.SURVIVOR, gameManager.roleOf(target))
        );
    }

    @Test
    void countdownAllowsRemovalButRejectsZombieToggle() {
        configureValidSetup(4, 1);
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
        assertEquals(RoundPhase.COUNTDOWN, gameManager.getPhase());
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
    void selectedSurvivorsAndInfectedSeeGetReadyWhenTheEventStarts() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");

        assertTrue(gameManager.startGame().success());

        for (Player participant : activeParticipants()) {
            verify(participant).sendTitle("§e§lGET READY!", "", 10, 40, 10);
        }
    }

    @Test
    void finalThreeHeadStartSecondsShowTitlesAndPlayANoteblockSound() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        assertTrue(gameManager.startGame().success());
        List<Player> participants = activeParticipants();

        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        scheduledTasks.get(140L).run();
        scheduledTasks.get(160L).run();
        scheduledTasks.get(180L).run();

        for (Player participant : participants) {
            verify(participant).sendTitle("§e§l3", "", 0, 20, 0);
            verify(participant).sendTitle("§e§l2", "", 0, 20, 0);
            verify(participant).sendTitle("§e§l1", "", 0, 20, 0);
            verify(participant, times(3)).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("block.note_block.hat"),
                    org.mockito.ArgumentMatchers.eq(1.0f),
                    org.mockito.ArgumentMatchers.eq(1.0f)
            );
        }
    }

    @Test
    void infectedReleaseAnnouncesActivePlayWithALowPitchedBell() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        assertTrue(gameManager.startGame().success());
        List<Player> participants = activeParticipants();
        completions.get(SpawnRole.SURVIVOR).accept(success(2));

        scheduledTasks.get(200L).run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        for (Player participant : participants) {
            verify(participant).sendTitle("§c§lINFECTION HAS STARTED!", "", 10, 50, 10);
            verify(participant).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("block.note_block.bell"),
                    org.mockito.ArgumentMatchers.eq(1.0f),
                    org.mockito.ArgumentMatchers.eq(0.5f)
            );
        }
    }

    @Test
    void infectedReleaseDelayNeverCutsOffTheFullThreeSecondCountdown() {
        configureValidSetup(3, 1);
        config.set("settings.infected-teleport-delay", 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        assertTrue(gameManager.startGame().success());

        completions.get(SpawnRole.SURVIVOR).accept(success(2));

        assertAll(
                () -> assertTrue(scheduledTasks.containsKey(0L)),
                () -> assertTrue(scheduledTasks.containsKey(20L)),
                () -> assertTrue(scheduledTasks.containsKey(40L)),
                () -> assertTrue(scheduledTasks.containsKey(60L))
        );
    }

    @Test
    void staleCountdownDoesNotDisplayAfterTheRoundStops() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        assertTrue(gameManager.startGame().success());
        List<Player> participants = activeParticipants();
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        Runnable staleCountdown = scheduledTasks.get(140L);
        clearInvocations(participants.toArray());

        assertTrue(gameManager.stopGame());
        staleCountdown.run();

        for (Player participant : participants) {
            verify(participant, never()).sendTitle("§e§l3", "", 0, 20, 0);
            verify(participant, never()).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("block.note_block.hat"),
                    org.mockito.ArgumentMatchers.eq(1.0f),
                    org.mockito.ArgumentMatchers.eq(1.0f)
            );
        }
    }

    @Test
    void failedInfectedReleaseNeverAnnouncesThatInfectionStarted() {
        configureValidSetup(3, 1);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");
        assertTrue(gameManager.startGame().success());
        List<Player> participants = activeParticipants();
        completions.get(SpawnRole.SURVIVOR).accept(success(2));

        scheduledTasks.get(200L).run();
        for (Player participant : participants) {
            verify(participant, never()).sendTitle("§c§lINFECTION HAS STARTED!", "", 10, 50, 10);
        }
        completions.get(SpawnRole.INFECTED_RELEASE).accept(new TeleportBatchResult(
                1, 0, List.of(UUID.randomUUID()), null));

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        for (Player participant : participants) {
            verify(participant, never()).sendTitle("§c§lINFECTION HAS STARTED!", "", 10, 50, 10);
            verify(participant, never()).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("block.note_block.bell"),
                    org.mockito.ArgumentMatchers.eq(1.0f),
                    org.mockito.ArgumentMatchers.eq(0.5f)
            );
        }
    }

    @Test
    void preparationCountdownAndStartPresentationUseConfiguredValues() {
        configureValidSetup(3, 1);
        config.set("messages.get-ready.title", "&aPrepare");
        config.set("messages.get-ready.subtitle", "&7Choose wisely");
        config.set("messages.get-ready.fade-in", 1);
        config.set("messages.get-ready.stay", 2);
        config.set("messages.get-ready.fade-out", 3);
        config.set("messages.infected-release-countdown.title", "&b{time}!");
        config.set("messages.infected-release-countdown.subtitle", "&7Release in {time}");
        config.set("messages.infected-release-countdown.fade-in", 4);
        config.set("messages.infected-release-countdown.stay", 5);
        config.set("messages.infected-release-countdown.fade-out", 6);
        config.set("messages.infected-release-countdown.sound", "custom.tick");
        config.set("messages.infected-release-countdown.volume", 0.4);
        config.set("messages.infected-release-countdown.pitch", 1.2);
        config.set("messages.infection-started.title", "&4Go");
        config.set("messages.infection-started.subtitle", "&cRun");
        config.set("messages.infection-started.fade-in", 7);
        config.set("messages.infection-started.stay", 8);
        config.set("messages.infection-started.fade-out", 9);
        config.set("messages.infection-started.sound", "custom.bell");
        config.set("messages.infection-started.volume", 0.6);
        config.set("messages.infection-started.pitch", 0.3);
        addLobbyPlayer("first");
        addLobbyPlayer("second");
        addLobbyPlayer("third");

        assertTrue(gameManager.startGame().success());
        List<Player> participants = activeParticipants();
        completions.get(SpawnRole.SURVIVOR).accept(success(2));
        scheduledTasks.get(140L).run();
        scheduledTasks.get(200L).run();
        completions.get(SpawnRole.INFECTED_RELEASE).accept(success(1));

        for (Player participant : participants) {
            verify(participant).sendTitle("§aPrepare", "§7Choose wisely", 1, 2, 3);
            verify(participant).sendTitle("§b3!", "§7Release in 3", 4, 5, 6);
            verify(participant).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("custom.tick"),
                    org.mockito.ArgumentMatchers.eq(0.4f),
                    org.mockito.ArgumentMatchers.eq(1.2f)
            );
            verify(participant).sendTitle("§4Go", "§cRun", 7, 8, 9);
            verify(participant).playSound(
                    any(Location.class),
                    org.mockito.ArgumentMatchers.eq("custom.bell"),
                    org.mockito.ArgumentMatchers.eq(0.6f),
                    org.mockito.ArgumentMatchers.eq(0.3f)
            );
        }
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
    void lastSurvivorDepartureCancelsInsteadOfAwardingZombies() {
        startActiveRound(2, 1);
        Player lastSurvivor = gameManager.getSurvivors().getFirst().getPlayer();

        gameManager.handleQuit(lastSurvivor);

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("abandoned"));
        verify(server, never()).broadcastMessage(org.mockito.ArgumentMatchers.contains("All survivors infected"));
    }

    @Test
    void lastInfectedDepartureAwardsSurvivors() {
        startActiveRound(2, 1);
        Player lastInfected = gameManager.getInfected().getFirst().getPlayer();

        gameManager.handleQuit(lastInfected);

        assertEquals(RoundPhase.ENDING, gameManager.getPhase());
        verify(server).broadcastMessage(org.mockito.ArgumentMatchers.contains("Survivors win"));
    }

    @Test
    void removePlayerLeavesThemOutsideTheRoundWithoutLobbyRegistration() {
        startActiveRound(3, 1);
        Player removed = gameManager.getSurvivors().getFirst().getPlayer();

        RoundActionResult result = gameManager.removePlayer(removed);

        assertAll(
                () -> assertTrue(result.success()),
                () -> assertEquals(ParticipantRole.NONE, gameManager.roleOf(removed)),
                () -> assertFalse(gameManager.isQueued(removed)),
                () -> assertEquals(RoundPhase.ACTIVE, gameManager.getPhase())
        );
        verify(gameManager).resetPlayerState(removed);
        verify(roleFactory, never()).createSurvivor(removed);
    }

    @Test
    void resetPlayerStateClearsInventoryHelmetAndRestoresTheNeutralPlayerListName() {
        Player player = player("neutral-list-name");
        PlayerInventory inventory = player.getInventory();
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        doCallRealMethod().when(gameManager).resetPlayerState(player);

        gameManager.resetPlayerState(player);

        verify(player).setPlayerListName("neutral-list-name");
        verify(inventory).clear();
        verify(inventory).setHelmet(null);
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
    void activeCombatRecordsRoleSpecificRoundStats() {
        startActiveRound(3, 1);
        Player attacker = gameManager.getInfected().getFirst().getPlayer();
        Player converted = gameManager.getSurvivors().getFirst().getPlayer();
        Player survivor = gameManager.getSurvivors().getLast().getPlayer();

        gameManager.infectPlayer(converted, attacker, true);
        gameManager.recordSurvivorKill(survivor);
        gameManager.recordSurvivorKill(attacker);

        assertAll(
                () -> assertEquals(1, gameManager.infections(attacker)),
                () -> assertEquals(1, gameManager.kills(survivor)),
                () -> assertEquals(0, gameManager.kills(attacker))
        );
    }

    @Test
    void shutdownClearsPersonalRoundStats() {
        startActiveRound(2, 1);
        Player survivor = gameManager.getSurvivors().getFirst().getPlayer();
        gameManager.recordSurvivorKill(survivor);
        assertEquals(1, gameManager.kills(survivor));

        gameManager.shutdown();

        assertEquals(0, gameManager.kills(survivor));
    }

    @Test
    void infectedRespawnTeleportTemporarilyBypassesCageContainment() {
        startActiveRound(2, 1);
        Player infected = gameManager.getInfected().getFirst().getPlayer();
        Location destination = safeLocation();
        when(infected.teleport(destination)).thenAnswer(invocation -> {
            assertTrue(gameManager.isRoundTeleportBypass(infected));
            return true;
        });

        assertTrue(gameManager.teleportInfectedToRespawn(infected, destination));

        assertFalse(gameManager.isRoundTeleportBypass(infected));
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

    private List<Player> activeParticipants() {
        List<Player> participants = new ArrayList<>();
        gameManager.getSurvivors().forEach(role -> participants.add(role.getPlayer()));
        gameManager.getInfected().forEach(role -> participants.add(role.getPlayer()));
        return participants;
    }

    private void configureValidSetup(int participants, int startingInfected) {
        config.set("settings.starting-zombies", startingInfected);
        config.set("settings.teleport-batch-size", 2);
        config.set("settings.teleport-delay", 40);
        config.set("settings.infected-teleport-delay", 10);
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
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
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
