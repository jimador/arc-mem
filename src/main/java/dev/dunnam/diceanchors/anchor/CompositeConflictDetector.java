package dev.dunnam.diceanchors.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite conflict detector that chains lexical and semantic detection
 * based on a configurable {@link ConflictDetectionStrategy}.
 * <p>
 * Uses {@link SubjectFilter} to reduce semantic LLM calls by pre-filtering
 * anchors to those sharing subjects with the incoming text.
 */
public class CompositeConflictDetector implements ConflictDetector {

    private static final Logger logger = LoggerFactory.getLogger(CompositeConflictDetector.class);

    private final NegationConflictDetector lexicalDetector;
    private final ConflictDetector semanticDetector;
    private final SubjectFilter subjectFilter;
    private final ConflictDetectionStrategy strategy;

    public CompositeConflictDetector(NegationConflictDetector lexicalDetector,
                                     ConflictDetector semanticDetector,
                                     SubjectFilter subjectFilter,
                                     ConflictDetectionStrategy strategy) {
        this.lexicalDetector = lexicalDetector;
        this.semanticDetector = semanticDetector;
        this.subjectFilter = subjectFilter;
        this.strategy = strategy;
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
        };
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
