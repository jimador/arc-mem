package dev.arcmem.core.memory.canon;
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

import com.fasterxml.jackson.annotation.JsonValue;

public enum InvariantRuleType {
    AUTHORITY_FLOOR("authority-floor"),
    EVICTION_IMMUNITY("eviction-immunity"),
    MIN_AUTHORITY_COUNT("min-authority-count"),
    ARCHIVE_PROHIBITION("archive-prohibition");

    private final String key;

    InvariantRuleType(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }
}
