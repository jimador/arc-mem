package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A player character, NPC, or named creature in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("A player character, non-player character (NPC), or named creature in a D&D campaign")
public interface Character extends NamedEntity {
}
