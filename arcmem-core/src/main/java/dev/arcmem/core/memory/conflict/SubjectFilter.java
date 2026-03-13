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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filters units to those sharing subjects with incoming text.
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
     * Filter units to those sharing at least one subject with the incoming text.
     *
     * @return units with subject overlap; if no subjects found, returns all units (safe fallback)
     */
    public List<MemoryUnit> filterCandidates(String incomingText, List<MemoryUnit> units) {
        var incomingSubjects = extractSubjects(incomingText);
        if (incomingSubjects.isEmpty()) {
            logger.debug("No subjects extracted from incoming text, returning all {} units", units.size());
            return units;
        }

        var filtered = units.stream()
                .filter(unit -> hasSubjectOverlap(incomingSubjects, extractSubjects(unit.text())))
                .toList();

        logger.debug("Subject filter: {} subjects from incoming, {}/{} units have overlap",
                     incomingSubjects.size(), filtered.size(), units.size());
        return filtered.isEmpty() ? units : filtered;
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
