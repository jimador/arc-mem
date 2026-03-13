package dev.arcmem.simulator.engine;
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

import dev.arcmem.core.spi.llm.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;

import java.util.List;

/**
 * Structured result from the drift evaluation LLM call.
 * Designed for Jackson deserialization from the evaluator's JSON output.
 */
public record DriftEvaluationResult(List<FactVerdict> verdicts) {

    /**
     * Single fact verdict as returned by the evaluator LLM in JSON format.
     * Mapped to {@link EvalVerdict} after parsing.
     */
    public record FactVerdict(
            String factId,
            String verdict,
            String severity,
            String explanation
    ) {
        /**
         * Convert to the domain-level {@link EvalVerdict}.
         */
        public EvalVerdict toEvalVerdict() {
            var v = parseVerdict(verdict);
            var s = parseSeverity(severity, v);
            return new EvalVerdict(factId, v, s, explanation != null ? explanation : "");
        }

        private static EvalVerdict.Verdict parseVerdict(String raw) {
            if (raw == null) {
                return EvalVerdict.Verdict.NOT_MENTIONED;
            }
            return switch (raw.toUpperCase().trim()) {
                case "CONTRADICTED" -> EvalVerdict.Verdict.CONTRADICTED;
                case "CONFIRMED" -> EvalVerdict.Verdict.CONFIRMED;
                default -> EvalVerdict.Verdict.NOT_MENTIONED;
            };
        }

        private static EvalVerdict.Severity parseSeverity(String raw, EvalVerdict.Verdict verdict) {
            if (verdict != EvalVerdict.Verdict.CONTRADICTED) {
                return EvalVerdict.Severity.NONE;
            }
            if (raw == null) {
                return EvalVerdict.Severity.MAJOR;
            }
            return switch (raw.toUpperCase().trim()) {
                case "MINOR" -> EvalVerdict.Severity.MINOR;
                case "NONE" -> EvalVerdict.Severity.NONE;
                default -> EvalVerdict.Severity.MAJOR;
            };
        }
    }

    /**
     * Convert all fact verdicts to domain-level {@link EvalVerdict} list.
     */
    public List<EvalVerdict> toEvalVerdicts() {
        if (verdicts == null) {
            return List.of();
        }
        return verdicts.stream()
                       .map(FactVerdict::toEvalVerdict)
                       .toList();
    }
}
