package me.DaWHeL.infected.loot;

import java.util.Objects;

public record ChestOperationProgress(String stage, int completed, int total) {
    public ChestOperationProgress {
        stage = Objects.requireNonNull(stage, "stage");
        completed = Math.max(0, completed);
        total = Math.max(1, total);
        completed = Math.min(completed, total);
    }

    public double fraction() {
        return (double) completed / total;
    }
}
