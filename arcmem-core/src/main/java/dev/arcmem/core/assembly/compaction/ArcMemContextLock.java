package dev.arcmem.core.assembly.compaction;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe lock that prevents unit mutations during context assembly.
 * Acquired before assembling the system prompt, released after the LLM response.
 * <p>
 * Uses a single {@link AtomicReference} to store the current lock holder, eliminating
 * the TOCTOU race between the old {@code AtomicBoolean locked} + {@code volatile lockedBy}
 * pair. All operations are a single compare-and-set.
 */
public class ArcMemContextLock {

    private final AtomicReference<String> lockedBy = new AtomicReference<>();

    public boolean tryLock(String turnId) {
        return lockedBy.compareAndSet(null, turnId);
    }

    public void unlock(String turnId) {
        lockedBy.compareAndSet(turnId, null);
    }

    public boolean isLocked() {
        return lockedBy.get() != null;
    }

    public String getLockedBy() {
        return lockedBy.get();
    }
}
