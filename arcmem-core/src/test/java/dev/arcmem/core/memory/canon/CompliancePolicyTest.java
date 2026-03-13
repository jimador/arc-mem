package dev.arcmem.core.memory.canon;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompliancePolicy")
class CompliancePolicyTest {

    @Nested
    @DisplayName("DefaultCompliancePolicy")
    class Default {

        private final CompliancePolicy policy = CompliancePolicy.flat();

        @Test
        @DisplayName("maps all authorities to STRICT")
        void allStrict() {
            for (var authority : Authority.values()) {
                assertThat(policy.getStrengthFor(authority))
                        .as("Authority %s should be STRICT", authority)
                        .isEqualTo(ComplianceStrength.STRICT);
            }
        }
    }

    @Nested
    @DisplayName("AuthorityTieredCompliancePolicy")
    class Tiered {

        private final CompliancePolicy policy = CompliancePolicy.tiered();

        @Test
        @DisplayName("CANON maps to STRICT")
        void canonStrict() {
            assertThat(policy.getStrengthFor(Authority.CANON)).isEqualTo(ComplianceStrength.STRICT);
        }

        @Test
        @DisplayName("RELIABLE maps to STRICT")
        void reliableStrict() {
            assertThat(policy.getStrengthFor(Authority.RELIABLE)).isEqualTo(ComplianceStrength.STRICT);
        }

        @Test
        @DisplayName("UNRELIABLE maps to MODERATE")
        void unreliableModerate() {
            assertThat(policy.getStrengthFor(Authority.UNRELIABLE)).isEqualTo(ComplianceStrength.MODERATE);
        }

        @Test
        @DisplayName("PROVISIONAL maps to PERMISSIVE")
        void provisionalPermissive() {
            assertThat(policy.getStrengthFor(Authority.PROVISIONAL)).isEqualTo(ComplianceStrength.PERMISSIVE);
        }

        @Test
        @DisplayName("all Authority values are handled")
        void allAuthoritiesHandled() {
            for (var authority : Authority.values()) {
                assertThat(policy.getStrengthFor(authority)).isNotNull();
            }
        }
    }
}
