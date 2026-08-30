package me.DaWHeL.infected.Roles;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SurvivorTest {

    @Test
    void creatingLobbyMembershipDoesNotMutatePlayerState() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        clearInvocations(player, inventory);

        new Survivor(player);

        verifyNoInteractions(player, inventory);
    }
}
