package dev.dunnam.diceanchors.anchor.event;

import java.util.List;

/**
 * Published when incoming text conflicts with one or more active anchors.
 */
public class ConflictDetectedEvent extends AnchorLifecycleEvent {

    private final String incomingText;
    private final int conflictCount;
    private final List<String> conflictingAnchorIds;

    public ConflictDetectedEvent(Object source, String contextId,
                                 String incomingText, int conflictCount,
                                 List<String> conflictingAnchorIds) {
        super(source, contextId);
        this.incomingText = incomingText;
        this.conflictCount = conflictCount;
        this.conflictingAnchorIds = List.copyOf(conflictingAnchorIds);
    }

    public String getIncomingText() {
        return incomingText;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public List<String> getConflictingAnchorIds() {
        return conflictingAnchorIds;
    }
}
