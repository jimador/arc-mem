package dev.dunnam.diceanchors.persistence;

import com.embabel.dice.proposition.Proposition;
import org.drivine.annotation.Direction;
import org.drivine.annotation.GraphRelationship;
import org.drivine.annotation.GraphView;
import org.drivine.annotation.Root;

import java.util.List;

/**
 * GraphView combining a Proposition with its entity Mentions.
 * <p>
 * This view loads a proposition as the root node and its related
 * mentions via the HAS_MENTION relationship.
 */
@GraphView
public class PropositionView {

    @Root
    private PropositionNode proposition;

    @GraphRelationship(type = "HAS_MENTION", direction = Direction.OUTGOING)
    private List<Mention> mentions;

    public PropositionView() {
    }

    public PropositionView(PropositionNode proposition, List<Mention> mentions) {
        this.proposition = proposition;
        this.mentions = mentions;
    }

    public PropositionNode getProposition() {
        return proposition;
    }

    public void setProposition(PropositionNode proposition) {
        this.proposition = proposition;
    }

    public List<Mention> getMentions() {
        return mentions;
    }

    public void setMentions(List<Mention> mentions) {
        this.mentions = mentions;
    }

    /**
     * Create a PropositionView from a Dice Proposition.
     */
    public static PropositionView fromDice(Proposition p) {
        var propNode = new PropositionNode(
                p.getId(),
                p.getContextIdValue(),
                p.getText(),
                p.getConfidence(),
                p.getDecay(),
                p.getReasoning(),
                p.getGrounding(),
                p.getCreated(),
                p.getRevised(),
                p.getStatus(),
                p.getUri(),
                p.getSourceIds()
        );
        var mentionNodes = p.getMentions().stream()
                            .map(Mention::fromDice)
                            .toList();
        return new PropositionView(propNode, mentionNodes);
    }

    /**
     * Convert this PropositionView back to a Dice Proposition.
     */
    public Proposition toDice() {
        var diceMentions = mentions.stream()
                                   .map(Mention::toDice)
                                   .toList();
        return Proposition.create(
                proposition.getId(),
                proposition.getContextId(),
                proposition.getText(),
                diceMentions,
                proposition.getConfidence(),
                proposition.getDecay(),
                0.0,
                proposition.getReasoning(),
                proposition.getGrounding(),
                proposition.getCreated(),
                proposition.getRevised(),
                proposition.getRevised(),
                proposition.getStatus(),
                0,
                proposition.getSourceIds()
        );
    }

    @Override
    public String toString() {
        return "PropositionView{" +
               "proposition=" + proposition +
               ", mentions=" + mentions +
               '}';
    }
}
