package me.DaWHeL.infected;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class UniqueBatchQueue<K, T> {
    private final List<T> values;
    private int index;

    public UniqueBatchQueue(Collection<T> source, Function<T, K> keySelector) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(keySelector, "keySelector");

        LinkedHashMap<K, T> unique = new LinkedHashMap<>();
        for (T value : source) {
            T present = Objects.requireNonNull(value, "source value");
            unique.put(Objects.requireNonNull(keySelector.apply(present), "source key"), present);
        }
        values = List.copyOf(unique.values());
    }

    public List<T> nextBatch(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be at least 1.");
        }
        if (isComplete()) {
            return List.of();
        }

        int end = Math.min(index + batchSize, values.size());
        List<T> batch = new ArrayList<>(values.subList(index, end));
        index = end;
        return List.copyOf(batch);
    }

    public boolean isComplete() {
        return index >= values.size();
    }

    public int size() {
        return values.size();
    }
}
