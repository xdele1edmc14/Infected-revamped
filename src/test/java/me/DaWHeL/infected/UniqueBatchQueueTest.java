package me.DaWHeL.infected;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueBatchQueueTest {

    @Test
    void processesEachKeyOnceAcrossMultipleBatches() {
        UniqueBatchQueue<String, String> queue = new UniqueBatchQueue<>(
                List.of("alpha", "bravo", "alpha", "charlie"), value -> value);

        assertEquals(3, queue.size());
        assertEquals(List.of("alpha", "bravo"), queue.nextBatch(2));
        assertEquals(List.of("charlie"), queue.nextBatch(2));
        assertTrue(queue.isComplete());
        assertEquals(List.of(), queue.nextBatch(2));
    }

    @Test
    void keepsTheLastValueForARepeatedKeyWithoutChangingItsOrder() {
        record Entry(String id, int revision) {
        }
        Entry first = new Entry("alpha", 1);
        Entry replacement = new Entry("alpha", 2);
        Entry second = new Entry("bravo", 1);
        UniqueBatchQueue<String, Entry> queue = new UniqueBatchQueue<>(
                List.of(first, second, replacement), Entry::id);

        assertEquals(List.of(replacement, second), queue.nextBatch(5));
    }

    @Test
    void rejectsAnInvalidBatchSize() {
        UniqueBatchQueue<String, String> queue = new UniqueBatchQueue<>(List.of("alpha"), value -> value);

        assertThrows(IllegalArgumentException.class, () -> queue.nextBatch(0));
    }
}
