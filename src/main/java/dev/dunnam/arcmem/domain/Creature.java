package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A monster, beast, or supernatural entity encountered in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("A monster, beast, or supernatural entity encountered in a D&D campaign")
public interface Creature extends NamedEntity {
}
