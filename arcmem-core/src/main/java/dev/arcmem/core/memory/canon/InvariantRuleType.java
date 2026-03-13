package dev.arcmem.core.memory.canon;

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
