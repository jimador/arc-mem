package dev.arcmem.core.memory.event;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

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
