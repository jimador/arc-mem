package dev.arcmem.core.persistence;
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

import java.util.Locale;

/**
 * Deterministic identity helper for mention entities.
 */
public final class EntityMentionIdentity {

    private static final String UNRESOLVED_PREFIX = "unresolved:";

    private EntityMentionIdentity() {
    }

    public static String resolveEntityKey(@Nullable String resolvedId, @Nullable String span, @Nullable String type) {
        if (resolvedId != null && !resolvedId.isBlank()) {
            return resolvedId.trim();
        }
        return fallbackKey(span, type);
    }

    public static String fallbackKey(@Nullable String span, @Nullable String type) {
        var normalizedType = normalize(type);
        var normalizedSpan = normalize(span);
        return UNRESOLVED_PREFIX + normalizedType + ":" + normalizedSpan;
    }

    static String normalize(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ")
                    .replace('|', '-');
    }
}
