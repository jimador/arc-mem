package dev.dunnam.diceanchors.domain;

import com.embabel.agent.rag.model.NamedEntity;
import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * An organization, guild, cult, or named group in a D&D campaign.
 * Used by the DICE extraction pipeline via DataDictionary.
 */
@JsonClassDescription("An organization, guild, cult, or named group in a D&D campaign")
public interface Faction extends NamedEntity {
}
