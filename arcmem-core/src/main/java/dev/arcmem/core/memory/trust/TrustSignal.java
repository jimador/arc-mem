package dev.arcmem.core.memory.trust;
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

import dev.arcmem.core.persistence.PropositionNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalDouble;

/**
 * SPI for evaluating a single dimension of trustworthiness for a proposition.
 * <p>
 * Each implementation contributes one signal to the composite trust score computed
 * by {@link TrustEvaluator}. Multiple signals are combined via weighted averaging;
 * absent signals (returned as {@link OptionalDouble#empty()}) have their weight
 * redistributed to the signals that are present.
 * <p>
 * <strong>Thread-safety:</strong> implementations MUST be thread-safe. A single
 * {@code TrustSignal} instance MAY be shared across concurrent evaluation calls
 * (e.g., registered as a Spring {@code @Component}).
 * <p>
 * <strong>Error handling:</strong> implementations MUST NOT throw exceptions.
 * On any failure (missing data, external service error, etc.), return
 * {@link OptionalDouble#empty()} to indicate the signal is not applicable.
 */
public interface TrustSignal {

    /**
     * Unique name for this signal, used as key in the signal audit map
     * and in {@link DomainProfile} weight configurations.
     *
     * @return a stable, non-null, non-empty signal identifier
     */
    String name();

    /**
     * Evaluate the trust signal for a proposition within a context.
     * <p>
     * Implementations MUST return a value in {@code [0.0, 1.0]} when present,
     * where {@code 0.0} represents minimum trust and {@code 1.0} represents maximum trust.
     * Return {@link OptionalDouble#empty()} when this signal is not applicable to the
     * given proposition (e.g., required metadata is absent).
     * <p>
     * Implementations MUST NOT throw exceptions — return empty on any failure.
     *
     * @param proposition the proposition node to evaluate; never null
     * @param contextId   the conversation or session context identifier; never null
     * @return signal value in {@code [0.0, 1.0]}, or empty if not applicable
     */
    OptionalDouble evaluate(PropositionNode proposition, String contextId);

    /**
     * Creates a trust signal that passes through the DICE extraction confidence score directly.
     * The confidence value produced by DICE is already in {@code [0.0, 1.0]} and requires no
     * further transformation.
     *
     * @return a thread-safe {@link TrustSignal} named {@code "extractionConfidence"}
     */
    static TrustSignal extractionConfidence() {
        return new TrustSignal() {
            @Override
            public String name() { return "extractionConfidence"; }

            @Override
            public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
                return OptionalDouble.of(proposition.getConfidence());
            }
        };
    }

    /**
     * Creates a corroboration trust signal that scores based on the number of distinct
     * source IDs that have asserted the proposition.
     *
     * @return a thread-safe {@link TrustSignal} named {@code "corroboration"}
     */
    static TrustSignal corroboration() {
        return new CorroborationSignal();
    }



    /**
     * Trust signal based on how many distinct sources corroborate a proposition.
     * <p>
     * Uses count-based scoring rather than source-type weighting because source type
     * cannot be reliably inferred from source IDs alone.
     * <p>
     * Scoring: 1 source = 0.3, 2 sources = 0.6, 3+ sources = 0.9.
     * Returns empty when sourceIds information is not available.
     */
    class CorroborationSignal implements TrustSignal {

        @Override
        public String name() {
            return "corroboration";
        }

        @Override
        public OptionalDouble evaluate(PropositionNode proposition, String contextId) {
            List<String> sourceIds = proposition.getSourceIds();
            if (sourceIds == null || sourceIds.isEmpty()) {
                return OptionalDouble.empty();
            }

            long distinctCount = sourceIds.stream().distinct().count();
            if (distinctCount >= 3) {
                return OptionalDouble.of(0.9);
            } else if (distinctCount == 2) {
                return OptionalDouble.of(0.6);
            } else {
                return OptionalDouble.of(0.3);
            }
        }
    }

}
