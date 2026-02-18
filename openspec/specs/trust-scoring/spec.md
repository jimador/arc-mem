## MODIFIED Requirements

### Requirement: TrustSignal SPI and four implementations

A `TrustSignal` interface SHALL define the SPI with method `evaluate(PropositionNode, String contextId) -> OptionalDouble`. Four implementations SHALL be provided:

1. `SourceAuthoritySignal` — returns 0.9 for DM-sourced propositions, 0.3 for PLAYER-sourced, 1.0 for SYSTEM-sourced. Source determination SHALL use the proposition's sourceIds.
2. `ExtractionConfidenceSignal` — returns the DICE extraction confidence score directly from the PropositionNode.
3. `GraphConsistencySignal` — computes Jaccard similarity between the proposition's token set and the union of active anchor token sets in the same contextId. Tokens SHALL be lowercased words with stop words removed. Returns 0.5 (neutral) when no anchors exist (cold-start).
4. `CorroborationSignal` — computes a weighted source diversity score. Single PLAYER source returns 0.3. Single DM source returns 0.5. Mixed DM+PLAYER sources return 0.7. Three or more distinct sources return 0.9.

#### Scenario: DM source authority signal
- **WHEN** `SourceAuthoritySignal.evaluate()` is called for a DM-sourced proposition
- **THEN** the signal returns 0.9

#### Scenario: Cold-start graph consistency
- **WHEN** `GraphConsistencySignal.evaluate()` is called with no existing anchors in the context
- **THEN** the signal returns 0.5

#### Scenario: Jaccard similarity computation
- **WHEN** a proposition has tokens {"king", "alive", "throne"} and active anchors have combined tokens {"king", "rules", "province", "alive"}
- **THEN** GraphConsistencySignal returns the Jaccard coefficient: |intersection| / |union| = 2/5 = 0.4

#### Scenario: Mixed source corroboration
- **WHEN** a proposition has sourceIds from both a DM turn and a PLAYER turn
- **THEN** `CorroborationSignal` returns 0.7

#### Scenario: Multi-source corroboration
- **WHEN** a proposition has 3 distinct sourceIds
- **THEN** `CorroborationSignal` returns 0.9
