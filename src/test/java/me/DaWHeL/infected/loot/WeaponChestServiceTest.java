package me.DaWHeL.infected.loot;

import me.DaWHeL.infected.GameManager;
import me.DaWHeL.infected.RoundPhase;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeaponChestServiceTest {
    @Test
    void clearCompletelyEmptiesEveryDiscoveredInventoryInLobby() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 10, 10, 10),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256), List.of(), List.of(), List.of());
        Inventory first = mock(Inventory.class);
        Inventory second = mock(Inventory.class);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.discover(any())).thenReturn(ChestDiscoveryService.DiscoveryResult.success(List.of(
                new DiscoveredChest("a", first), new DiscoveredChest("b", second))));

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class)).clear();

        assertAll(() -> assertTrue(result.success()), () -> assertEquals(2, result.affectedChests()));
        verify(first).clear();
        verify(second).clear();
    }

    @Test
    void rejectsMutationWhileRoundIsRunning() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.ACTIVE);
        WeaponLootCatalog catalog = new WeaponLootCatalog(null, null,
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256), List.of(), List.of(), List.of());

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, mock(ChestDiscoveryService.class), mock(ChestLootGenerator.class)).clear();

        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains("round"));
    }

    @Test
    void fillPlansEveryChestBeforeClearingAnyOfThem() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        ItemStack gun = item();
        ItemStack ammo = item();
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 10, 10, 10),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256),
                List.of(new WeaponLootCatalog.GunEntry(UUID.randomUUID(), gun, LootRarity.COMMON, ammo, 1, 1)),
                List.of(), List.of());
        Inventory first = mock(Inventory.class);
        Inventory second = mock(Inventory.class);
        when(first.getSize()).thenReturn(27);
        when(second.getSize()).thenReturn(27);
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.discover(any())).thenReturn(ChestDiscoveryService.DiscoveryResult.success(List.of(
                new DiscoveredChest("a", first), new DiscoveredChest("b", second))));
        ChestLootGenerator generator = mock(ChestLootGenerator.class);
        ChestLootGenerator.PlanResult firstPlan = ChestLootGenerator.PlanResult.success(Map.of(1, gun));
        ChestLootGenerator.PlanResult failedPlan = ChestLootGenerator.PlanResult.failure(List.of("does not fit"));
        when(generator.plan(any(), anyInt())).thenReturn(
                firstPlan, failedPlan);

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, discovery, generator).fill();

        assertFalse(result.success());
        verify(first, never()).clear();
        verify(second, never()).clear();
    }

    @Test
    void fillRejectsAGunWithoutCorrespondingAmmoBeforeDiscovery() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 1, 1, 1),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256),
                List.of(new WeaponLootCatalog.GunEntry(UUID.randomUUID(), item(), LootRarity.COMMON, null, 1, 1)),
                List.of(), List.of());
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class)).fill();

        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("ammo")));
        verifyNoInteractions(discovery);
    }

    @Test
    void largeBlockVolumeIsAllowedWhenTheRegionFitsTheChunkBudget() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, -2_048, 0), new BlockPoint("arena", 15, 2_048, 15),
                new WeaponLootCatalog.Settings(10, 25, 256), List.of(), List.of(), List.of());
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.discover(any())).thenReturn(ChestDiscoveryService.DiscoveryResult.success(List.of()));

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class)).clear();

        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("No chests")));
        verify(discovery).discover(any());
    }

    @Test
    void emptyRegionReportsThatNoChestsWereFound() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 1, 1, 1),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256), List.of(), List.of(), List.of());
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.discover(any())).thenReturn(ChestDiscoveryService.DiscoveryResult.success(List.of()));

        WeaponChestService.ActionResult result = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class)).clear();

        assertFalse(result.success());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("No chests")));
    }

    @Test
    void previewDoesNotRejectAValidRegionJustBecauseItsChunksAreCurrentlyUnloaded() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 31, 255, 31),
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of());
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);

        WeaponChestService.ActionPreview preview = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class))
                .preview(WeaponChestService.ActionType.CLEAR);

        assertTrue(preview.success());
        assertEquals(-1, preview.chestCount());
        verifyNoInteractions(discovery);
    }

    @Test
    void streamedClearLoadsGeneratedChunksInScheduledBatchesAndReportsCompletion() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15),
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of());
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Inventory inventory = mock(Inventory.class);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        when(world.isChunkGenerated(0, 0)).thenReturn(true);
        when(world.getChunkAtAsync(0, 0, false)).thenReturn(CompletableFuture.completedFuture(chunk));
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.world("arena")).thenReturn(world);
        when(discovery.discover(any(ChestRegion.class), eq(chunk))).thenReturn(
                List.of(new DiscoveredChest("chest", inventory)));
        AtomicReference<WeaponChestService.ActionResult> completed = new AtomicReference<>();
        WeaponChestService service = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class));

        service.executeAsync(plugin, WeaponChestService.ActionType.CLEAR, completed::set);
        var runnable = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), runnable.capture(), eq(1L), eq(1L));
        for (int tick = 0; tick < 4 && completed.get() == null; tick++) runnable.getValue().run();

        assertNotNull(completed.get());
        assertTrue(completed.get().success());
        assertEquals(1, completed.get().affectedChests());
        verify(inventory).clear();
        verify(task).cancel();
    }

    @Test
    void reportsWhetherAStreamedOperationOwnsTheRoundStartLease() {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 15, 255, 15),
                new WeaponLootCatalog.Settings(10, 25, 5_000), List.of(), List.of(), List.of());
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                .thenReturn(mock(BukkitTask.class));
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        when(discovery.world("arena")).thenReturn(mock(World.class));
        WeaponChestService service = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class));

        assertFalse(service.isOperationActive());
        service.executeAsync(plugin, WeaponChestService.ActionType.CLEAR, ignored -> { });

        assertTrue(service.isOperationActive());
    }

    @Test
    void concurrentOperationIsRejectedWhileFirstOperationIsStillDiscovering() throws Exception {
        GameManager game = mock(GameManager.class);
        when(game.getPhase()).thenReturn(RoundPhase.LOBBY);
        WeaponLootCatalog catalog = new WeaponLootCatalog(
                new BlockPoint("arena", 0, 0, 0), new BlockPoint("arena", 1, 1, 1),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000, 256), List.of(), List.of(), List.of());
        ChestDiscoveryService discovery = mock(ChestDiscoveryService.class);
        CountDownLatch discoveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDiscovery = new CountDownLatch(1);
        when(discovery.discover(any())).thenAnswer(ignored -> {
            discoveryStarted.countDown();
            if (!releaseDiscovery.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release chest discovery.");
            }
            return ChestDiscoveryService.DiscoveryResult.success(List.of());
        });
        WeaponChestService service = new WeaponChestService(
                game, () -> catalog, discovery, mock(ChestLootGenerator.class));

        CompletableFuture<WeaponChestService.ActionResult> first = CompletableFuture.supplyAsync(service::clear);
        assertTrue(discoveryStarted.await(5, TimeUnit.SECONDS));
        WeaponChestService.ActionResult second = service.clear();
        releaseDiscovery.countDown();
        WeaponChestService.ActionResult firstResult = first.get(5, TimeUnit.SECONDS);

        assertFalse(second.success());
        assertTrue(second.errors().stream().anyMatch(error -> error.contains("already running")));
        assertFalse(firstResult.success());
        assertTrue(firstResult.errors().stream().anyMatch(error -> error.contains("No chests")));
        verify(discovery, times(1)).discover(any());
    }

    private static ItemStack item() {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        return item;
    }
}
