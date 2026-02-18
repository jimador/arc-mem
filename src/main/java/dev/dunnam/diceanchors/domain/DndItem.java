package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A weapon, artifact, treasure, or magical item in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("A weapon, artifact, treasure, or magical item in a D&D campaign")
public interface DndItem extends NamedEntity {
}
