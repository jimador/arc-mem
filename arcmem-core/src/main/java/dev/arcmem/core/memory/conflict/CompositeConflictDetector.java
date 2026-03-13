package dev.arcmem.core.memory.conflict;
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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composite conflict detector that chains lexical and semantic detection
 * based on a configurable {@link ConflictDetectionStrategy}.
 * <p>
 * Uses {@link SubjectFilter} to reduce semantic LLM calls by pre-filtering
 * units to those sharing subjects with the incoming text.
 * <p>
 * When strategy is {@link ConflictDetectionStrategy#INDEXED}, consults the
 * {@link ConflictIndex} first and falls back to semantic detection on cache miss.
 */
public class CompositeConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(CompositeConflictDetector.class);

    private final NegationConflictDetector lexicalDetector;
    private final ConflictDetector semanticDetector;
    private final SubjectFilter subjectFilter;
    private final ConflictDetectionStrategy strategy;
    private final @Nullable ConflictIndex conflictIndex;
    private final @Nullable PrologConflictDetector logicalDetector;

    public CompositeConflictDetector(NegationConflictDetector lexicalDetector,
                                     ConflictDetector semanticDetector,
                                     SubjectFilter subjectFilter,
                                     ConflictDetectionStrategy strategy,
                                     @Nullable ConflictIndex conflictIndex,
                                     @Nullable PrologConflictDetector logicalDetector) {
        this.lexicalDetector = lexicalDetector;
        this.semanticDetector = semanticDetector;
        this.subjectFilter = subjectFilter;
        this.strategy = strategy;
        this.conflictIndex = conflictIndex;
        this.logicalDetector = logicalDetector;
    }

    /** Backward-compatible 5-parameter constructor -- delegates with null logical detector. */
    public CompositeConflictDetector(NegationConflictDetector lexicalDetector,
                                     ConflictDetector semanticDetector,
                                     SubjectFilter subjectFilter,
                                     ConflictDetectionStrategy strategy,
                                     @Nullable ConflictIndex conflictIndex) {
        this(lexicalDetector, semanticDetector, subjectFilter, strategy, conflictIndex, null);
    }

    /** Backward-compatible 4-parameter constructor -- delegates with null index and null logical detector. */
    public CompositeConflictDetector(NegationConflictDetector lexicalDetector,
                                     ConflictDetector semanticDetector,
                                     SubjectFilter subjectFilter,
                                     ConflictDetectionStrategy strategy) {
        this(lexicalDetector, semanticDetector, subjectFilter, strategy, null, null);
    }

    @Override
    public List<Conflict> detect(String incomingText, List<MemoryUnit> existingUnits) {
        if (existingUnits == null || existingUnits.isEmpty()) {
            return List.of();
        }

        return switch (strategy) {
            case LEXICAL_ONLY -> lexicalDetector.detect(incomingText, existingUnits);
            case SEMANTIC_ONLY -> detectSemantic(incomingText, existingUnits);
            case LEXICAL_THEN_SEMANTIC -> detectLexicalThenSemantic(incomingText, existingUnits);
            case INDEXED -> detectIndexed(incomingText, existingUnits);
            case LOGICAL -> {
                if (logicalDetector != null) {
                    yield logicalDetector.detect(incomingText, existingUnits);
                }
                throw new UnsupportedOperationException(
                        "LOGICAL conflict detection requires PrologConflictDetector -- none injected");
            }
        };
    }

    @Override
    public Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        if (existingUnits == null || existingUnits.isEmpty()) {
            return candidateTexts.stream()
                    .collect(java.util.stream.Collectors.toMap(c -> c, c -> List.of()));
        }

        return switch (strategy) {
            case LEXICAL_ONLY -> lexicalDetector.batchDetect(candidateTexts, existingUnits);
            case SEMANTIC_ONLY -> semanticDetector.batchDetect(candidateTexts, existingUnits);
            case LEXICAL_THEN_SEMANTIC -> batchDetectLexicalThenSemantic(candidateTexts, existingUnits);
            case INDEXED -> batchDetectIndexed(candidateTexts, existingUnits);
            case LOGICAL -> {
                if (logicalDetector != null) {
                    yield logicalDetector.batchDetect(candidateTexts, existingUnits);
                }
                throw new UnsupportedOperationException(
                        "LOGICAL conflict detection requires PrologConflictDetector -- none injected");
            }
        };
    }

    private List<Conflict> detectIndexed(String incomingText, List<MemoryUnit> existingUnits) {
        if (conflictIndex == null) {
            logger.warn("INDEXED strategy requested but no ConflictIndex available -- falling back to LEXICAL_THEN_SEMANTIC");
            return detectLexicalThenSemantic(incomingText, existingUnits);
        }

        var conflicts = new ArrayList<Conflict>();
        var missUnits = new ArrayList<MemoryUnit>();

        for (var unit : existingUnits) {
            var entries = conflictIndex.getConflicts(unit.id());
            var hit = entries.stream().findFirst();
            if (hit.isPresent()) {
                var entry = hit.get();
                conflicts.add(new Conflict(unit, incomingText, entry.confidence(),
                        "index hit: " + entry.conflictType(), DetectionQuality.FULL, entry.conflictType()));
            } else {
                missUnits.add(unit);
            }
        }

        if (!missUnits.isEmpty()) {
            var semanticResults = detectSemantic(incomingText, missUnits);
            for (var conflict : semanticResults) {
                if (conflict.existing() != null) {
                    var unit = conflict.existing();
                    var conflictType = conflict.conflictType() != null
                            ? conflict.conflictType()
                            : ConflictType.CONTRADICTION;
                    var entry = new ConflictEntry(
                            unit.id(),
                            unit.text(),
                            unit.authority(),
                            conflictType,
                            conflict.confidence(),
                            Instant.now()
                    );
                    conflictIndex.recordConflict(unit.id(), entry);
                }
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    private Map<String, List<Conflict>> batchDetectIndexed(
            List<String> candidateTexts, List<MemoryUnit> existingUnits) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidateTexts) {
            result.put(candidate, detectIndexed(candidate, existingUnits));
        }
        return result;
    }

    private Map<String, List<Conflict>> batchDetectLexicalThenSemantic(
            List<String> candidateTexts, List<MemoryUnit> units) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        var semanticBatch = new ArrayList<String>();

        var lexicalResults = lexicalDetector.batchDetect(candidateTexts, units);
        for (var candidate : candidateTexts) {
            var lexicalConflicts = lexicalResults.getOrDefault(candidate, List.of());
            if (!lexicalConflicts.isEmpty()) {
                logger.info("Lexical batch: found {} conflicts for candidate, skipping semantic", lexicalConflicts.size());
                result.put(candidate, lexicalConflicts);
            } else {
                semanticBatch.add(candidate);
            }
        }

        if (!semanticBatch.isEmpty()) {
            try {
                var semanticResults = semanticDetector.batchDetect(semanticBatch, units);
                result.putAll(semanticResults);
            } catch (Exception e) {
                logger.warn("Batch semantic conflict detection failed, falling back to per-candidate: {}",
                        e.getMessage());
                for (var candidate : semanticBatch) {
                    result.put(candidate, detectSemantic(candidate, units));
                }
            }
        }
        return result;
    }

    private List<Conflict> detectLexicalThenSemantic(String incomingText, List<MemoryUnit> units) {
        var lexicalConflicts = lexicalDetector.detect(incomingText, units);
        if (!lexicalConflicts.isEmpty()) {
            logger.info("Lexical detector found {} conflicts, skipping semantic", lexicalConflicts.size());
            return lexicalConflicts;
        }
        return detectSemantic(incomingText, units);
    }

    private List<Conflict> detectSemantic(String incomingText, List<MemoryUnit> units) {
        var filtered = subjectFilter.filterCandidates(incomingText, units);
        logger.debug("Subject filter reduced {} units to {} candidates for semantic check",
                     units.size(), filtered.size());
        return semanticDetector.detect(incomingText, filtered);
    }
}
