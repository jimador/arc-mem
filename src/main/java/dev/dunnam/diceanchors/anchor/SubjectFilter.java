package dev.dunnam.diceanchors.anchor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filters anchors to those sharing subjects with incoming text.
 * Reduces LLM calls by pre-filtering candidates for semantic conflict detection.
 * <p>
 * Subject extraction heuristics:
 * <ul>
 *   <li>Capitalized words as named entities (e.g., "Mars", "Bigby")</li>
 *   <li>Explicit topic markers ("about X", "regarding X")</li>
 *   <li>Key nouns after determiners ("the king", "a dragon")</li>
 * </ul>
 */
public class SubjectFilter {

    private static final Logger logger = LoggerFactory.getLogger(SubjectFilter.class);
    private static final Pattern CAPITALIZED = Pattern.compile("\\b([A-Z][a-z]{2,})\\b");
    private static final Pattern TOPIC_MARKER = Pattern.compile("(?:about|regarding|concerning)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DETERMINER_NOUN = Pattern.compile("(?:the|a|an|this|that)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    /**
     * Filter anchors to those sharing at least one subject with the incoming text.
     *
     * @return anchors with subject overlap; if no subjects found, returns all anchors (safe fallback)
     */
    public List<Anchor> filterCandidates(String incomingText, List<Anchor> anchors) {
        var incomingSubjects = extractSubjects(incomingText);
        if (incomingSubjects.isEmpty()) {
            logger.debug("No subjects extracted from incoming text, returning all {} anchors", anchors.size());
            return anchors;
        }

        var filtered = anchors.stream()
                .filter(anchor -> hasSubjectOverlap(incomingSubjects, extractSubjects(anchor.text())))
                .toList();

        logger.debug("Subject filter: {} subjects from incoming, {}/{} anchors have overlap",
                     incomingSubjects.size(), filtered.size(), anchors.size());
        return filtered.isEmpty() ? anchors : filtered;
    }

    /**
     * Extract subject terms from text using heuristics.
     */
    Set<String> extractSubjects(String text) {
        var subjects = new HashSet<String>();

        var capitalizedMatcher = CAPITALIZED.matcher(text);
        while (capitalizedMatcher.find()) {
            subjects.add(capitalizedMatcher.group(1).toLowerCase());
        }

        var topicMatcher = TOPIC_MARKER.matcher(text);
        while (topicMatcher.find()) {
            subjects.add(topicMatcher.group(1).toLowerCase());
        }

        var determinerMatcher = DETERMINER_NOUN.matcher(text);
        while (determinerMatcher.find()) {
            subjects.add(determinerMatcher.group(1).toLowerCase());
        }

        return subjects;
    }

    private boolean hasSubjectOverlap(Set<String> a, Set<String> b) {
        for (var subject : a) {
            if (b.contains(subject)) {
                return true;
            }
        }
        return false;
    }
}
