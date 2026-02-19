package dev.dunnam.diceanchors.assembly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorContextLock")
class AnchorContextLockTest {

    private AnchorContextLock lock;

    @BeforeEach
    void setUp() {
        lock = new AnchorContextLock();
    }

    @Nested
    @DisplayName("tryLock")
    class TryLock {

        @Test
        @DisplayName("acquires available lock")
        void acquiresAvailableLock() {
            assertThat(lock.tryLock("turn-1")).isTrue();
            assertThat(lock.isLocked()).isTrue();
        }

        @Test
        @DisplayName("rejects concurrent attempt")
        void rejectsConcurrentAttempt() {
            lock.tryLock("turn-1");
            assertThat(lock.tryLock("turn-2")).isFalse();
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("releases lock")
        void releasesLock() {
            lock.tryLock("turn-1");
            lock.unlock("turn-1");
            assertThat(lock.isLocked()).isFalse();
        }

        @Test
        @DisplayName("wrong owner cannot unlock")
        void wrongOwnerCannotUnlock() {
            lock.tryLock("turn-1");
            lock.unlock("turn-2");
            assertThat(lock.isLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("getLockedBy")
    class GetLockedBy {

        @Test
        @DisplayName("returns lock owner when locked")
        void returnsOwnerWhenLocked() {
            lock.tryLock("turn-1");
            assertThat(lock.getLockedBy()).isEqualTo("turn-1");
        }

        @Test
        @DisplayName("returns null when not locked")
        void returnsNullWhenUnlocked() {
            assertThat(lock.getLockedBy()).isNull();
        }
    }
}
