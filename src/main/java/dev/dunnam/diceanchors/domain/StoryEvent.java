package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A significant story event, encounter, or turning point in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("A significant story event, encounter, or turning point in a D&D campaign")
public interface StoryEvent extends NamedEntity {
}
