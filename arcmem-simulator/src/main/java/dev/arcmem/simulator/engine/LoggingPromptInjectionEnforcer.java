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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Scans player messages for heuristic prompt-injection signatures before LLM generation.
 * Always accepts the message (never blocks) but logs each detected pattern and returns
 * the total match count for ContextTrace observability.
 * <p>
 * Thread-safe: stateless with pre-compiled, final patterns.
 */
@Component
public class LoggingPromptInjectionEnforcer {

    private static final Logger logger = LoggerFactory.getLogger(LoggingPromptInjectionEnforcer.class);

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)^system:"),
            Pattern.compile("(?i)forget\\s+(everything|all|what)\\s+"),
            Pattern.compile("(?i)new\\s+instructions?:"),
            Pattern.compile("(?i)act\\s+as\\s+(if\\s+you\\s+are\\s+|a\\s+)"),
            Pattern.compile("(?i)pretend\\s+(you\\s+are\\s+|to\\s+be\\s+)"),
            Pattern.compile("(?i)disregard\\s+(the\\s+)?(above\\s+|prior\\s+)")
    );

    private static final List<String> PATTERN_DESCRIPTIONS = List.of(
            "classic instruction override (ignore previous instructions)",
            "role reassignment (you are now)",
            "system message injection (system:)",
            "memory wipe attempt (forget everything/all/what)",
            "instruction replacement (new instructions:)",
            "role-play injection (act as if/a)",
            "role-play injection variant (pretend you are/to be)",
            "instruction override variant (disregard above/prior)"
    );

    /**
     * Scans the player message for injection-signature patterns.
     *
     * @param playerMessage the raw player input for this turn
     * @return count of distinct patterns matched; 0 if none or input is blank
     */
    public int scan(String playerMessage) {
        if (playerMessage == null || playerMessage.isBlank()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < INJECTION_PATTERNS.size(); i++) {
            if (INJECTION_PATTERNS.get(i).matcher(playerMessage).find()) {
                logger.info("Injection pattern detected in player message: {}", PATTERN_DESCRIPTIONS.get(i));
                count++;
            }
        }
        return count;
    }
}
