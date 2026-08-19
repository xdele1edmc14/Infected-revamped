package me.DaWHeL.infected;

import java.util.List;
import java.util.UUID;

public record TeleportBatchResult(
        int attempted,
        int succeeded,
        List<UUID> failedPlayerIds,
        String error
) {
    public TeleportBatchResult {
        failedPlayerIds = List.copyOf(failedPlayerIds);
    }

    public boolean success() {
        return error == null && attempted == succeeded && failedPlayerIds.isEmpty();
    }
}
