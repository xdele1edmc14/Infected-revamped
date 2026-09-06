package me.DaWHeL.infected.loot;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ChestOperationGate {
    private final AtomicBoolean active = new AtomicBoolean();

    public Lease tryAcquire() {
        return active.compareAndSet(false, true) ? new Lease() : null;
    }

    public boolean isActive() {
        return active.get();
    }

    public final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease() {
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) active.set(false);
        }
    }
}
