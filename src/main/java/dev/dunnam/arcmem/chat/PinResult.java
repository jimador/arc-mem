package dev.dunnam.diceanchors.chat;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * Result of a pin or unpin operation on an anchor.
 */
@JsonClassDescription("Result of pinning or unpinning a fact")
public record PinResult(boolean success, String message) {}
