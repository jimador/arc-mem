package dev.dunnam.diceanchors.anchor;

import com.embabel.dice.projection.prolog.PrologEngine;
import com.embabel.dice.projection.prolog.PrologRuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects {@link Anchor} instances to a {@link PrologEngine} loaded with hybrid fact schema
 * (anchor/5 metadata + claim/4 entity triples) and contradiction rules from anchor-rules.pl.
 *
 * <p>Implements the deterministic pre-filter pattern from Sleeping LLM (Guo et al., 2025):
 * logically decidable contradictions are resolved by Prolog; semantic ambiguity is deferred
 * to LLM fallback.
 *
 * <p>A fresh {@link PrologEngine} is created per projection call. At 20 anchors the theory
 * is under 5KB and construction takes under 5ms.
 */
@Service
public class AnchorPrologProjector {

    private static final Logger logger = LoggerFactory.getLogger(AnchorPrologProjector.class);

    private static final String RULES_PATH = "prolog/anchor-rules.pl";

    /**
     * Projects anchors to a Prolog engine containing anchor/5 and claim/4 facts plus rules.
     */
    public PrologEngine project(List<Anchor> anchors) {
        return buildEngine(anchors, "");
    }

    /**
     * Like {@link #project(List)} but also adds a synthetic anchor fact for incoming text
     * so rules like {@code conflicts_with_incoming/1} can fire.
     */
    public PrologEngine projectWithIncoming(List<Anchor> anchors, String incomingText) {
        var incomingFacts = String.join("\n", decomposeText("incoming", incomingText));
        return buildEngine(anchors, incomingFacts);
    }

    private PrologEngine buildEngine(List<Anchor> anchors, String additionalFacts) {
        var theory = buildTheory(anchors, additionalFacts);
        logger.info("Projecting {} anchors to Prolog: facts={} theoryBytes={}",
                anchors.size(), countFacts(anchors), theory.length());
        return PrologEngine.Companion.fromTheory(theory);
    }

    private String buildTheory(List<Anchor> anchors, String additionalFacts) {
        var sb = new StringBuilder();

        for (var anchor : anchors) {
            sb.append(anchorFact(anchor)).append("\n");
            for (var claim : decomposeText(anchor.id(), anchor.text())) {
                sb.append(claim).append("\n");
            }
        }

        if (!additionalFacts.isBlank()) {
            sb.append(additionalFacts).append("\n");
        }

        var rules = loadRules();
        sb.append(rules);

        return sb.toString();
    }

    private String loadRules() {
        try {
            return PrologRuleLoader.INSTANCE.loadFromResource(RULES_PATH,
                    Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            logger.warn("Failed to load {} from context classloader, trying class classloader: {}",
                    RULES_PATH, e.getMessage());
        }
        try {
            return PrologRuleLoader.INSTANCE.loadFromResource(RULES_PATH,
                    AnchorPrologProjector.class.getClassLoader());
        } catch (Exception e) {
            logger.warn("Failed to load {}: {}", RULES_PATH, e.getMessage());
            return "";
        }
    }

    /**
     * Produces the {@code anchor/5} fact: anchor(Id, AuthOrdinal, Rank, Pinned, ReinforcementCount).
     * Authority ordinal: 0=PROVISIONAL, 1=UNRELIABLE, 2=RELIABLE, 3=CANON.
     */
    String anchorFact(Anchor anchor) {
        int authOrdinal = anchor.authority().ordinal();
        String pinned = anchor.pinned() ? "true" : "false";
        return String.format("anchor('%s', %d, %d, %s, %d).",
                escapeAtom(anchor.id()), authOrdinal, anchor.rank(), pinned, anchor.reinforcementCount());
    }

    /**
     * Heuristic SVO decomposition producing {@code claim/4} fact strings.
     * Splits on sentence boundaries; extracts subject (pre-verb words), predicate (first verb-like word),
     * object (remainder). Falls back to {@code claim(Id, unknown, states, NormalizedFullText)}.
     */
    List<String> decomposeText(String anchorId, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        var claims = new ArrayList<String>();
        var sentences = text.split("[.;]\\s*");

        for (var sentence : sentences) {
            var trimmed = sentence.trim();
            if (trimmed.isBlank()) continue;
            claims.add(decomposeSentence(anchorId, trimmed));
        }

        if (claims.isEmpty()) {
            claims.add(fallbackClaim(anchorId, text));
        }

        return claims;
    }

    private String decomposeSentence(String anchorId, String sentence) {
        var tokens = sentence.split("\\s+");
        if (tokens.length < 2) {
            return fallbackClaim(anchorId, sentence);
        }

        int verbIndex = findFirstVerbLike(tokens);
        if (verbIndex < 1 || verbIndex >= tokens.length - 1) {
            return fallbackClaim(anchorId, sentence);
        }

        var subject = joinTokens(tokens, 0, verbIndex);
        var predicate = normalize(tokens[verbIndex]);
        var object = joinTokens(tokens, verbIndex + 1, tokens.length);

        if (subject.isBlank() || predicate.isBlank() || object.isBlank()) {
            return fallbackClaim(anchorId, sentence);
        }

        return String.format("claim('%s', '%s', '%s', '%s').",
                escapeAtom(anchorId), escapeAtom(subject), escapeAtom(predicate), escapeAtom(object));
    }

    private String fallbackClaim(String anchorId, String text) {
        return String.format("claim('%s', 'unknown', 'states', '%s').",
                escapeAtom(anchorId), escapeAtom(normalize(text)));
    }

    private int findFirstVerbLike(String[] tokens) {
        for (int i = 1; i < tokens.length; i++) {
            var t = tokens[i].toLowerCase().replaceAll("[^a-z]", "");
            if (isVerbLike(t)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isVerbLike(String word) {
        return switch (word) {
            case "is", "are", "was", "were", "has", "have", "had",
                 "leads", "led", "controls", "holds", "rules",
                 "commands", "guards", "defends", "attacks", "captures",
                 "contains", "belongs", "owns", "seeks" -> true;
            default -> word.endsWith("s") && word.length() > 3;
        };
    }

    private String joinTokens(String[] tokens, int from, int to) {
        var sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (!sb.isEmpty()) sb.append('_');
            sb.append(normalize(tokens[i]));
        }
        return sb.toString();
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String escapeAtom(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private int countFacts(List<Anchor> anchors) {
        return anchors.stream()
                .mapToInt(a -> 1 + Math.max(1, a.text().split("[.;]\\s*").length))
                .sum();
    }
}
