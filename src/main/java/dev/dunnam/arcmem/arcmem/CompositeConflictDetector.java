package dev.dunnam.diceanchors.anchor;

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
 * anchors to those sharing subjects with the incoming text.
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
    public List<Conflict> detect(String incomingText, List<Anchor> existingAnchors) {
        if (existingAnchors == null || existingAnchors.isEmpty()) {
            return List.of();
        }

        return switch (strategy) {
            case LEXICAL_ONLY -> lexicalDetector.detect(incomingText, existingAnchors);
            case SEMANTIC_ONLY -> detectSemantic(incomingText, existingAnchors);
            case LEXICAL_THEN_SEMANTIC -> detectLexicalThenSemantic(incomingText, existingAnchors);
            case INDEXED -> detectIndexed(incomingText, existingAnchors);
            case LOGICAL -> {
                if (logicalDetector != null) {
                    yield logicalDetector.detect(incomingText, existingAnchors);
                }
                throw new UnsupportedOperationException(
                        "LOGICAL conflict detection requires PrologConflictDetector -- none injected");
            }
        };
    }

    @Override
    public Map<String, List<Conflict>> batchDetect(List<String> candidateTexts, List<Anchor> existingAnchors) {
        if (existingAnchors == null || existingAnchors.isEmpty()) {
            return candidateTexts.stream()
                    .collect(java.util.stream.Collectors.toMap(c -> c, c -> List.of()));
        }

        return switch (strategy) {
            case LEXICAL_ONLY -> lexicalDetector.batchDetect(candidateTexts, existingAnchors);
            case SEMANTIC_ONLY -> semanticDetector.batchDetect(candidateTexts, existingAnchors);
            case LEXICAL_THEN_SEMANTIC -> batchDetectLexicalThenSemantic(candidateTexts, existingAnchors);
            case INDEXED -> batchDetectIndexed(candidateTexts, existingAnchors);
            case LOGICAL -> {
                if (logicalDetector != null) {
                    yield logicalDetector.batchDetect(candidateTexts, existingAnchors);
                }
                throw new UnsupportedOperationException(
                        "LOGICAL conflict detection requires PrologConflictDetector -- none injected");
            }
        };
    }

    private List<Conflict> detectIndexed(String incomingText, List<Anchor> existingAnchors) {
        if (conflictIndex == null) {
            logger.warn("INDEXED strategy requested but no ConflictIndex available -- falling back to LEXICAL_THEN_SEMANTIC");
            return detectLexicalThenSemantic(incomingText, existingAnchors);
        }

        var conflicts = new ArrayList<Conflict>();
        var missAnchors = new ArrayList<Anchor>();

        for (var anchor : existingAnchors) {
            var entries = conflictIndex.getConflicts(anchor.id());
            var hit = entries.stream().findFirst();
            if (hit.isPresent()) {
                var entry = hit.get();
                conflicts.add(new Conflict(anchor, incomingText, entry.confidence(),
                        "index hit: " + entry.conflictType(), DetectionQuality.FULL, entry.conflictType()));
            } else {
                missAnchors.add(anchor);
            }
        }

        if (!missAnchors.isEmpty()) {
            var semanticResults = detectSemantic(incomingText, missAnchors);
            for (var conflict : semanticResults) {
                if (conflict.existing() != null) {
                    var anchor = conflict.existing();
                    var conflictType = conflict.conflictType() != null
                            ? conflict.conflictType()
                            : ConflictType.CONTRADICTION;
                    var entry = new ConflictEntry(
                            anchor.id(),
                            anchor.text(),
                            anchor.authority(),
                            conflictType,
                            conflict.confidence(),
                            Instant.now()
                    );
                    conflictIndex.recordConflict(anchor.id(), entry);
                }
                conflicts.add(conflict);
            }
        }

        return conflicts;
    }

    private Map<String, List<Conflict>> batchDetectIndexed(
            List<String> candidateTexts, List<Anchor> existingAnchors) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        for (var candidate : candidateTexts) {
            result.put(candidate, detectIndexed(candidate, existingAnchors));
        }
        return result;
    }

    private Map<String, List<Conflict>> batchDetectLexicalThenSemantic(
            List<String> candidateTexts, List<Anchor> anchors) {
        var result = new LinkedHashMap<String, List<Conflict>>();
        var semanticBatch = new ArrayList<String>();

        var lexicalResults = lexicalDetector.batchDetect(candidateTexts, anchors);
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
                var semanticResults = semanticDetector.batchDetect(semanticBatch, anchors);
                result.putAll(semanticResults);
            } catch (Exception e) {
                logger.warn("Batch semantic conflict detection failed, falling back to per-candidate: {}",
                        e.getMessage());
                for (var candidate : semanticBatch) {
                    result.put(candidate, detectSemantic(candidate, anchors));
                }
            }
        }
        return result;
    }

    private List<Conflict> detectLexicalThenSemantic(String incomingText, List<Anchor> anchors) {
        var lexicalConflicts = lexicalDetector.detect(incomingText, anchors);
        if (!lexicalConflicts.isEmpty()) {
            logger.info("Lexical detector found {} conflicts, skipping semantic", lexicalConflicts.size());
            return lexicalConflicts;
        }
        return detectSemantic(incomingText, anchors);
    }

    private List<Conflict> detectSemantic(String incomingText, List<Anchor> anchors) {
        var filtered = subjectFilter.filterCandidates(incomingText, anchors);
        logger.debug("Subject filter reduced {} anchors to {} candidates for semantic check",
                     anchors.size(), filtered.size());
        return semanticDetector.detect(incomingText, filtered);
    }
}
