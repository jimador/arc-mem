package dev.dunnam.diceanchors.anchor.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SupersessionReason")
class SupersessionReasonTest {

    @Test
    @DisplayName("maps ArchiveReason.REVISION to USER_REVISION")
    void mapsRevisionArchiveReasonToUserRevision() {
        var reason = SupersessionReason.fromArchiveReason(ArchiveReason.REVISION);

        assertThat(reason).isEqualTo(SupersessionReason.USER_REVISION);
    }
}
