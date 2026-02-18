package dev.dunnam.diceanchors.assembly;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe lock that prevents anchor mutations during context assembly.
 * Acquired before assembling the system prompt, released after the LLM response.
 */
public class AnchorContextLock {

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private volatile String lockedBy;

    public boolean tryLock(String turnId) {
        if (locked.compareAndSet(false, true)) {
            lockedBy = turnId;
            return true;
        }
        return false;
    }

    public void unlock(String turnId) {
        if (turnId.equals(lockedBy)) {
            lockedBy = null;
            locked.set(false);
        }
    }

    public boolean isLocked() {
        return locked.get();
    }

    public String getLockedBy() {
        return lockedBy;
    }
}
