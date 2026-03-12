package dev.dunnam.diceanchors.anchor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConflictDetector.Conflict")
class ConflictRecordTest {

    private static final Anchor ANCHOR =
            Anchor.withoutTrust("a1", "Anakin is a wizard", 500, Authority.PROVISIONAL, false, 0.9, 0);

    @Test
    @DisplayName("four argument constructor defaults FULL quality and null conflictType")
    void fourArgumentConstructorDefaults() {
        var conflict = new ConflictDetector.Conflict(ANCHOR, "Anakin is a bard", 0.9, "conflict");

        assertThat(conflict.detectionQuality()).isEqualTo(ConflictDetector.DetectionQuality.FULL);
        assertThat(conflict.conflictType()).isNull();
    }

    @Test
    @DisplayName("five argument constructor preserves quality and defaults null conflictType")
    void fiveArgumentConstructorDefaultsConflictType() {
        var conflict = new ConflictDetector.Conflict(
                ANCHOR,
                "Anakin is a bard",
                0.7,
                "fallback",
                ConflictDetector.DetectionQuality.FALLBACK);

        assertThat(conflict.detectionQuality()).isEqualTo(ConflictDetector.DetectionQuality.FALLBACK);
        assertThat(conflict.conflictType()).isNull();
    }

    @Test
    @DisplayName("six argument constructor stores conflictType")
    void sixArgumentConstructorStoresConflictType() {
        var conflict = new ConflictDetector.Conflict(
                ANCHOR,
                "Anakin is a bard",
                0.9,
                "explicit correction",
                ConflictDetector.DetectionQuality.FULL,
                ConflictType.REVISION);

        assertThat(conflict.conflictType()).isEqualTo(ConflictType.REVISION);
    }
}
