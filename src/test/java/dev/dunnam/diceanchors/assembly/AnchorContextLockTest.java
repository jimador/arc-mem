package dev.dunnam.diceanchors.assembly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorContextLock")
class AnchorContextLockTest {

    @Test
    @DisplayName("lock and unlock cycle")
    void lockAndUnlock() {
        var lock = new AnchorContextLock();
        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.tryLock("turn-1")).isTrue();
        assertThat(lock.isLocked()).isTrue();
        assertThat(lock.getLockedBy()).isEqualTo("turn-1");
        lock.unlock("turn-1");
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    @DisplayName("second lock attempt fails while locked")
    void secondLockFails() {
        var lock = new AnchorContextLock();
        lock.tryLock("turn-1");
        assertThat(lock.tryLock("turn-2")).isFalse();
    }

    @Test
    @DisplayName("wrong turn cannot unlock")
    void wrongTurnCannotUnlock() {
        var lock = new AnchorContextLock();
        lock.tryLock("turn-1");
        lock.unlock("turn-2"); // wrong ID
        assertThat(lock.isLocked()).isTrue(); // still locked
    }
}
