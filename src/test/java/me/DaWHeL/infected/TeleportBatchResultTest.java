package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportBatchResultTest {

    @Test
    void succeedsOnlyWhenEveryAttemptSucceededAndNoErrorExists() {
        assertTrue(new TeleportBatchResult(2, 2, List.of(), null).success());
        assertFalse(new TeleportBatchResult(2, 1, List.of(UUID.randomUUID()), null).success());
        assertFalse(new TeleportBatchResult(0, 0, List.of(), "No survivor spawns are available.").success());
    }

    @Test
    void protectsTheFailedPlayerListFromMutation() {
        ArrayList<UUID> failed = new ArrayList<>(List.of(UUID.randomUUID()));
        TeleportBatchResult result = new TeleportBatchResult(1, 0, failed, null);

        failed.clear();

        assertFalse(result.failedPlayerIds().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.failedPlayerIds().clear());
    }
}
