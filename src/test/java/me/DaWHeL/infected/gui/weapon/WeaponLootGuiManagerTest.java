package me.DaWHeL.infected.gui.weapon;

import me.DaWHeL.infected.InfectedPlugin;
import me.DaWHeL.infected.loot.BlockPoint;
import me.DaWHeL.infected.loot.WeaponChestService;
import me.DaWHeL.infected.loot.WeaponLootCatalog;
import me.DaWHeL.infected.loot.WeaponLootRepository;
import me.DaWHeL.infected.loot.WeaponSelectionListener;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class WeaponLootGuiManagerTest {
    private WeaponLootRepository repository;
    private WeaponChestService chestService;
    private WeaponLootGuiManager manager;
    private Player player;

    @BeforeEach
    void setUp() {
        repository = mock(WeaponLootRepository.class);
        chestService = mock(WeaponChestService.class);
        when(repository.snapshot()).thenReturn(catalog(
                new BlockPoint("arena", 0, 60, 0), new BlockPoint("arena", 10, 70, 10)));
        manager = spy(new WeaponLootGuiManager(
                mock(InfectedPlugin.class), repository, chestService,
                mock(WeaponSelectionListener.class), ignored -> { }));
        player = mock(Player.class);
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
                "BlockPoint[world=arena, x=0, y=60, z=0]|BlockPoint[world=arena, x=5, y=70, z=5]");

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verifyNoInteractions(chestService);
        verify(player).sendMessage(contains("region changed"));
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
    void confirmedClearStartsAStreamedOperationAndReopensWizardAfterCompletion() {
        WeaponMenuHolder confirmation = WeaponMenuHolder.confirmation(
                WeaponMenuHolder.MenuType.CONFIRM_CLEAR,
                "BlockPoint[world=arena, x=0, y=60, z=0]|BlockPoint[world=arena, x=10, y=70, z=10]");
        var completion = org.mockito.ArgumentCaptor.forClass(Consumer.class);

        manager.handleClick(player, confirmation, 11, ClickType.LEFT);

        verify(chestService).executeAsync(any(), eq(WeaponChestService.ActionType.CLEAR), completion.capture());
        verify(player).closeInventory();
        verify(player).sendMessage(contains("Scanning"));
        verify(manager, never()).openWizard(player);

        completion.getValue().accept(new WeaponChestService.ActionResult(true, 3, List.of()));

        verify(player).sendMessage(contains("Successfully cleared 3"));
        verify(manager).openWizard(player);
    }

    private static WeaponLootCatalog catalog(BlockPoint point1, BlockPoint point2) {
        return new WeaponLootCatalog(point1, point2,
                new WeaponLootCatalog.Settings(10, 25, 256),
                List.of(), List.of(), List.of());
    }
}
