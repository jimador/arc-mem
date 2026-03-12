package dev.dunnam.diceanchors.chat;

import java.time.Instant;

public record ConversationRecord(
        String conversationId,
        String title,
        Instant createdAt,
        int messageCount
) {}
