package me.DaWHeL.infected.loot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChestOperationGateTest {
    @Test
    void permitsOnlyOneChestMutationUntilItsLeaseCloses() {
        ChestOperationGate gate = new ChestOperationGate();
        ChestOperationGate.Lease first = gate.tryAcquire();

        assertNotNull(first);
        assertTrue(gate.isActive());
        assertNull(gate.tryAcquire());

        first.close();

        assertFalse(gate.isActive());
        assertNotNull(gate.tryAcquire());
    }
}
