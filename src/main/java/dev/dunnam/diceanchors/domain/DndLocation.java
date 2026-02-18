package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * A place, town, dungeon, region, or geographic feature in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("A place, town, dungeon, region, or geographic feature in a D&D campaign")
public interface DndLocation extends NamedEntity {
}
