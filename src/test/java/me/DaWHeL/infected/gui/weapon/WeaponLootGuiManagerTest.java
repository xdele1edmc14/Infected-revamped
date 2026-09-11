package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.loot.BlockPoint;
import me.DaWHeL.infected.loot.ChestOperationProgress;
import me.DaWHeL.infected.loot.GeneratedChestService;
import me.DaWHeL.infected.loot.WeaponChestService;
import me.DaWHeL.infected.loot.WeaponLootCatalog;
import me.DaWHeL.infected.loot.WeaponLootRepository;
import me.DaWHeL.infected.loot.WeaponSelectionListener;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class WeaponLootGuiManagerTest {
    private WeaponLootRepository repository;
    private WeaponChestService chestService;
    private GeneratedChestService generatedChestService;
    private WeaponLootGuiManager manager;
    private Player player;
    private InfectedPlugin plugin;
    private BossBar bossBar;

    @BeforeEach
    void setUp() {
        repository = mock(WeaponLootRepository.class);
        chestService = mock(WeaponChestService.class);
        generatedChestService = mock(GeneratedChestService.class);
        when(repository.snapshot()).thenReturn(catalog(
                new BlockPoint("arena", 0, 60, 0), new BlockPoint("arena", 10, 70, 10)));
        plugin = mock(InfectedPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bossBar = mock(BossBar.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.createBossBar(anyString(), any(), any(), any(BarFlag[].class))).thenReturn(bossBar);
        manager = spy(new WeaponLootGuiManager(
                plugin, repository, chestService, generatedChestService,
                mock(WeaponSelectionListener.class), ignored -> { }));
        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        doNothing().when(manager).openWizard(any());
        doNothing().when(manager).openGuns(any(), anyInt());
        doNothing().when(manager).openGrenades(any(), anyInt());
    }

    @Test
    void wizardRoutesGunAndGrenadeCategoriesToTheirIndependentMenus() {
        WeaponMenuHolder wizard = WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD);

        manager.handleClick(player, wizard, 20, ClickType.LEFT);
        manager.handleClick(player, wizard, 24, ClickType.LEFT);

        verify(manager).openGuns(player, 0);
        verify(manager).openGrenades(player, 0);
    }

    @Test
    void changedRegionCannotExecuteAStaleFillConfirmation() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_FILL,
                "catalog=-1|layout=0");

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verifyNoInteractions(chestService);
        verify(player).sendMessage(contains("configuration or generated layout changed"));
        verify(manager).openWizard(player);
    }

    @Test
    void changedWeaponCatalogCannotExecuteAStaleFillConfirmation() {
        when(repository.revision()).thenReturn(8L);
        when(generatedChestService.layoutRevision()).thenReturn(11L);
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_FILL, "catalog=7|layout=11");

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verifyNoInteractions(chestService);
        verify(player).sendMessage(contains("configuration or generated layout changed"));
        verify(manager).openWizard(player);
    }

    @Test
    void changedGeneratedLayoutCannotExecuteAStaleRemoveConfirmation() {
        when(repository.revision()).thenReturn(7L);
        when(generatedChestService.layoutRevision()).thenReturn(12L);
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_REMOVE, "catalog=7|layout=11");

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verify(generatedChestService, never()).executeAsync(any(), any(), any(), any());
        verify(player).sendMessage(contains("configuration or generated layout changed"));
        verify(manager).openWizard(player);
    }

    @Test
    void wizardRaisesTheChunkLimitInFiveHundredChunkStepsWithoutExceedingFiveThousand() {
        when(repository.snapshot()).thenReturn(new WeaponLootCatalog(
                new BlockPoint("arena", 0, 60, 0), new BlockPoint("arena", 10, 70, 10),
                new WeaponLootCatalog.Settings(10, 25, 4_750), List.of(), List.of(), List.of()));

        manager.handleClick(player, WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD),
                31, ClickType.SHIFT_LEFT);

        verify(repository).setMaxChunks(5_000);
        verify(manager).openWizard(player);
    }

    @Test
    void wizardRaisesGeneratedChestCountInFiftyChestStepsWithoutExceedingOneThousand() {
        when(repository.snapshot()).thenReturn(new WeaponLootCatalog(
                new BlockPoint("arena", 0, 60, 0), new BlockPoint("arena", 10, 70, 10),
                new WeaponLootCatalog.Settings(10, 25, 2_000_000L, 4_750, 975),
                List.of(), List.of(), List.of()));

        manager.handleClick(player, WeaponMenuHolder.root(WeaponMenuHolder.MenuType.WIZARD),
                33, ClickType.SHIFT_LEFT);

        verify(repository).setGeneratedChestCount(1_000);
        verify(manager).openWizard(player);
    }

    @Test
    void droppingACursorItemIntoTheGunInputRegistersAnExactCopyWithoutUsingMainHand() {
        ItemStack cursor = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(cursor.clone()).thenReturn(copy);

        manager.handleDrop(player, WeaponMenuHolder.page(
                WeaponMenuHolder.MenuType.GUNS, 0, java.util.Map.of()), 49, cursor);

        verify(repository).addGun(copy);
        verify(player, never()).getInventory();
        verify(manager).openGuns(player, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmedEmptyStartsAStreamedOperationAndReopensWizardAfterCompletion() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_CLEAR,
                "catalog=0|layout=0");
        var completion = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        var progress = org.mockito.ArgumentCaptor.forClass(Consumer.class);

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verify(chestService).executeAsync(any(), eq(WeaponChestService.ActionType.CLEAR),
                progress.capture(), completion.capture());
        verify(player).closeInventory();
        verify(player).sendMessage(contains("Scanning"));
        verify(manager, never()).openWizard(player);

        progress.getValue().accept(new ChestOperationProgress("Emptying chests", 2, 3));
        verify(bossBar).setTitle("Emptying chests — 67% (2 / 3)");

        completion.getValue().accept(new WeaponChestService.ActionResult(true, 3, List.of()));

        verify(player).sendMessage(contains("Successfully emptied 3"));
        verify(manager).openWizard(player);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmedGenerateStartsTheIndependentLayoutOperation() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_GENERATE,
                "catalog=0|layout=0");
        var completion = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        var progress = org.mockito.ArgumentCaptor.forClass(Consumer.class);

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verify(generatedChestService).executeAsync(
                any(), eq(GeneratedChestService.ActionType.GENERATE),
                progress.capture(), completion.capture());
        verify(player).closeInventory();

        progress.getValue().accept(new ChestOperationProgress("Finding sites", 42, 100));
        verify(bossBar).setTitle("Finding sites — 42% (42 / 100)");

        completion.getValue().accept(new GeneratedChestService.ActionResult(true, 100, List.of()));

        verify(player).sendMessage(contains("generated 100"));
        verify(manager).openWizard(player);
    }

    @Test
    void changedGeneratedCountCannotExecuteAStaleGenerateConfirmation() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_GENERATE,
                "catalog=-1|layout=0");

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verify(generatedChestService, never()).executeAsync(any(), any(), any(), any());
        verify(player).sendMessage(contains("configuration or generated layout changed"));
        verify(manager).openWizard(player);
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedAdminReceivesNoFillCompletionUi() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_CLEAR, "catalog=0|layout=0");
        var completion = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        manager.handleClick(player, confirmation, 11, ClickType.LEFT);
        verify(chestService).executeAsync(any(), eq(WeaponChestService.ActionType.CLEAR),
                any(), completion.capture());
        clearInvocations(player, manager);
        when(player.isOnline()).thenReturn(false);

        completion.getValue().accept(new WeaponChestService.ActionResult(true, 3, List.of()));

        verify(player, never()).sendMessage(anyString());
        verify(manager, never()).openWizard(player);
        verify(plugin.getLogger()).info(contains("completed after the administrator disconnected"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void disconnectedAdminReceivesNoGeneratedCompletionUi() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_GENERATE, "catalog=0|layout=0");
        var completion = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        manager.handleClick(player, confirmation, 11, ClickType.LEFT);
        verify(generatedChestService).executeAsync(any(), eq(GeneratedChestService.ActionType.GENERATE),
                any(), completion.capture());
        clearInvocations(player, manager);
        when(player.isOnline()).thenReturn(false);

        completion.getValue().accept(new GeneratedChestService.ActionResult(true, 100, List.of()));

        verify(player, never()).sendMessage(anyString());
        verify(manager, never()).openWizard(player);
        verify(plugin.getLogger()).info(contains("completed after the administrator disconnected"));
    }

    private static WeaponLootCatalog catalog(BlockPoint point1, BlockPoint point2) {
        return new WeaponLootCatalog(point1, point2,
                new WeaponLootCatalog.Settings(10, 25, 256),
                List.of(), List.of(), List.of());
    }
}
