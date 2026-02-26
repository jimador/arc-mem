package dev.dunnam.diceanchors.chat;

import java.time.Instant;

public record ChatMessageRecord(
        String conversationId,
        String role,
        String text,
        int ordinal,
        Instant createdAt
) {}
