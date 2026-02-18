package dev.dunnam.diceanchors.anchor;

import java.util.List;

public interface ConflictDetector {

    record Conflict(Anchor existing, String incomingText, double confidence, String reason) {}

    List<Conflict> detect(String incomingText, List<Anchor> existingAnchors);
}
