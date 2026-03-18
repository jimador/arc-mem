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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriftEvaluationResult(List<FactVerdict> verdicts) {

    static final int DEFAULT_CONFIDENCE_THRESHOLD = 2;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FactVerdict(
            String factId,
            String verdict,
            String severity,
            String evidenceQuote,
            String reasoning,
            Integer confidence,
            String explanation
    ) {
        public EvalVerdict toEvalVerdict() {
            return toEvalVerdict(DEFAULT_CONFIDENCE_THRESHOLD);
        }

        public EvalVerdict toEvalVerdict(int confidenceThreshold) {
            var v = parseVerdict(verdict);
            var s = parseSeverity(severity, v);
            int conf = confidence != null ? confidence : 3;
            if (v == EvalVerdict.Verdict.CONTRADICTED && conf < confidenceThreshold) {
                v = EvalVerdict.Verdict.NOT_MENTIONED;
                s = EvalVerdict.Severity.NONE;
            }
            return new EvalVerdict(factId, v, s, conf,
                    evidenceQuote != null ? evidenceQuote : "",
                    reasoning != null ? reasoning : "",
                    explanation != null ? explanation : "");
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

    public List<EvalVerdict> toEvalVerdicts() {
        return toEvalVerdicts(DEFAULT_CONFIDENCE_THRESHOLD);
    }

    public List<EvalVerdict> toEvalVerdicts(int confidenceThreshold) {
        if (verdicts == null) {
            return List.of();
        }
        return verdicts.stream()
                       .map(fv -> fv.toEvalVerdict(confidenceThreshold))
                       .toList();
    }
}
